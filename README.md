# Warehouse Management System (WMS) & Codebase RAG Platform

A comprehensive enterprise Warehouse Management System (WMS) featuring an integrated, autonomous Codebase Retrieval-Augmented Generation (Codebase RAG) subsystem communicating via the Model Context Protocol (MCP).

---

## Repository Architecture

```text
.
├── inbound-storage-dispatch/           # WMS Core Subsystem
│   ├── wmsBack/                        # Spring Boot 3 backend (Java 21, JPA, Flyway, PostgreSQL)
│   └── wmsFront/                       # Vue 3 frontend (Vite, PrimeVue, Pinia, JavaScript)
│
├── wms-code-rag/                       # Containerized Codebase RAG + MCP Service
│   ├── src/                            # RAG Engine: AST chunking, ONNX embedder, ChromaDB, reranker
│   ├── Dockerfile                      # Lightweight container image (Python 3.11-slim, ONNX runtime)
│   ├── docker-compose.yml              # Service orchestration with read-only (:ro) volume mount of WMS
│   ├── requirements.txt                # Python package dependencies
│   └── config.yaml                     # Model, vector, and server configuration
│
└── .gitignore                          # Build artifacts and temporary file exclusions
```

---

## 1. WMS Subsystem (`inbound-storage-dispatch`)

* **Backend:** Java 21, Spring Boot 3.4, Spring Data JPA, Flyway migrations, PostgreSQL 16.
* **Frontend:** Vue 3 Composition API, Vite, PrimeVue, Pinia.
* **Core Workflows:** Inbound receiving, storage allocation, auto-replenishment, order picking, and dispatch.

### Running WMS Locally
* **Backend:**
  ```bash
  cd "inbound-storage-dispatch/wmsBack"
  ./mvnw clean spring-boot:run
  ```
* **Frontend:**
  ```bash
  cd "inbound-storage-dispatch/wmsFront"
  npm install
  npm run dev
  ```

---

## 2. Codebase RAG & MCP Subsystem (`wms-code-rag`)

An autonomous semantic code search and indexing service engineered as an external cognitive capability layer (Capability Boundary) for AI development agents (e.g., Antigravity).

* **Chunking:** Syntax-aware splitting preserving Java class/method structures, Vue `<script>/<template>` blocks, and SQL DDL migrations.
* **Embedder:** `sentence-transformers/all-MiniLM-L6-v2` executed via optimized ONNX Runtime (CPU, ~100 MB RAM footprint).
* **Vector Store:** ChromaDB with persistent HNSW index and cosine distance metric.
* **Reranking:** Cross-Encoder (`ms-marco-MiniLM-L-6-v2`).
* **Protocol:** FastMCP / Streamable HTTP (spec version 2024-11-05).

### Running the RAG Container
```bash
cd wms-code-rag
docker compose up -d
```
The server binds to port `8000`:
* Healthcheck: `http://localhost:8000/health`
* MCP Streamable HTTP: `http://localhost:8000/sse`

---

## 3. Connecting to AI Agent (Antigravity)

Register the MCP server in `~/.gemini/config/mcp_config.json`:
```json
{
  "mcpServers": {
    "wms-code-rag": {
      "serverUrl": "http://localhost:8000/sse"
    }
  }
}
```

### Available Agent MCP Tools
* `search_wms_code(query, top_n)` — Semantic search across Java and Vue codebases.
* `find_symbol_declaration(symbol_name)` — Deterministic verification of exact symbol declarations (classes, interfaces, methods, records, inner types) without fuzzy matching.
* `get_entity_and_schema(table_or_entity)` — Retrieve table DDL schemas, Flyway migrations, and JPA entity definitions.
* `search_wms_security(topic)` — Targeted security search (`@PreAuthorize`, JWT filters, role checks, CORS).
* `get_rag_status()` — Vector database metrics, indexed chunk counts, and model status.
* `reindex_wms_codebase()` — Trigger full reindexing of the WMS codebase after modifications.

---

## 4. Project Documentation

* [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md) — **Primary Source of Truth:** Independent adversarial findings verification pass over DEF-01..DEF-26 and baseline remediation items.
* [`docs/WMS_REMEDIATION_ROADMAP.md`](docs/WMS_REMEDIATION_ROADMAP.md) — Active prioritized implementation roadmap across 4 remediation waves.
* [`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) — Architecture registry, AST parsing, and security boundary of the Code RAG subsystem (29 tests).
* [`docs/RAG_POST_FIX_EVALUATION.md`](docs/RAG_POST_FIX_EVALUATION.md) — Empirical retrieval evaluation benchmark (1,261 chunks, Hit@1 83.3%).
* [`docs/archive/`](docs/archive/README.md) — Archive of historical technical audits, pre-audit guides, unified registers, and initial benchmarks.
* [`inbound-storage-dispatch/README.md`](inbound-storage-dispatch/README.md) — Domain specification, entity lifecycle, and REST API documentation for WMS.
* [`wms-code-rag/README.md`](wms-code-rag/README.md) — Standalone guide for the RAG MCP service.
