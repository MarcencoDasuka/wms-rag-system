# Warehouse Management System (WMS) & Multi-Domain RAG Platform

A comprehensive enterprise Warehouse Management System (WMS) featuring an integrated, autonomous Multi-Domain Retrieval-Augmented Generation (Codebase & Documentation RAG) subsystem communicating via the Model Context Protocol (MCP).

---

## Repository Architecture

```text
.
├── inbound-storage-dispatch/           # WMS Core Subsystem
│   ├── wmsBack/                        # Spring Boot 3 backend (Java 21, JPA, Flyway, PostgreSQL)
│   └── wmsFront/                       # Vue 3 frontend (Vite, PrimeVue, Pinia, JavaScript)
│
├── Ospysin/                            # Ospysin Architecture Specifications Subsystem
│   └── docs/                           # 21 Markdown specifications and 5 Architecture Decision Records (ADRs)
│
├── wms-code-rag/                       # Containerized Multi-Domain RAG + MCP Service
│   ├── src/                            # Code & Doc RAG engine, ONNX embedder, ChromaDB, FastMCP
│   ├── eval/                           # Retrieval evaluation datasets (WMS & Ospysin queries)
│   ├── tests/                          # 65 automated tests (unit, integration, adversarial invariants)
│   ├── Dockerfile                      # Lightweight container image (Python 3.11-slim, ONNX runtime)
│   ├── docker-compose.yml              # Service orchestration with read-only (:ro) volume mounts
│   ├── requirements.txt                # Python package dependencies
│   └── config.yaml                     # Multi-domain vector, model, and server configuration
│
├── docs/                               # Central Platform Documentation Hub
│   ├── README.md                       # Master documentation index and domain comparison matrix
│   ├── wms-codebase-rag/               # Domain 1: WMS Codebase Intelligence reports & roadmaps
│   ├── ospysin-docs-rag/               # Domain 2: Ospysin Documentation Intelligence reports & roadmaps
│   └── archive/                        # Historical technical audits and superseded benchmarks
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

## 2. Multi-Domain RAG & MCP Subsystem (`wms-code-rag`)

An autonomous semantic retrieval and indexing service engineered as an external cognitive capability layer (Capability Boundary) for AI development agents (e.g., Antigravity). Operates two strictly isolated vector domains:

1. **WMS Codebase Domain (`wms_codebase_knowledge`):**
   * **Scope:** 1,261 syntax-aware code chunks (Java AST class/method blocks, Vue SFC components, SQL DDL migrations).
   * **Embedder:** `sentence-transformers/all-MiniLM-L6-v2` executed via optimized ONNX Runtime (CPU, ~100 MB RAM).
   * **Reranker:** Cross-Encoder (`ms-marco-MiniLM-L-6-v2`) with `-7.0` minimum score cutoff.
   * **Context Boundary:** Enclosed inside `<untrusted_wms_codebase_context>` security envelopes.

2. **Ospysin Documentation Domain (`ospysin_docs_knowledge`):**
   * **Scope:** 246 specification chunks (21 markdown specifications and 5 ADRs).
   * **Chunking:** `MarkdownSpecChunker` preserving hierarchical header trees, code blocks, and markdown tables.
   * **Calibrated Gating:** `-2.5` minimum score threshold rejecting out-of-domain queries.
   * **Context Boundary:** Enclosed inside `<untrusted_ospysin_docs_context>` security envelopes.

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

### Available Agent MCP Tools (9 Tools)

#### Domain 1: WMS Codebase Intelligence
* `search_wms_code(query, top_n)` — Semantic search across Java, Vue, and SQL codebases with cross-encoder reranking.
* `find_symbol_declaration(symbol_name)` — Deterministic exact verification of symbol declarations (classes, interfaces, methods, records).
* `get_entity_and_schema(table_or_entity)` — Retrieve table DDL schemas, Flyway migrations, and JPA entity definitions.
* `search_wms_security(topic)` — Targeted security search (`@PreAuthorize`, JWT filters, role checks, CORS).
* `get_rag_status()` — Multi-collection metrics, indexed chunk counts, vector dimensions, and model status.
* `reindex_wms_codebase()` — Trigger isolated reindexing of the WMS codebase.

#### Domain 2: Ospysin Documentation Intelligence
* `search_ospysin_docs(query, top_n)` — Semantic specification search with heading hierarchy preservation and reranking.
* `get_ospysin_doc(path_or_slug, section)` — Deterministic exact document retrieval with optional section extraction.
* `reindex_ospysin_docs(confirm)` — Trigger isolated reindexing of the Ospysin specification corpus.

---

## 4. Project Documentation

The documentation is organized by domain in the [`docs/`](docs/README.md) directory:

* [`docs/README.md`](docs/README.md) — **Master Documentation Hub:** Navigation and domain comparison matrix.
* **Domain 1 — WMS Codebase RAG:**
  * [`docs/wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md`](docs/wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) — Post-fix retrieval evaluation report (1,261 chunks, Hit@1 83.3%, Hit@5 100%, MRR 0.894).
  * [`docs/wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](docs/wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) — Architecture registry, AST parsing, and security boundary.
  * [`docs/wms-codebase-rag/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](docs/wms-codebase-rag/WMS_ADVERSARIAL_VERIFICATION_REPORT.md) — Adversarial findings verification pass over DEF-01..DEF-26.
  * [`docs/wms-codebase-rag/WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md`](docs/wms-codebase-rag/WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md) — Inventory catalog of all verified WMS codebase and RAG defects.
  * [`docs/wms-codebase-rag/WMS_REMEDIATION_ROADMAP.md`](docs/wms-codebase-rag/WMS_REMEDIATION_ROADMAP.md) — Implementation roadmap across 4 remediation waves.
* **Domain 2 — Ospysin Documentation RAG:**
  * [`docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md`](docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) — Post-fix evaluation report (246 chunks, Hit@1 96.4%, Hit@4 96.4%, MRR 0.964).
  * [`docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md`](docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md) — Investigation and 4-phase remediation roadmap (DEF-01..DEF-09).
* **Reference & Historical Archives:**
  * [`docs/archive/README.md`](docs/archive/README.md) — Archive of historical technical audits, pre-audit guides, and initial benchmarks.
  * [`inbound-storage-dispatch/README.md`](inbound-storage-dispatch/README.md) — Domain specification, entity lifecycle, and REST API documentation for WMS.
  * [`wms-code-rag/README.md`](wms-code-rag/README.md) — Standalone guide for the RAG MCP service.
