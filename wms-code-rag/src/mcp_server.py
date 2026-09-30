"""Model Context Protocol (MCP) Server for WMS Codebase Intelligence."""

import argparse
import os
import sys
from pathlib import Path

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


@mcp.tool()
def search_wms_code(query: str, top_n: int = 4) -> str:
    """Semantic search across the WMS codebase (Java services, controllers, Vue components, configs).

    Use this tool to find how warehouse operations, business logic, endpoints,
    or components are implemented without reading the entire repository.
    """
    results = retriever.retrieve(query=query, top_n=top_n)
    return retriever.format_for_agent(results)


@mcp.tool()
def get_entity_and_schema(table_or_entity: str) -> str:
    """Retrieves database DDL, Flyway migrations, and JPA entity definitions for a table or entity.

    Args:
        table_or_entity: Name of the table or entity (e.g., 'orders', 'stock', 'location', 'product')
    """
    query = f"table entity definition {table_or_entity} create table schema"
    results = retriever.retrieve(
        query=query,
        top_n=5,
        where_filter={"chunk_type": "sql_schema"},
    )
    if not results:
        # Fallback to searching without where_filter
        results = retriever.retrieve(query=query, top_n=4)
    return retriever.format_for_agent(results)


@mcp.tool()
def search_wms_security(topic: str) -> str:
    """Searches security-critical code in WMS: authorization, JWT, CORS, roles, file uploads, sensitive endpoints.

    Args:
        topic: Specific security aspect (e.g., 'PreAuthorize role checks', 'jwt token validation', 'excel upload sanitization')
    """
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


@mcp.tool()
def reindex_wms_codebase() -> str:
    """Forces a full re-scan and re-index of the WMS codebase into the vector database."""
    files_scanned, chunks_indexed = indexer.scan_and_index(clear_first=True)
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
        from starlette.routing import Route
        from starlette.responses import JSONResponse

        # Build StreamableHTTP application supporting modern MCP specification (2024-11-05)
        app = mcp.streamable_http_app()
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

        # Auto-heal expired session IDs instead of failing with 404
        class SessionAutoHealMiddleware:
            def __init__(self, inner_app, session_manager):
                self.inner_app = inner_app
                self.session_manager = session_manager

            async def __call__(self, scope, receive, send):
                if scope.get("type") == "http":
                    headers = dict(scope.get("headers", []))
                    session_id = headers.get(b"mcp-session-id")
                    if session_id:
                        sess_str = session_id.decode("ascii", errors="ignore")
                        # If session ID is not active, strip header to start fresh session seamlessly
                        if sess_str not in self.session_manager._server_instances:
                            scope["headers"] = [
                                (k, v) for k, v in scope.get("headers", [])
                                if k.lower() != b"mcp-session-id"
                            ]
                await self.inner_app(scope, receive, send)

        final_app = SessionAutoHealMiddleware(app, mcp.session_manager)

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
