"""Model Context Protocol (MCP) Server for WMS Codebase Intelligence."""

import argparse
import logging
import os
import re
import sys
from pathlib import Path
from typing import Any, Optional

logging.basicConfig(stream=sys.stderr, level=logging.INFO)

# Ensure package root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))
os.chdir(PROJECT_ROOT)

from mcp.server.fastmcp import FastMCP

from src.config import load_config
from src.indexer import CodebaseIndexer
from src.retriever import CodeRetriever

config = load_config()
indexer = CodebaseIndexer(config)
retriever = CodeRetriever(config)

# Initialize FastMCP Server
mcp = FastMCP(
    name="wms-code-rag",
    dependencies=["chromadb", "pydantic", "pyyaml"],
)


MAX_QUERY_LENGTH = 1000


def validate_query(text: str, name: str = "query") -> str | None:
    if not text or not text.strip():
        return f"Error: {name} parameter cannot be empty."
    if len(text) > MAX_QUERY_LENGTH:
        return f"Error: {name} exceeds maximum allowed length of {MAX_QUERY_LENGTH} characters."
    return None


def clamp_top_n(top_n: int, default: int = 4, max_val: int = 20) -> int:
    try:
        val = int(top_n)
        return min(max(1, val), max_val)
    except (ValueError, TypeError):
        return default


@mcp.tool()
def search_wms_code(query: str, top_n: int = 4) -> str:
    """Semantic search across the WMS codebase (Java services, controllers, Vue components, configs).

    Use this tool to find how warehouse operations, business logic, endpoints,
    or components are implemented without reading the entire repository.
    """
    if err := validate_query(query, "query"):
        return err
    bounded_n = clamp_top_n(top_n, default=4, max_val=20)
    results = retriever.retrieve(query=query, top_n=bounded_n)
    return retriever.format_for_agent(results)


@mcp.tool()
def get_entity_and_schema(table_or_entity: str) -> str:
    """Retrieves database DDL, Flyway migrations, and JPA entity definitions for a table or entity.

    Args:
        table_or_entity: Name of the table or entity (e.g., 'orders', 'stock', 'location', 'product')
    """
    if err := validate_query(table_or_entity, "table_or_entity"):
        return err

    # 1. Retrieve SQL schema / DDL migrations
    sql_query = f"table schema definition CREATE TABLE ALTER TABLE {table_or_entity}"
    sql_results = retriever.retrieve(
        query=sql_query,
        top_n=3,
        where_filter={"chunk_type": "sql_schema"},
    )

    # 2. Retrieve Java JPA Entity definitions (@Entity, @Table, class definition)
    entity_query = f"JPA @Entity @Table class definition {table_or_entity} fields relationships"
    entity_results = retriever.retrieve(
        query=entity_query,
        top_n=3,
        where_filter={"language": "java"},
    )

    # 3. Combine and deduplicate
    combined = []
    seen_ids = set()
    for item in sql_results + entity_results:
        chunk = item[0]
        if chunk.id not in seen_ids:
            seen_ids.add(chunk.id)
            combined.append(item)

    # 4. Fallback if both specific searches returned nothing
    if not combined:
        fallback_query = f"table entity definition {table_or_entity} create table schema"
        combined = retriever.retrieve(query=fallback_query, top_n=4)

    # 5. Entity Relevance Verification: ensure returned chunks actually relate to the requested entity
    target_clean = table_or_entity.strip().lower()
    target_stems = {target_clean}
    if target_clean.endswith("s") and len(target_clean) > 3:
        target_stems.add(target_clean[:-1])
    if target_clean.endswith("es") and len(target_clean) > 4:
        target_stems.add(target_clean[:-2])

    relevant_combined = []
    for item in combined:
        chunk = item[0]
        symbol = (chunk.symbol_name or "").lower()
        table_meta = str(chunk.metadata.get("table_or_index", "")).lower()
        class_meta = str(chunk.metadata.get("class", "")).lower()
        content_lower = chunk.content.lower()

        matched = False
        for stem in target_stems:
            if symbol and (stem in symbol or (len(symbol) >= 3 and symbol in stem)):
                matched = True
                break
            if table_meta and (stem in table_meta or (len(table_meta) >= 3 and table_meta in stem)):
                matched = True
                break
            if class_meta and (stem in class_meta or (len(class_meta) >= 3 and class_meta in stem)):
                matched = True
                break
            if re.search(rf"\b{re.escape(stem)}\b", content_lower):
                matched = True
                break

        if matched:
            relevant_combined.append(item)

    return retriever.format_for_agent(relevant_combined)


