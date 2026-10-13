# WMS Codebase RAG — Documentation Index

This directory contains the technical documentation, architectural guides, and empirical retrieval evaluation benchmarks for the **WMS Codebase RAG** subsystem (`wms-code-rag`).

For documentation regarding the core WMS application architecture, defect reports, and remediation roadmaps, see [`docs/wms/`](../wms/README.md).

---

## 1. Directory Structure & Document Map

| Document | Type | Description |
| :--- | :---: | :--- |
| [`WMS_CODE_RAG_POST_FIX_EVALUATION.md`](WMS_CODE_RAG_POST_FIX_EVALUATION.md) | **Benchmark Audit** | Empirical post-fix retrieval quality evaluation report comparing baseline vs post-fix states across 36 frozen benchmark queries and 28 adversarial hard negatives (Hit@1 83.3%, Hit@5 100%, MRR 0.894). |
| [`WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) | **Architecture Guide** | Technical architecture reference, AST chunking rules, vector database configuration, and security boundary guarantees. |

---

## 2. Core Capabilities & Architectural Scope

The WMS Codebase RAG system indexes the full warehouse management codebase (`inbound-storage-dispatch`):
- **Indexed Scope:** Java 21 Spring Boot 3 classes, interfaces, records, methods, `@PreAuthorize` security annotations, Vue 3 Composition API components, and Flyway SQL DDL migrations (1,261 total chunks).
- **Vector Collection:** `wms_codebase_knowledge` in ChromaDB (`data/chroma`).
- **Security Envelope:** Retrieved code snippets enclosed in `<untrusted_wms_codebase_context>` delimiters.
- **Reranker Scoring:** Cross-Encoder (`ms-marco-MiniLM-L-6-v2`) with `-7.0` minimum score cutoff.
- **Agent Tools Provided:**
  - `search_wms_code(query, top_n)` — Semantic search with cross-encoder reranking.
  - `find_symbol_declaration(symbol_name)` — Deterministic exact AST symbol declaration verification.
  - `get_entity_and_schema(table_or_entity)` — Schema DDL and JPA entity lookup.
  - `search_wms_security(topic)` — Specialized security boundary retrieval.
  - `get_rag_status()` — Multi-collection health and dimension metrics.
  - `reindex_wms_codebase()` — Isolated codebase reindexing.

---

## 3. Related Documentation

- [Central Documentation Hub](../README.md)
- [WMS Core Application Documentation](../wms/README.md)
- [Ospysin Documentation RAG Index](../ospysin-docs-rag/README.md)
- [Historical Technical Archive](../archive/README.md)
- [WMS MCP Server Guide](../../wms-code-rag/README.md)
