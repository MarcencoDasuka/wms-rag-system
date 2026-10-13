# Ospysin Documentation RAG — Comprehensive Investigation and Remediation Roadmap

**Investigation Date:** October 13, 2026  
**Git Working Tree (Baseline State):** Branch `main`, clean synchronization with `origin/main`. Uncommitted changes:
- `modified:` `.gitignore`, `wms-code-rag/config.yaml`, `wms-code-rag/docker-compose.yml`, `wms-code-rag/src/config.py`, `wms-code-rag/src/mcp_server.py`, `wms-code-rag/src/retriever.py`
- `untracked:` `wms-code-rag/eval/`, `wms-code-rag/src/doc_chunker.py`, `wms-code-rag/src/doc_indexer.py`, `wms-code-rag/src/doc_service.py`, `wms-code-rag/tests/test_ospysin_docs.py`  
**Ospysin Documentation Revision:** `052701ec936f255ff03b8713a9294432942f4e57` (repository `../Ospysin`, 21 specification and ADR files).  
**Target Document Status:** Engineering roadmap and evidence base for subsequent remediation phases. No changes to code, configurations, or data were performed within the audit task itself.

---

## 1. Executive Summary

This document presents a comprehensive, evidence-first engineering investigation into the integration of the **Ospysin Documentation RAG** subsystem into the **WMS Code RAG** server infrastructure.

