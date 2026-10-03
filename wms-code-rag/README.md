# WMS Codebase RAG MCP Server

An intelligent semantic code search and indexing system for the WMS codebase (`inbound-storage-dispatch`), communicating via the **Model Context Protocol (MCP)** for AI developer agents.

---

## 1. Architecture & Data Flow

```mermaid
flowchart TD
    subgraph Host["Host Environment (Antigravity / IDE)"]
        Agent["AI Coding Agent (Antigravity)"]
    end

    subgraph MCPBoundary["MCP Protocol Layer (JSON-RPC 2.0 / SSE :8000)"]
        Agent <-->|Tools: search_wms_code, get_entity_and_schema| MCPServer["FastMCP Server (src/mcp_server.py)"]
    end

    subgraph DockerContainer["Docker Container: wms-code-rag-server"]
        MCPServer --> Retriever["CodeRetriever (src/retriever.py)"]
        Retriever --> Embedder["Embedder: all-MiniLM-L6-v2 (src/embedder.py)"]
        Retriever --> VectorDB[("ChromaDB HNSW Index (data/chroma)")]
        Retriever --> Reranker["Cross-Encoder: ms-marco-MiniLM (src/reranker.py)"]
        
        Indexer["CodebaseIndexer (src/indexer.py)"] --> Chunker["CodeAwareChunker (src/chunker.py)"]
        Chunker --> Embedder
        Embedder --> VectorDB
    end

    subgraph Codebase["Target WMS Repository (/workspace/wms)"]
        Java["wmsBack: Java Services, Controllers, Entities"]
        SQL["Flyway Migrations (db/migration/*.sql)"]
        Vue["wmsFront: Vue 3 Components, Pinia, API"]
        Config["application.properties, docker-compose.yaml"]
        
        Java -.-> Indexer
        SQL -.-> Indexer
        Vue -.-> Indexer
        Config -.-> Indexer
    end
```

---

## 2. Project Structure & File Index

```text
wms-code-rag/
├── Dockerfile                   # Container build based on python:3.11-slim (non-root appuser)
├── docker-compose.yml           # Service orchestration with read-only WMS codebase volume mount
├── requirements.txt             # Dependencies: mcp, chromadb, sentence-transformers, torch, pydantic
├── config.yaml                  # Configuration for paths, embedding models, reranker, and network ports
├── run_docker.bat               # One-click Docker container launcher
├── run_local_sse.bat            # Local execution launcher over HTTP/SSE
├── run_local_stdio.bat          # Local execution launcher over stdio
└── src/
    ├── __init__.py
    ├── config.py                # Pydantic validation models for settings + env overrides
    ├── chunker.py               # Syntax-aware code chunker (Java, SQL, Vue, Markdown)
    ├── embedder.py              # Text vectorization (SentenceTransformers + thread-safe LRU cache)
    ├── vector_store.py          # Persistent ChromaDB integration (HNSW index, cosine distance)
    ├── reranker.py              # Cross-Encoder for candidate reranking (high-precision ranking)
    ├── indexer.py               # Codebase scanner (file parsing, chunk generation, and persistence)
    ├── retriever.py             # Orchestrator: Query -> Vector Search -> Filters -> Reranking
    └── mcp_server.py            # FastMCP server with tool registrations for AI agents
```

---

## 3. Available MCP Tools

AI agents invoke these tools in the background during coding and inspection tasks:

1. `search_wms_code(query: str, top_n: int = 4)`  
   Semantic search across Java services, controllers, Vue components, and configurations. Returns exact file path, line numbers, and relevant code chunk.
2. `find_symbol_declaration(symbol_name: str)`  
   Deterministic verification of exact symbol declarations in indexed code (interfaces, classes, methods, records, inner types). Avoids fuzzy false positives and protects against existence hallucinations.
3. `get_entity_and_schema(table_or_entity: str)`  
   Retrieves DDL table schemas, Flyway migrations, and relational mappings for a specific database entity.
4. `search_wms_security(topic: str)`  
   Targeted security search: `@PreAuthorize`, JWT filters, role validations, file uploads, CORS configuration.
5. `get_rag_status()`  
   Returns live vector index metrics (indexed chunk counts, database path, active model status).
6. `reindex_wms_codebase()`  
   Forces full reindexing of the WMS repository after major codebase modifications.

---

## 4. How to Run

### Option 1: In Docker (Recommended)
Double-click `run_docker.bat` or execute:
```powershell
docker compose up -d --build
```
The server starts inside an isolated container on port `8000`. On first launch, it automatically indexes the target WMS repository.

### Option 2: Locally via Python
```powershell
pip install -r requirements.txt
python src/mcp_server.py --transport sse --host 127.0.0.1 --port 8000 --auto-index
```

---

## 5. Architectural Guide for Auditing External RAG / MCP Code

When inspecting an external RAG or MCP implementation, use this reference checklist:

1. **Where is the entrypoint?**
   * In MCP projects, locate the `FastMCP(...)` or `Server(...)` initialization — look for functions decorated with `@mcp.tool()`. These define the server's capability boundaries.
2. **What is the chunking strategy?**
   * Generic `RecursiveCharacterTextSplitter` breaks code syntax boundaries. Production-grade projects use AST, regex tokenizers, or signature-preserving splitters (`chunker.py`).
3. **Which embedding model is used?**
   * Check embedding dimensionality and supported languages. For source code, `sentence-transformers/all-MiniLM-L6-v2` or `bge-m3` offer solid accuracy-to-latency ratios.
4. **Is a reranker included?**
   * Dense vector search alone frequently introduces noise. A Cross-Encoder reranker (`reranker.py`) is standard for high-precision retrieval.
5. **Where are embeddings persisted?**
   * Ensure vector storage is persisted to disk (e.g., `ChromaDB PersistentClient` with a dedicated directory or `pgvector`), otherwise restarting the process requires re-indexing the entire codebase.
