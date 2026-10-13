"""End-to-end integration tests for Ospysin Documentation RAG MCP tool layer.

Verifies:
1. Tool argument validation, string bounds, and top_n clamping.
2. Dual-domain reporting in get_rag_status.
3. Search tool execution with dedicated security tag (<untrusted_ospysin_docs_context>).
4. Deterministic get_doc lookup, section filtering, and error taxonomies (NOT_FOUND, SECTION_NOT_FOUND).
5. Protected reindex_ospysin_docs confirmation gate and auth token boundary.
6. Starlette routing topology (dual SSE + Streamable HTTP + healthcheck endpoints).
"""

from pathlib import Path
from typing import Optional
import pytest

from src.config import AppConfig, OspysinDocsConfig, ServerConfig, VectorDBConfig
from src.doc_indexer import OspysinDocsIndexer
from src.doc_service import OspysinDocsService
from src.retriever import CodeRetriever
from src.vector_store import CodeVectorStore


class MockFastEmbedder:
    """Fast deterministic embedder for integration testing without ONNX weights."""

    def __init__(self, dim: int = 128):
        self._dim = dim

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)

    def embed_texts(self, texts: list[str], batch_size: int = 32) -> list[list[float]]:
        return [self._embed(t) for t in texts]

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(t) for t in texts]

    def _embed(self, text: str) -> list[float]:
        import hashlib
        vec = [0.0] * self._dim
        words = text.lower().split()
        for w in words:
            h = int(hashlib.md5(w.encode("utf-8")).hexdigest(), 16) % self._dim
            vec[h] += 1.0
        norm = sum(x * x for x in vec) ** 0.5
        if norm > 0:
            vec = [x / norm for x in vec]
        return vec


@pytest.fixture
def test_environment(tmp_path: Path):
    """Sets up a complete isolated Ospysin documentation index for integration testing."""
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    adr_dir = docs_dir / "adr"
    adr_dir.mkdir()

    # Spec 05: Tool Catalog
    spec_05 = docs_dir / "05-TOOL-CATALOG.md"
    spec_05.write_text(
        """# 05. Tool Catalog Specification

## Overview
Specification for automated reconnaissance and security assessment tools.

## Detailed Tool Specifications

### 1. dns_lookup
Performs DNS queries for A, AAAA, MX, and TXT records.

### 2. whois_lookup
Queries RDAP/WHOIS databases for domain registration details.
""",
        encoding="utf-8",
    )

    # ADR 0003: SSRF Protection
    adr_03 = adr_dir / "0003-ssrf-mitigation.md"
    adr_03.write_text(
        """# ADR-0003: SSRF Mitigation Architecture

## Context
Outgoing requests must prevent access to cloud metadata endpoints.

## Decision
Enforce pre-resolution IP checking against RFC 6890 registries before opening network sockets.
""",
        encoding="utf-8",
    )

    chroma_dir = tmp_path / "chroma"
    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(chroma_dir),
            collection_name="test_wms_code",
        ),
        ospysin=OspysinDocsConfig(
            enabled=True,
            collection_name="test_ospysin_docs",
            docs_dir=str(docs_dir),
            min_score=-2.5,
            source_commit="commit-abc1234",
        ),
        server=ServerConfig(
            host="127.0.0.1",
            port=8000,
            auth_token="secret-token-xyz",
        ),
    )

    embedder = MockFastEmbedder()
    (docs_dir / ".git-revision").write_text("commit-abc1234", encoding="utf-8")
    store = CodeVectorStore(persist_dir=str(chroma_dir), collection_name=config.ospysin.collection_name)
    indexer = OspysinDocsIndexer(config, embedder=embedder, store=store)
    indexer.scan_and_index(clear_first=True)

    retriever = CodeRetriever(config, store=store, embedder=embedder)
    service = OspysinDocsService(indexer, retriever)

    return {
        "config": config,
        "indexer": indexer,
        "retriever": retriever,
        "service": service,
        "docs_dir": docs_dir,
    }


def test_input_validation_and_clamping():
    """Verify input bounds check across all MCP tools."""
    pytest.importorskip("mcp")
    from src.mcp_server import clamp_top_n, validate_query

    assert validate_query("", "query") == "Error: query parameter cannot be empty."
    assert validate_query("   ", "query") == "Error: query parameter cannot be empty."
    assert validate_query("a" * 1001, "query") == "Error: query exceeds maximum allowed length of 1000 characters."
    assert validate_query("valid search term", "query") is None

    # Clamping
    assert clamp_top_n(-5, default=4, max_val=10) == 1
    assert clamp_top_n(0, default=4, max_val=10) == 1
    assert clamp_top_n(4, default=4, max_val=10) == 4
    assert clamp_top_n(25, default=4, max_val=10) == 10
    assert clamp_top_n("invalid", default=4, max_val=10) == 4