@mcp.tool()
def search_wms_security(topic: str) -> str:
    """Searches security-critical code in WMS: authorization, JWT, CORS, roles, file uploads, sensitive endpoints.

    Args:
        topic: Specific security aspect (e.g., 'PreAuthorize role checks', 'jwt token validation', 'excel upload sanitization')
    """
    if err := validate_query(topic, "topic"):
        return err
    query = f"security auth permission {topic}"
    results = retriever.retrieve(query=query, top_n=5)
    return retriever.format_for_agent(results)


@mcp.tool()
def get_rag_status() -> str:
    """Returns current status and statistics of the WMS Codebase RAG index."""
    count = indexer.store.count()
    return (
        f"WMS Codebase RAG Status:\n"
        f"- Target Codebase: {config.codebase.target_dir}\n"
        f"- Total Indexed Chunks: {count}\n"
        f"- Embedding Model: {config.embeddings.default_model}\n"
        f"- Reranker: {config.reranking.model_name} (Enabled: {config.reranking.enabled})\n"
        f"- ChromaDB Path: {config.vector_db.persist_dir}\n"
    )


class AuthMiddleware:
    """Enforces token-based authentication on remote MCP endpoints when auth_token is configured."""

    def __init__(self, inner_app, auth_token: str | None = None):
        self.inner_app = inner_app
        self.auth_token = auth_token

    async def __call__(self, scope, receive, send):
        if scope.get("type") == "http":
            path = scope.get("path", "")
            # Whitelist healthcheck endpoint for orchestration/readiness probes
            if path == "/health":
                await self.inner_app(scope, receive, send)
                return

            if self.auth_token:
                import hmac
                from starlette.responses import JSONResponse

                headers = dict(scope.get("headers", []))
                auth_header = headers.get(b"authorization", b"").decode("latin-1").strip()
                api_key_header = headers.get(b"x-api-key", b"").decode("latin-1").strip()

                bearer_token = ""
                if auth_header.lower().startswith("bearer "):
                    bearer_token = auth_header[7:].strip()

                # Query parameters (?token=...) are intentionally NOT supported
                # to prevent secret token leakage into HTTP access logs, proxy logs, and URI histories (CWE-598).
                provided_token = bearer_token or api_key_header
                if not provided_token or not hmac.compare_digest(provided_token, self.auth_token):
                    response = JSONResponse(
                        {"error": "Unauthorized: valid authentication token required in Authorization or X-API-Key header"},
                        status_code=401,
                        headers={"WWW-Authenticate": "Bearer"},
                    )
                    await response(scope, receive, send)
                    return

        await self.inner_app(scope, receive, send)


def is_session_active(session_manager: Any, session_id: str) -> bool:
    """Safely checks if an MCP session is active without hard private API coupling.

    Inspects public methods if provided by current/future FastMCP versions,
    with safe defensive inspection of container attributes if private.
    """
    if not session_manager:
        return True

    # 1. Prefer public session check APIs if provided by FastMCP version
    for method_name in ("has_session", "is_active", "get_session"):
        method = getattr(session_manager, method_name, None)
        if callable(method):
            try:
                res = method(session_id)
                return bool(res)
            except Exception:
                pass

    # 2. Defensive fallback checking session container without crashing on attribute change
    instances = getattr(session_manager, "_server_instances", None)
    if isinstance(instances, (dict, set, list)):
        return session_id in instances

    # 3. Default to True so standard MCP protocol error handling applies
    return True