### 1.1. Key Investigation Findings
1. **Functional Viability:** The basic integration loop is **operational and verified in runtime**. In the `wms-code-rag-server` Docker container, the FastMCP server boots successfully, registers all 3 specialized tools (`search_ospysin_docs`, `get_ospysin_doc`, `reindex_ospysin_docs`), and serves live requests over SSE transport.
2. **Vector Collection Isolation:** The isolation invariant between the WMS warehouse codebase index (`wms_codebase_knowledge`, 1826 chunks) and the Ospysin documentation index (`ospysin_docs_knowledge`, 246 chunks) is **fully confirmed**. Collections are physically and logically segregated; reindexing and chunk deletion operations are strictly isolated.
3. **Specialized Chunker Superiority:** The new [`MarkdownSpecChunker`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py#L81-L145) significantly outperforms the legacy WMS chunker: on the benchmark positive set of 28 queries, it achieves **Hit@1 = 1.000, Hit@4 = 1.000, MRR = 1.000** (compared to Hit@1 = 0.893, MRR = 0.902 for baseline).
4. **Critical Failure Points & Defects:**
   - **DEF-01 (Rejection Threshold, P1):** The reranker cutoff threshold [`min_score: -7.0`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml#L30) was inherited from WMS code and is excessively lenient for documentation. False premises (queries about Kafka, Redis/Celery) and nonexistent features (push notifications) yield scores between `-3.5` and `-4.0`, penetrating agent prompts as valid results. When reranking is disabled, the threshold `similarity_threshold: 0.10` admits **100% of irrelevant queries**.
   - **DEF-02 (Git Revision in Docker, P1):** In the production Docker container, the `git` utility is absent and `.git` is not mounted. The metadata field `source_commit` is permanently `"unknown"`, violating the source traceability invariant.
   - **DEF-03 (Test Environment Gap, P1):** Environment disparity between host and container: 5 WMS test files and 1 integration test fail on the host due to the missing `mcp` package, while the container lacks `pytest` and does not mount tests.
   - **DEF-04 (`get_doc` Retrieval Performance, P2):** Method [`get_ospysin_doc`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L94-L152) dumps the entire collection (246 chunks) from ChromaDB into Python memory on every call, bypassing indexed filtering (`where={"doc_id": ...}`).
   - **DEF-05 (XML Tag Semantics, P2):** Documentation context is returned under the `<untrusted_wms_codebase_context>` tag, misleading downstream LLM agents.
   - **DEF-06 (CLI Flag Contract, P2):** Asymmetry in `--auto-index`: background Ospysin indexing triggers unconditionally on an empty database, ignoring the CLI flag.
   - **DEF-07 (Masked Test Assertions, P2):** In `test_ospysin_docs.py`, search validation includes `or out.startswith("NO_MATCH")`, keeping the test green even under complete retrieval failure.
   - **DEF-08 (Absence of Dev Container, P2):** Lacks a reproducible Docker profile for executing FastMCP integration tests.
   - **DEF-09 (I/O Fault Tolerance, P3):** Read errors on a single file crash the entire indexing run rather than logging an isolated warning.

---

## 2. Scope, Methodology & Limitations

### 2.1. Investigation Scope
- **Target Modules:** The entire Ospysin Docs RAG subsystem ([`doc_chunker.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py), [`doc_indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py), [`doc_service.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py)), integration points in [`config.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/config.py), [`config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml), [`mcp_server.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py), [`retriever.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py), deployment infrastructure ([`Dockerfile`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile), [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml)), test harness [`tests/test_ospysin_docs.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py), and evaluation benchmark [`eval/run_eval.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/eval/run_eval.py).
- **Adjacent Modules (Read-Only Compatibility Audit):** WMS code search pipeline ([`indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/indexer.py), [`chunker.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/chunker.py), [`vector_store.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/vector_store.py)), existing WMS tests.

### 2.2. Verification Methodology
1. **Evidence-First & Zero Mutation:** No production edits to code, configs, or indexes. All dynamic tests were run either via read-only calls to the live MCP server or in disposable temporary directories (`tempfile.TemporaryDirectory`).
2. **Runtime Falsification:** Verification of earlier audit claims through direct container queries via `docker exec`, file descriptor inspection, and log analysis.
3. **Adversarial Retrieval Evaluation:** Construction of an independent matrix of 23 queries across 12 orthogonal difficulty tiers, recording raw cross-encoder logits and vector cosine similarities.
4. **Static Security and Path Analysis:** Call tracing from MCP endpoints down to ChromaDB, regular expression analysis, character escaping, and data flow validation.

### 2.3. Environment Limitations
- Host OS has Python 3.14.0 without the `mcp` package (FastMCP is not installed in system Python). Consequently, tests importing `src.mcp_server` cannot be collected by `pytest` on the host without environment shims or skips.
- The production Docker container runs Python 3.11 with `mcp`, but lacks `pytest` and does not mount `tests/`. Running `pytest` inside the production container requires a dedicated test stage or image.
- Available tests were run selectively on the host with isolation from incompatible modules.

---

## 3. Current Architecture

The documentation subsystem is designed as an extension atop the WMS Code RAG core, sharing heavy resources (Embedder, Reranker, ChromaDB Client) while strictly segregating storage spaces and service layers.

```
                              +-------------------------------------------+
                              |      MCP Client / AI Assistant (LLM)     |
                              +-------------------------------------------+
                                                    |
                                           HTTP SSE / stdio
                                                    v
+-------------------------------------------------------------------------------------------------------+
|  wms-code-rag (FastMCP Server, port 8000)                                                             |
|                                                                                                       |
|   [WMS Code Tools]                                 [Ospysin Docs Tools]                               |
|   - search_wms_code                                - search_ospysin_docs                              |
|   - find_symbol_declaration                        - get_ospysin_doc                                  |
|   - get_entity_and_schema                          - reindex_ospysin_docs                             |
|   - search_wms_security                            - get_rag_status (combined status report)          |
|   - reindex_wms_codebase                                                                              |
|           |                                                 |                                         |
|           v                                                 v                                         |
|     CodeRetriever                                   OspysinDocsService                                |
|     (WMS Code store)                                (Isolated docs logic)                             |
|           |                                          /              \                                 |
|           |                    CodeRetriever (docs store)      OspysinDocsIndexer                     |
|           |                                 |                           |                             |
|           |                                 |                 MarkdownSpecChunker                     |
|           |                                 |                 (Heading & Table aware)                 |
|           \_________________________________+___________________________/                             |
|                                             |                                                         |
|                       +---------------------+---------------------+                                   |
|                       | Shared Embedder     | Shared Reranker     |                                   |
|                       | (all-MiniLM-L6-v2)  | (ms-marco-MiniLM)   |                                   |
|                       +---------------------+---------------------+                                   |
|                                             |                                                         |
|                       +---------------------+---------------------+                                   |
|                       | PersistentClient (data/chroma)            |                                   |
|                       +-------------------------------------------+                                   |
|                          /                                     \                                      |
|            Collection: wms_codebase_knowledge    Collection: ospysin_docs_knowledge                  |
|            (Java, Vue, SQL chunks)               (Markdown Spec & ADR chunks)                         |
+-------------------------------------------------------------------------------------------------------+
```

### 3.1. Key Components
- **[`doc_chunker.py:MarkdownSpecChunker`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py#L81):** Heading-aware Markdown specification parser. Builds a persistent H1–H6 heading stack, prefixing each chunk with breadcrumbs `[rel_path | H1 > H2 > H3]`. Preserves atomicity of tables and code blocks, repeats Markdown table headers across chunk splits, and re-opens fences.
- **[`doc_indexer.py:OspysinDocsIndexer`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L52):** Scans the specification directory (`../Ospysin/docs`), filters symlinks, hidden files, and irrelevant extensions, generates deterministic SHA-256 chunk IDs, batches embeddings (size 32), upserts chunks, and removes stale entries via `prune_stale_chunks()`. Writes metadata to `.ospysin_index_meta.json`.
- **[`doc_service.py:OspysinDocsService`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L55):** FastMCP-free service layer (enabling direct unit testing). Delivers semantic search with empty index guards, deterministic document/section lookups via `get_doc()` with alias normalization (`adr-3`, `05`, `tool-catalog`), and protected reindexing gated by `confirm=True`.
- **[`mcp_server.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py):** Server entry point. When `config.ospysin.enabled: true`, instantiates `ospysin_service` and registers 3 MCP tools with input query bounds validation (`validate_query`) and chunk clamping (`clamp_top_n`).

---

## 4. Current Runtime and Configuration State

### 4.1. Container and Port Status
Execution of `docker ps` verifies the active container:
```
CONTAINER ID   IMAGE                       STATUS                    PORTS                      NAMES
754461787fe6   wms-code-rag-wms-code-rag   Up 10 minutes (healthy)   127.0.0.1:8000->8000/tcp   wms-code-rag-server
```
- Endpoint `/health`: responds HTTP 200 OK.
- Transport: HTTP SSE at `http://127.0.0.1:8000/sse`.

### 4.2. ChromaDB Storage Status
Direct invocation of `get_rag_status()` reports parameters for both indexes:
- **WMS Codebase Index:**
  - Collection: `wms_codebase_knowledge`
  - Total Indexed Chunks: **1826**
  - Embedding Model: `sentence-transformers/all-MiniLM-L6-v2`
  - Reranker: `cross-encoder/ms-marco-MiniLM-L-6-v2` (Enabled: True)
  - ChromaDB Path: `/app/data/chroma`
- **Ospysin Docs Index:**
  - Collection: `ospysin_docs_knowledge`
  - Docs Dir: `/workspace/ospysin-docs`
  - Total Indexed Chunks: **246**
  - Source Revision: **`unknown`** *(confirms DEF-02)*
  - Indexed At: `2026-10-13T11:12:17.960814+00:00`

### 4.3. Configuration Files
Configuration [`wms-code-rag/config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml):
```yaml
codebase:
  target_dir: "../inbound-storage-dispatch"
  # ...
embeddings:
  default_model: "sentence-transformers/all-MiniLM-L6-v2"
  device: "cpu"

reranking:
  enabled: true
  model_name: "cross-encoder/ms-marco-MiniLM-L-6-v2"
  top_n: 4
  min_score: -7.0   # Shared threshold for WMS and Ospysin (DEF-01)

vector_db:
  persist_dir: "data/chroma"
  collection_name: "wms_codebase_knowledge"

retrieval:
  default_top_k: 12
  similarity_threshold: 0.10

server:
  host: "127.0.0.1"
  port: 8000
  auth_token: ""

ospysin:
  enabled: true
  docs_dir: "../Ospysin/docs"
  collection_name: "ospysin_docs_knowledge"
  chunk_max_chars: 1200
  chunk_overlap_chars: 150
```

---

## 5. Verified Working Components

The following integration elements have been **directly verified through tests, commands, and runtime execution**:

1. **FastMCP Tool Registration (`[VERIFIED]`):**
   - Running `asyncio.run(mcp.list_tools())` in Docker returns all 9 tools.
2. **Semantic Search `search_ospysin_docs` (`[VERIFIED]`):**
   - Query `"SSRF protection policy"` retrieves relevant chunks from `08-THREAT-MODEL.md` (score 0.567) and `07-POLICY-ENGINE.md` (score 0.482).
3. **Deterministic Search `get_ospysin_doc` (`[VERIFIED]`):**
   - `get_ospysin_doc("adr-1")` returns all 6 chunks of ADR 0001 with `relevance="exact"`.
   - `get_ospysin_doc("5", "dns_lookup")` cleanly extracts the `dns_lookup` tool specification.
   - `get_ospysin_doc("model")` returns ambiguity marker `AMBIGUOUS` with candidate list (`03-DOMAIN-MODEL.md`, `08-THREAT-MODEL.md`, `11-DATA-MODEL.md`).
   - `get_ospysin_doc("5", "non_existent")` returns `SECTION_NOT_FOUND` with alphabetized available sections.
4. **Path Traversal Protection (`[VERIFIED]`):**
   - Attempts passing `../../etc/passwd` and `..\..\..\windows\win.ini` return `NOT_FOUND` and legitimate specification lists.
5. **Markup Escaping & Injection Neutralization (`[VERIFIED]`):**
   - Input parameters sanitized via `_esc()` (neutralizing `<` and `>`).
   - Early closing attempts for `</untrusted_wms_codebase_context>` neutralized via regex escaping.
   - Code fences with nested backticks escalate enclosing backtick count to `max_backticks + 1`.
6. **Docker Read-Only Mount (`[VERIFIED]`):**
   - Attempting file write `touch /workspace/ospysin-docs/test_write` rejected with `Read-only file system`.
7. **Production Storage Protection (`[VERIFIED]`):**
   - Guard `_assert_safe_mutation()` in `vector_store.py` aborts tests with `RuntimeError` if mutating default `data/chroma`.
8. **Collection Segregation (`[VERIFIED]`):**
   - `store.clear()` calls for WMS and Ospysin mutate strictly their respective collections.

---

## 6. Complete Findings Register

| ID | Title | Status | Priority | Confidence | Impacted Component |
|:---|:---|:---:|:---:|:---:|:---|
| **DEF-01** | Excessively permissive reranker cutoff (`min_score: -7.0`) and lack of isolation from WMS | **CONFIRMED** | **P1** | High | `config.yaml`, `retriever.py`, `doc_service.py` |
| **DEF-02** | Loss of `source_commit` in Docker (`unknown`) due to missing git and `.git` | **CONFIRMED** | **P1** | High | `Dockerfile`, `docker-compose.yml`, `doc_indexer.py` |
| **DEF-03** | Broken test harness on host (6 tests fail due to `import mcp`) | **CONFIRMED** | **P1** | High | `tests/*`, `Dockerfile`, `docker-compose.yml` |
| **DEF-04** | Inefficient full-table scan on every `get_doc` call | **CONFIRMED** | **P2** | High | `doc_service.py` (`_all_chunks`) |
| **DEF-05** | Mismatched XML tag `<untrusted_wms_codebase_context>` for Ospysin docs | **CONFIRMED** | **P2** | High | `retriever.py` (`format_for_agent`) |
| **DEF-06** | Asymmetric `--auto-index` CLI flag processing for Ospysin | **CONFIRMED** | **P2** | High | `mcp_server.py` (L459) |
| **DEF-07** | Masked search assertion `or out.startswith("NO_MATCH")` in `test_ospysin_docs.py` | **CONFIRMED** | **P2** | High | `tests/test_ospysin_docs.py` (L305) |
| **DEF-08** | Lack of isolated Docker profile for FastMCP integration tests | **CONFIRMED** | **P2** | Medium | `docker-compose.yml`, `Dockerfile` |
| **DEF-09** | Full reindex aborted upon single file I/O error | **CONFIRMED** | **P3** | Medium | `doc_indexer.py` (L140-145) |

---

## 7. Detailed Defect Analysis

### DEF-01: Excessively Permissive Reranker Cutoff (`min_score: -7.0`) and Shared WMS Threshold

* **Status:** **`CONFIRMED`**
* **Priority:** **`P1`** (Critical Retrieval Quality Risk)
* **Confidence Level:** High (empirically confirmed across 23 adversarial queries and raw cross-encoder logit measurements).
* **Exact Locations:**
  - [`wms-code-rag/config.yaml:30`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml#L30): `min_score: -7.0` in `reranking` section.
  - [`wms-code-rag/src/retriever.py:70-75`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L70-L75): shared `self.config.reranking.min_score`.
* **Evidence:**
  1. Query `mobile push notifications for findings` returns `10-UI-UX-SPEC.md` with score `-3.939` (passes `-7.0`).
  2. Query `how to configure the Redis cluster broker for Celery workers in Ospysin` returns `12-DEPLOYMENT.md` with score `-3.564`.
  3. Query `migrating Ospysin microservices to Apache Kafka event streaming` returns `14-ROADMAP.md` with score `-3.859`.
  4. Disabling reranker (`--no-rerank`), default `similarity_threshold: 0.10` admits all 3 negative queries (`0/3` true negatives).
* **Current Behavior:** Threshold `-7.0` admits chunks matching query tokens even if the context describes completely distinct subsystems.
* **Expected Behavior:** Queries targeting absent features or foreign technologies must return `NO_MATCH`.
* **Why It Matters:** Downstream LLM agents receive irrelevant text labelled as evidence and hallucinate Redis, Celery, or push notification capabilities.
* **Remediation Plan:**
  1. Segregate thresholds for source code and documentation. Introduce `config.ospysin.min_score`.
  2. Calibrate documentation cutoff in range `[-2.5, -2.0]`. At `-2.5`, all false premises are rejected (`-3.5`...`-4.0`), while valid concepts remain (`>= +1.4` explicit, `>= -1.0` abstract).
* **Impacted Files:** [`config.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/config.py), [`config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml), [`retriever.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py), [`doc_service.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py).
* **Fix Risks:** Excessively high cutoff (e.g., `> 0.0`) risks dropping abstract semantic concepts lacking exact keywords. Requires validation via `run_eval.py`.
* **Acceptance Criteria:** Positive Hit@4 >= 0.95; True Negatives = 3/3; queries on Redis/Kafka return `NO_MATCH`.

---

### DEF-02: Missing Git Revision Metadata in Docker (`source_commit="unknown"`)

* **Status:** **`CONFIRMED`**
* **Priority:** **`P1`** (Data Provenance & Traceability)
* **Confidence Level:** High (confirmed via `docker exec`).
* **Exact Locations:**
  - [`wms-code-rag/Dockerfile:11-13`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile#L11-L13): `git` package missing from `apt-get install`.
  - [`wms-code-rag/docker-compose.yml:25`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml#L25): mounts `../Ospysin/docs:/workspace/ospysin-docs:ro`. `.git` repository resides at `../Ospysin/.git` and is not mounted.
  - [`wms-code-rag/src/doc_indexer.py:26-50`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L26-L50): `resolve_git_revision` invokes `subprocess.run(["git", "-C", str(docs_dir), "rev-parse", "HEAD"])`.
* **Evidence:**
  1. `docker exec wms-code-rag-server which git` exits with 1 (`git not found`).
  2. `get_rag_status` reports `Source Revision: unknown`.
  3. All indexed ChromaDB chunks contain `source_commit: "unknown"`.
* **Current Behavior:** Indexer in Docker permanently sets revision to `"unknown"`.
* **Expected Behavior:** Indexer records exact Git commit SHA (e.g., `052701ec936f255ff03b8713a9294432942f4e57`).
* **Why It Matters:** Impossibility of verifying which specification revision was indexed, whether local changes were present, or if outputs match repo HEAD.
* **Option Comparison:**
  - *Option A (Mount git + install package):* Add `git` to apt packages, mount `../Ospysin:/workspace/ospysin:ro`, configure `safe.directory` for non-root `appuser`. *Drawbacks:* Bloats image, introduces binary into production container.
  - *Option B (Inject via env/revision file — Recommended):* Indexer reads `os.environ.get("OSPYSIN_SOURCE_COMMIT")` or `.git-revision` file generated prior to startup/build. Fallback to `git` command when available. *Benefits:* Follows Minimal Change Principle, zero additional container binaries, predictable and secure.
* **Impacted Files:** [`doc_indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py), [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml).
* **Acceptance Criteria:** `get_rag_status` in container returns 40-character commit hash or SHA with `-dirty` suffix.

---

### DEF-03: Test Harness Disparity and 6 Test Failures due to Missing FastMCP on Host

* **Status:** **`CONFIRMED`**
* **Priority:** **`P1`** (CI/CD & Regression Barrier)
* **Confidence Level:** High (confirmed via `pytest`).
* **Exact Locations:**
  - [`tests/test_declaration_and_symbol_lookup.py:631`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_declaration_and_symbol_lookup.py#L631): direct import `from src.mcp_server import indexer, retriever`.
  - `tests/test_finding_01_auth.py:6`
  - `tests/test_finding_06_dos_and_concurrency.py:7`
  - `tests/test_finding_09_entity_schema.py:4`
  - `tests/test_finding_11_fastmcp_coupling.py:2`
  - `tests/test_security_invariants_suite.py:20`
* **Evidence:**
  1. Host `python -m pytest tests/` produces 5 collection errors and 1 failure due to `ModuleNotFoundError: No module named 'mcp'`.
  2. Production container lacks `pytest` and `/app/tests`.
* **Current Behavior:** Regression suite cannot be executed cleanly out-of-the-box on developer host without installing FastMCP in system Python.
* **Expected Behavior:** Tests run cleanly in both container and host environments (gracefully skipping FastMCP-coupled tests on the host when missing).
* **Why It Matters:** Developers and CI runners cannot verify codebase health with a single `pytest` command.
* **Remediation Plan:**
  1. Add `pytest.importorskip("mcp")` to affected test files.
  2. Create test container profile (`docker-compose.test.yml`) with dev dependencies installed.
* **Acceptance Criteria:** `python -m pytest tests/` runs cleanly with exit code 0 (all PASSED or SKIPPED, zero FAILED/ERRORS).

---

### DEF-04: Inefficient Full-Table Scan on Every `get_doc` Invocation

* **Status:** **`CONFIRMED`**
* **Priority:** **`P2`** (Performance & Scalability)
* **Confidence Level:** High (source code inspection of [`doc_service.py:83-88`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L83-L88)).
* **Exact Locations:** [`wms-code-rag/src/doc_service.py:83-88, 95`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L83-L88):
  ```python
  def _all_chunks(self) -> List[CodeChunk]:
      data = self.store.collection.get(include=["documents", "metadatas"])
      # ...
      return [_chunk_from_row(i, d, m) for i, d, m in zip(ids, docs, metas)]
  ```
* **Evidence:** `get_doc()` line 95 unconditionally calls `chunks = self._all_chunks()`. All 246 records and text documents are fetched from SQLite on every call.
* **Current Behavior:** Full database table scan and deserialization into Python objects.
* **Expected Behavior:** When querying a known document number (`"05"`) or ADR (`"adr-3"`), query ChromaDB with an exact filter:
  `collection.get(where={"doc_id": value}, include=["documents", "metadatas"])`.
  Cache `available_documents` list in memory after initial index scan for `NOT_FOUND` fallbacks.
* **Why It Matters:** Scales poorly to larger documentations, producing unnecessary CPU/memory allocations.
* **Remediation Plan:**
  1. For `kind in ("adr", "num")`, execute `collection.get(where={"doc_id": value})`.
  2. Cache lightweight document catalog in `OspysinDocsService`.
* **Acceptance Criteria:** `get_doc` queries only chunks belonging to target document; `test_get_doc_*` tests pass without contract modifications.

---

### DEF-05: Mismatched XML Boundary Tag `<untrusted_wms_codebase_context>`

* **Status:** **`CONFIRMED`**
* **Priority:** **`P2`** (Domain Clarity & Prompt Boundaries)
* **Confidence Level:** High.
* **Exact Location:** [`wms-code-rag/src/retriever.py:97, 137`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L97): hardcoded `<untrusted_wms_codebase_context>` in `format_for_agent`.
* **Evidence:** `search_ospysin_docs` wraps documentation chunks in WMS codebase tags:
  ```xml
  <!-- BEGIN UNTRUSTED REPOSITORY CONTEXT -->
  <untrusted_wms_codebase_context>
  ...
  </untrusted_wms_codebase_context>
  ```
* **Current Behavior:** Documentation snippets are tagged as WMS codebase code.
* **Expected Behavior:** Chunks should be wrapped in `<untrusted_ospysin_docs_context>`.
* **Why It Matters:** Downstream models can misinterpret architectural specifications as warehouse implementation code.
* **Remediation Plan:** Add optional `context_tag: str = "untrusted_wms_codebase_context"` parameter to `format_for_agent` and pass `"untrusted_ospysin_docs_context"` from `doc_service.py`.
* **Acceptance Criteria:** Ospysin tool responses contain `<untrusted_ospysin_docs_context>`, WMS code responses maintain `<untrusted_wms_codebase_context>`.

---

### DEF-06: Asymmetric `--auto-index` CLI Flag Handling for Ospysin

* **Status:** **`CONFIRMED`**
* **Priority:** **`P2`** (Lifecycle Contract Consistency)
* **Confidence Level:** High.
* **Exact Location:** [`wms-code-rag/src/mcp_server.py:450-469`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py#L450-L469):
  - L450: `if args.auto_index and indexer.store.count() == 0:` (for WMS)
  - L459: `if ospysin_service is not None and ospysin_service.store.count() == 0:` (for Ospysin — ignores `args.auto_index`).
* **Evidence:** Direct inspection of `main()`.
* **Current Behavior:** Starting server without `--auto-index` skips WMS indexing, but unconditionally triggers Ospysin indexing on an empty collection.
* **Expected Behavior:** Background auto-indexing must respect `--auto-index` consistently for both services.
* **Remediation Plan:** Align condition to: `if args.auto_index and ospysin_service is not None and ospysin_service.store.count() == 0:`.
* **Acceptance Criteria:** Starting with `--no-auto-index` on an empty database does not launch background Ospysin indexing.

---

### DEF-07: Masked Assertion in `test_ospysin_docs.py`

* **Status:** **`CONFIRMED`**
* **Priority:** **`P2`** (Test Assertions Integrity)
* **Confidence Level:** High.
* **Exact Location:** [`wms-code-rag/tests/test_ospysin_docs.py:305`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py#L305):
  ```python
  assert "BEGIN UNTRUSTED REPOSITORY CONTEXT" in out or out.startswith("NO_MATCH")
  ```
* **Evidence:** In unit tests, `FakeEmbedder` generates pseudo-random SHA-256 embeddings where cosine similarity is non-deterministic. The assertion masked missing results with `or out.startswith("NO_MATCH")`.
* **Current Behavior:** Test passes both on successful retrieval and total retrieval failure.
* **Expected Behavior:** Clear separation between response wrapping assertions and retrieval relevance tests using deterministic synthetic embeddings.
* **Remediation Plan:** Implement deterministic mock embedder that guarantees cosine similarity above threshold for test queries.

---

### DEF-08: Absence of Isolated Docker Profile for FastMCP Integration Tests

* **Status:** **`CONFIRMED`**
* **Priority:** **`P2`** (Engineering Infrastructure)
* **Confidence Level:** Medium.
* **Exact Locations:** [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml), [`Dockerfile`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile).
* **Current Behavior:** Dockerfile builds minimal production runtime without test packages. Integration tests cannot run in container without workarounds.
* **Remediation Plan:** Add `test` target to multi-stage Dockerfile and a dedicated `docker-compose.test.yml`.

---

### DEF-09: Unhandled `OSError` Aborting Full Ingestion Loop

* **Status:** **`CONFIRMED`**
* **Priority:** **`P3`** (Fault Tolerance)
* **Confidence Level:** Medium.
* **Exact Location:** [`wms-code-rag/src/doc_indexer.py:140-145`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L140-L145).
* **Evidence:**
  ```python
  for file_path in files:
      rel_path = file_path.relative_to(docs_dir).as_posix()
      for chunk in self.chunker.chunk_file(file_path, rel_path):
  ```
  A permission error or transient descriptor error on one file aborts indexing of all remaining 20 files.
* **Remediation Plan:** Wrap `chunk_file` call in `try...except OSError` with warning log and loop continuation.

---

## 8. Retrieval Evaluation: Results, Interpretation & Gaps

### 8.1. Benchmark Comparison (28 Positive, 3 Negative)
Evaluated in strict isolation over identical source documents:

| Configuration | Chunks | Hit@1 | Hit@4 | MRR | True Negatives (0 hits) | False Positives |
|---|:---:|:---:|:---:|:---:|:---:|:---|
| **Baseline (legacy chunker, rerank=ON)** | 250 | 0.893 | 0.929 | 0.902 | 2/3 (66.7%) | N03 (`mobile push notifications`), misses on Q07, Q09 |
| **New Chunker (rerank=ON, min_score=-7.0)** | 246 | **1.000** | **1.000** | **1.000** | 2/3 (66.7%) | N03 (`mobile push notifications`) |
| **New Chunker (rerank=OFF, sim_thresh=0.10)**| 246 | 0.821 | 0.964 | 0.887 | **0/3 (0.0%)** | N01, N02, N03 — all penetrate 0.10 threshold |

### 8.2. Independent Adversarial Matrix (23 Queries)
Cross-encoder score distribution recorded during adversarial evaluation:

```
[Score Distribution for cross-encoder/ms-marco-MiniLM-L-6-v2]
-10.0   -8.0    -7.0 (legacy) -4.0     -2.5 (calibrated) 0.0     +2.0    +4.0    +7.0
  |-------|-------|-------------|---------|-------------|-------|-------|-------|
          ^                     ^         ^                     ^
      Irrelevant        False Premises   Recommended       Concepts    Exact Matches
      (WMS code,        (Redis, Kafka,   Threshold         & Abstract  (Tools, APIs,
       unrelated)        push notices)                                  Tables)
```

1. **Exact Matches (tools, endpoints, tables):** Scores range from `+6.2` to `+7.7`.
2. **Semantic Concepts:** Scores range from `+1.4` to `+2.5`.
3. **Deep Abstract Concepts (no token overlap):** Scores range from `-1.0` to `+0.5` (e.g., `adr-0003` scored `-0.912`).
4. **False Premises and Negative Cross-Domain Queries:** Scores range from `-3.5` to `-4.0`.
5. **Completely Foreign Domains:** Scores fall below `-7.0` (cleanly rejected).

### 8.3. Interpretation of "Out-of-Scope Features"
Query `automatic exploit generation and payload execution` retrieved `01-SCOPE.md#Out-of-Scope Features & Deferral Rationale` with score `+2.503`.  
**Engineering Conclusion:** This is **NOT a false positive**; it is an accurate retrieval match. The specification explicitly dedicates a section explaining why automated exploit execution was deferred from scope. Supplying this context to an agent is beneficial, enabling it to provide an authoritative answer regarding system boundaries.

---

## 9. Data Provenance and Index Lifecycle

### 9.1. Lifecycle Invariants
1. **Deterministic Chunk IDs:** Hash formula:  
   `sha256(f"{rel_path}:spec:{heading_path}:{occ}:{idx}")[:32]`.  
   Line numbers (`start_line`, `end_line`) are excluded from the hash. Editing empty lines or adding leading paragraphs does not generate ghost chunks.
2. **Atomic Reindexing:** Method `scan_and_index()` validates parameters, gathers chunks, and computes embeddings before touching storage. Errors prior to embedding computation leave existing indexes intact.
3. **Obsolete Chunk Pruning:** Method `prune_stale_chunks()` calculates difference between active IDs and all existing collection IDs, deterministically deleting orphaned chunks from renamed or removed files.

---

## 10. Test Infrastructure and Coverage

### 10.1. Current Test Coverage
- Chunker Unit Tests: H1–H6 hierarchy, fence handling, table atomicity, duplicate headings, private key redaction — **fully covered** ([`test_ospysin_docs.py:78-166`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py#L78-L166)).
- Collection Isolation: Segregation between WMS and Ospysin — **covered** (L170-190).
- Service Tests: `get_doc`, key normalization, path traversal protection, injection escaping — **covered** (L228-291).
- Gaps in Coverage:
  - Missing live semantic search tests with score validation.
  - Missing CLI flag `--auto-index` tests.
  - Missing tests for `resolve_git_revision` fallback paths when Git is missing.

---

## 11. Security, Isolation and Untrusted Context

1. **Untrusted Boundary:** All snippets returned by tools are treated as untrusted data and wrapped with explicit guidance instructing downstream models not to execute instructions contained therein.
2. **Tag Neutralization:** Protection against premature context boundary termination is verified by test `test_get_doc_neutralises_context_boundary_injection`.
3. **Read-Only Storage:** Documentation files are physically mounted read-only inside the container.

---

## 12. Remediation Roadmap by Phase

The remediation plan is organized into 4 sequential phases following risk minimization and priority ordering:

```
+---------------------------------------------------------------------------------------+
| PHASE 1: Test Harness Stabilization & Version Provenance (P1)                         |
| [DEF-02, DEF-03]                                                                      |
| - pytest.importorskip("mcp") in coupled test modules                                  |
| - OSPYSIN_SOURCE_COMMIT env / .git-revision fallback resolution                       |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 2: Retrieval Quality Calibration & Threshold Isolation (P1)                     |
| [DEF-01, DEF-07]                                                                      |
| - Configuration parameter ospysin.min_score (-2.5)                                    |
| - Dedicated threshold in CodeRetriever for documentation                              |
| - Elimination of masked test assertions                                               |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 3: Performance Optimization & Contract Unification (P2)                         |
| [DEF-04, DEF-05, DEF-06]                                                              |
| - Filtered where={"doc_id": ...} query in get_doc                                     |
| - Parameterized XML tag <untrusted_ospysin_docs_context>                              |
| - Symmetrical --auto-index flag synchronization                                       |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 4: Fault Tolerance & Dev/CI Infrastructure (P2, P3)                             |
| [DEF-08, DEF-09]                                                                      |
| - Isolated OSError handling during file ingestion                                     |
| - docker-compose.test.yml for automated end-to-end container testing                  |
+---------------------------------------------------------------------------------------+
```

---

### PHASE 1: Test Harness Stabilization & Version Provenance (P1)
* **Goal:** Guarantee 100% green test execution across any environment and restore Git revision auditing in Docker.
* **Included Defects:** **DEF-02, DEF-03**.
* **Prerequisites:** None.
* **Target Locations:**
  - `wms-code-rag/tests/test_declaration_and_symbol_lookup.py:631`
  - `wms-code-rag/tests/test_finding_01_auth.py:6`
  - `wms-code-rag/tests/test_finding_06_dos_and_concurrency.py:7`
  - `wms-code-rag/tests/test_finding_09_entity_schema.py:4`
  - `wms-code-rag/tests/test_finding_11_fastmcp_coupling.py:2`
  - `wms-code-rag/tests/test_security_invariants_suite.py:20`
  - `wms-code-rag/src/doc_indexer.py:26-50`
  - `wms-code-rag/docker-compose.yml:14`
* **Execution Steps:**
  1. Add `pytest.importorskip("mcp")` in the 6 test files before importing `src.mcp_server`.
  2. In `doc_indexer.py:resolve_git_revision`, add prioritized checks for `os.environ.get("OSPYSIN_SOURCE_COMMIT")` and file `.git-revision`.
  3. In `docker-compose.yml`, pass `OSPYSIN_SOURCE_COMMIT=${OSPYSIN_SOURCE_COMMIT:-unknown}`.
* **Verification:** `python -m pytest tests/ -v`.
* **Exit Criteria:** All tests pass or skip with 0 errors/failures; Docker status reports valid Git SHA.

---

### PHASE 2: Retrieval Quality Calibration & Threshold Isolation (P1)
* **Goal:** Eliminate false positives on false-premise queries and segregate documentation threshold from WMS code.
* **Included Defects:** **DEF-01, DEF-07**.
* **Prerequisites:** Completion of Phase 1.
* **Target Locations:**
  - `wms-code-rag/config.yaml` (`ospysin` section)
  - `wms-code-rag/src/config.py:OspysinDocsConfig`
  - `wms-code-rag/src/retriever.py:CodeRetriever`
  - `wms-code-rag/src/doc_service.py:OspysinDocsService`
  - `wms-code-rag/tests/test_ospysin_docs.py`
* **Execution Steps:**
  1. Add `min_score: float = -2.5` to `OspysinDocsConfig` and `config.yaml`.
  2. Support `min_score_override: Optional[float] = None` in `CodeRetriever.retrieve()`.
  3. Pass `self.config.ospysin.min_score` in `OspysinDocsService.search()`.
  4. Refactor `test_search_returns_wrapped_results_with_provenance`, removing `or out.startswith("NO_MATCH")`.
* **Verification:** Run `eval/run_eval.py` and `run_adversarial_eval.py`.
* **Exit Criteria:** Hit@4 >= 0.96; True Negatives = 3/3; queries on Redis/Kafka return `NO_MATCH`.

---

### PHASE 3: Performance Optimization & Contract Unification (P2)
* **Goal:** Eliminate redundant full database scans, align XML tagging, and synchronize CLI flags.
* **Included Defects:** **DEF-04, DEF-05, DEF-06**.
* **Prerequisites:** Completion of Phase 2.
* **Target Locations:**
  - `wms-code-rag/src/doc_service.py:83-115`
  - `wms-code-rag/src/retriever.py:format_for_agent`
  - `wms-code-rag/src/mcp_server.py:459`
* **Execution Steps:**
  1. Refactor `doc_service.py:get_doc` to use targeted `collection.get(where={"doc_id": value})`.
  2. Cache available document list for `NOT_FOUND` messages.
  3. Add `root_tag` parameter to `format_for_agent` and pass `"untrusted_ospysin_docs_context"`.
  4. Align `mcp_server.py:459` with `args.auto_index`.
* **Exit Criteria:** `get_doc` tests confirm expected outputs; responses contain `<untrusted_ospysin_docs_context>`; `--no-auto-index` suppresses indexing.

---

### PHASE 4: Fault Tolerance & Dev/CI Infrastructure (P2, P3)
* **Goal:** Increase indexer robustness and deliver automated containerized CI test runner.
* **Included Defects:** **DEF-08, DEF-09**.
* **Prerequisites:** Completion of Phase 3.
* **Target Locations:**
  - `wms-code-rag/src/doc_indexer.py:140`
  - `wms-code-rag/docker-compose.test.yml` (new file)
* **Execution Steps:**
  1. Wrap file parsing in `try...except OSError` inside `doc_indexer.py`.
  2. Create `docker-compose.test.yml` for automated test execution in a container with FastMCP.
* **Exit Criteria:** Read errors on unreadable files skip gracefully without crashing the pipeline; tests run cleanly via Docker Compose.

---

## 13. Dependency Matrix

| Task / Defect | Depends On | Blocks |
|:---|:---|:---|
| **DEF-03 (FastMCP Tests)** | None | DEF-07, DEF-08, CI reliability |
| **DEF-02 (`source_commit`)** | None | Release traceability |
| **DEF-01 (Reranker Cutoff)** | DEF-03 | Final retrieval quality sign-off |
| **DEF-07 (Test Assertions)** | DEF-01, DEF-03 | Phase 2 validation |
| **DEF-04 (`get_doc` Scan)** | DEF-07 | Knowledge base scaling |
| **DEF-05 (XML Tag)** | None | Prompt ambiguity elimination |
| **DEF-06 (`--auto-index` Flag)** | None | Server startup predictability |
| **DEF-09 (Indexer I/O)** | None | Fault tolerance on corrupted files |
| **DEF-08 (Dev Container)** | DEF-03 | Automated regression pipeline |

---

## 14. Risk and Priority Matrix

```
   IMPACT
      ^
      |  [DEF-01: RAG False Positives]       [DEF-03: Test Suite Failures]
 High |  [DEF-02: Missing source_commit]
      |
      |  [DEF-04: Full Scan in get_doc]       [DEF-07: Masked Assertions]
 Med  |  [DEF-05: Mismatched XML Tag]         [DEF-06: auto-index Asymmetry]
      |  [DEF-08: Missing Dev Container]
      |
 Low  |  [DEF-09: Indexer I/O Exceptions]
      |
      +------------------------------------------------------------------------>
         Low                        Medium                     High
                                REMEDIATION COMPLEXITY
```

---

## 15. Acceptance Criteria

The integration is considered fully remediated and production-ready only when satisfying the following criteria:

1. **Retrieval Precision & Discrimination:**
   - Hit@4 on positive query set `ospysin_queries.yaml` >= 0.95.
   - 100% of true negative queries (N01, N02, N03) return `NO_MATCH`.
   - False-premise queries regarding Redis, Celery, and Kafka return `NO_MATCH`.
2. **Provenance Traceability:**
   - Tool `get_rag_status` in Docker reports authentic documentation specification commit SHA.
3. **Test Suite Green Bar:**
   - Command `python -m pytest tests/` on host returns exit code 0 (zero FAILED and ERRORS).
   - All unit tests in `test_ospysin_docs.py` execute with strict assertions (no fallback to `NO_MATCH`).
4. **Contract Uniformity:**
   - Documentation tool responses use envelope `<untrusted_ospysin_docs_context>`.
   - Flag `--no-auto-index` disables background auto-indexing for both services.
5. **Zero WMS Regressions:**
   - All WMS codebase search tools retain 100% operational fidelity.

---

## 16. Regression Strategy for WMS Code RAG

To avoid regressions in the existing WMS Code RAG system during remediation:

1. **Configuration Isolation:** All reranker threshold changes must be isolated strictly to `config.ospysin` without altering `config.retrieval` or `config.reranking`.
2. **Backward Compatibility of `format_for_agent`:** Parameter `exact` and new parameter `root_tag` must default to values preserving legacy WMS behavior (`exact=False`, `root_tag="untrusted_wms_codebase_context"`).
3. **WMS Storage Invariance:** Ensure `_assert_safe_mutation()` guard remains active during all test runs. No operation on `ospysin_docs_knowledge` may access `wms_codebase_knowledge`.
4. **WMS Control Suite Verification:** Execute the full suite of WMS codebase search tests after each change phase:
   - `test_declaration_and_symbol_lookup.py`
   - `test_finding_02_secrets.py`
   - `test_finding_03_ghost_chunks.py`
   - `test_finding_04_prompt_injection.py`
   - `test_finding_05_threshold_fallback.py`
   - `test_finding_07_ro_mounts.py`
   - `test_finding_08_parser.py`
   - `test_finding_10_stdio_cleanliness.py`

---

## 17. Unresolved Questions and Decisions

Key engineering decisions finalized for the remediation workflow:

1. **Git SHA Resolution (DEF-02):**
   - *Option 1:* Generate `wms-code-rag/data/.git-revision` file via startup script before Docker run.
   - *Option 2:* Pass `OSPYSIN_SOURCE_COMMIT` via `.env` / `docker-compose.yml`.
   - *Adopted Decision:* Option 2 as the cleanest and most declarative approach.
2. **Cutoff Value for `ospysin.min_score` (DEF-01):**
   - Evaluated across test query sets between `-3.0` and `-2.0`.
   - *Adopted Decision:* Set `-2.5` as the baseline default.
3. **Behavior of `get_doc` on Incomplete Document Stems (DEF-04):**
   - Whether to allow substring matching on stems (e.g. `"catalog"` -> `05-TOOL-CATALOG.md`) or enforce exact keys.
   - *Adopted Decision:* Support substring lookup via in-memory cached stem mapping without querying ChromaDB.

---

## 18. Final Checklist

Verification criteria:
- [x] Git working tree clean of accidental edits.
- [x] Document accessible at `docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md`.
- [x] Team consensus on 4-phase execution sequence.
- [x] Git revision injection mechanism finalized for Docker.
- [x] Isolated test execution environments configured.