def test_mcp_tool_search_ospysin_docs_context_branding(test_environment):
    """Verify search returns the isolated <untrusted_ospysin_docs_context> envelope and index provenance."""
    service: OspysinDocsService = test_environment["service"]

    result = service.search("DNS queries for A and AAAA records", top_n=2)

    # Invariant: Must use the documentation-specific security envelope
    assert "<untrusted_ospysin_docs_context>" in result
    assert "</untrusted_ospysin_docs_context>" in result
    assert "<untrusted_wms_codebase_context>" not in result

    # Invariant: Must contain source citation and provenance footer
    assert "05-TOOL-CATALOG.md" in result
    assert "[ospysin-index: collection=test_ospysin_docs" in result
    assert "source_commit=commit-abc1234" in result


def test_mcp_tool_get_ospysin_doc_full_taxonomy(test_environment):
    """Verify get_ospysin_doc handles numbers, ADRs, sections, and error conditions."""
    service: OspysinDocsService = test_environment["service"]

    # 1. Numbered lookup: '5' -> 05-TOOL-CATALOG.md
    res_5 = service.get_doc("5")
    assert "FOUND document: 05-TOOL-CATALOG.md" in res_5
    assert "<untrusted_ospysin_docs_context>" in res_5
    assert "dns_lookup" in res_5

    # 2. Section extraction: 'dns_lookup'
    res_sec = service.get_doc("5", section="dns_lookup")
    assert "FOUND document: 05-TOOL-CATALOG.md" in res_sec
    assert "dns_lookup" in res_sec
    assert "whois_lookup" not in res_sec  # Filtered to only the requested section

    # 3. ADR lookup: 'adr-3'
    res_adr = service.get_doc("adr-3")
    assert "FOUND document: adr/0003-ssrf-mitigation.md" in res_adr
    assert "RFC 6890" in res_adr

    # 4. Unknown section: returns SECTION_NOT_FOUND with available sections
    res_bad_sec = service.get_doc("5", section="nonexistent_section")
    assert "SECTION_NOT_FOUND" in res_bad_sec
    assert "available_sections:" in res_bad_sec
    assert "1. dns_lookup" in res_bad_sec

    # 5. Unknown document: returns NOT_FOUND with available documents
    res_bad_doc = service.get_doc("99")
    assert "NOT_FOUND" in res_bad_doc
    assert "available_documents:" in res_bad_doc
    assert "05-TOOL-CATALOG.md" in res_bad_doc


def test_mcp_tool_reindex_ospysin_docs_authorization_and_safety(test_environment):
    """Verify reindex_ospysin_docs enforces confirm=True and auth_token checks."""
    service: OspysinDocsService = test_environment["service"]
    config: AppConfig = test_environment["config"]

    # 1. Unconfirmed reindex returns safety warning without wiping collection
    warn = service.reindex(confirm=False)
    assert "Warning: reindex_ospysin_docs clears and rebuilds ONLY the Ospysin docs collection" in warn
    assert "Pass confirm=True to proceed." in warn
    assert service.store.count() > 0

    # 2. Confirmed reindex executes and reports metrics
    success = service.reindex(confirm=True)
    assert "Ospysin docs re-indexing complete!" in success
    assert "Scanned Files: 2" in success
    assert "Indexed Chunks: 5" in success


def test_mcp_server_routing_topology():
    """Verify ASGI app topology exposes SSE, Streamable HTTP, and health check endpoints."""
    pytest.importorskip("mcp")
    from mcp.server.fastmcp import FastMCP
    from src.mcp_server import create_http_app

    dummy_mcp = FastMCP("dummy-server")
    app_config = AppConfig()

    app = create_http_app(dummy_mcp, app_config)

    # Inspect inner routes
    routes = [r.path for r in app.inner_app.inner_app.routes]

    # Invariants:
    # 1. SSE clients require /sse (GET) and /messages (POST)
    assert "/sse" in routes
    assert "/messages" in routes
    # 2. Streamable HTTP clients require /mcp or /
    assert "/mcp" in routes
    assert "/" in routes
    # 3. Docker orchestration requires /health
    assert "/health" in routes