class SessionAutoHealMiddleware:
    """Auto-heals stale session IDs to prevent 404 disconnections on client reconnects."""

    def __init__(self, inner_app, session_manager: Any = None):
        self.inner_app = inner_app
        self.session_manager = session_manager

    async def __call__(self, scope, receive, send):
        if scope.get("type") == "http":
            headers = dict(scope.get("headers", []))
            session_id = headers.get(b"mcp-session-id")
            if session_id:
                sess_str = session_id.decode("ascii", errors="ignore")
                # If session ID is not active, strip header to start fresh session seamlessly
                if not is_session_active(self.session_manager, sess_str):
                    scope["headers"] = [
                        (k, v) for k, v in scope.get("headers", [])
                        if k.lower() != b"mcp-session-id"
                    ]
        await self.inner_app(scope, receive, send)


def create_http_app(mcp_server: FastMCP, app_config) -> Any:
    """Build ASGI application supporting modern MCP Streamable HTTP, health checks, and authentication."""
    from starlette.routing import Route
    from starlette.responses import JSONResponse

    app = mcp_server.streamable_http_app()
    endpoint = app.routes[0].endpoint

    # Mount on common MCP paths so clients connecting to /sse, /mcp, or root / all work
    app.routes.append(Route("/sse", endpoint))
    app.routes.append(Route("/", endpoint))

    # Fast, non-blocking health check endpoint for Docker & monitoring
    async def health_check(request):
        return JSONResponse({
            "status": "healthy",
            "service": "wms-code-rag",
            "indexed_chunks": indexer.store.count(),
        })

    app.routes.append(Route("/health", health_check, methods=["GET"]))

    try:
        session_mgr = getattr(mcp_server, "session_manager", None)
    except Exception:
        session_mgr = None

    session_app = SessionAutoHealMiddleware(app, session_mgr)
    effective_token = app_config.server.auth_token or os.environ.get("MCP_AUTH_TOKEN")
    return AuthMiddleware(session_app, auth_token=effective_token)


@mcp.tool()
def reindex_wms_codebase(confirm: bool = False, auth_token: str | None = None) -> str:
    """Forces a full re-scan and re-index of the WMS codebase into the vector database.

    Args:
        confirm: Confirmation flag. Must be True to proceed with clearing and reindexing.
        auth_token: Required authorization token when server authentication is configured.
    """
    expected_token = config.server.auth_token or os.environ.get("MCP_AUTH_TOKEN")
    if expected_token:
        import hmac
        clean_token = (auth_token or "").strip()
        if not clean_token or not hmac.compare_digest(clean_token, expected_token):
            return "Error: Unauthorized. Valid auth_token required to execute destructive reindexing."

    if not confirm:
        return (
            "Warning: reindex_wms_codebase is a destructive operation that clears the existing "
            "vector store and reindexes the codebase. Pass confirm=True to proceed."
        )

    try:
        files_scanned, chunks_indexed = indexer.scan_and_index(clear_first=True)
    except RuntimeError as e:
        return f"Error: {e}"

    return (
        f"Re-indexing complete!\n"
        f"- Scanned Files: {files_scanned}\n"
        f"- Indexed Semantic Chunks: {chunks_indexed}\n"
        f"- Total in Collection: {indexer.store.count()}\n"
    )


def main():
    parser = argparse.ArgumentParser(description="WMS Codebase RAG MCP Server")
    parser.add_argument(
        "--transport",
        choices=["stdio", "sse", "http", "streamable-http"],
        default="stdio",
        help="MCP transport protocol (stdio for local agent, sse/http for remote/Docker)",
    )
    parser.add_argument("--host", default=config.server.host, help="Host for HTTP/SSE server")
    parser.add_argument("--port", type=int, default=config.server.port, help="Port for HTTP/SSE server")
    parser.add_argument("--auto-index", action="store_true", help="Auto-index codebase on startup if empty")
    args = parser.parse_args()

    if args.auto_index or indexer.store.count() == 0:
        import threading
        index_thread = threading.Thread(
            target=indexer.scan_and_index,
            kwargs={"clear_first": False},
            daemon=True,
            name="initial-indexer"
        )
        index_thread.start()

    if args.transport in ("sse", "http", "streamable-http"):
        import uvicorn
        final_app = create_http_app(mcp, config)

        uvicorn_config = uvicorn.Config(
            final_app,
            host=args.host,
            port=args.port,
            log_level="info",
        )
        server = uvicorn.Server(uvicorn_config)
        server.run()
    else:
        mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
