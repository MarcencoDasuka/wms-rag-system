# Ospysin Documentation RAG — Documentation Index

This directory contains the complete technical documentation, defect investigation roadmap, and empirical evaluation reports for the **Ospysin Documentation RAG** integration inside the WMS Code RAG server.

---

## 1. Directory Structure & Document Map

| Document | Type | Description |
| :--- | :---: | :--- |
| [`OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md`](OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) | **Benchmark Audit** | Empirical post-fix retrieval evaluation report comparing baseline vs post-fix states across 28 positive queries, 7 negative queries, and 9 deterministic lookups. |
| [`OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md`](OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md) | **Remediation Roadmap** | Technical investigation and 4-phase remediation roadmap covering defects DEF-01 through DEF-09. |

---

## 2. Core Capabilities & Architectural Scope

The Ospysin Documentation RAG system indexes the full architectural specification and ADR corpus of the Ospysin security platform:
- **Specifications Corpus:** 21 markdown files (`00-VISION.md` to `15-GLOSSARY.md`, plus 5 ADRs in `adr/`).
- **Collection Isolation:** Stored in an isolated ChromaDB collection `ospysin_docs_knowledge` (`data/chroma`).
- **Context Branding:** Retrieved snippets are structurally isolated under `<untrusted_ospysin_docs_context>`.
- **Calibrated Gating:** Negative query cutoff configured at `min_score: -2.5` (`config.ospysin.min_score`).
- **Tools Provided:**
  - `search_ospysin_docs(query, top_n)` — Semantic search with cross-encoder reranking.
  - `get_ospysin_doc(name_or_id, section)` — Deterministic exact document and section extraction.
  - `reindex_ospysin_docs(confirm)` — Isolated reindexing of documentation.

---

## 3. Related Documentation

- [Central Documentation Hub](../README.md)
- [WMS Core Application Documentation](../wms/README.md)
- [WMS Codebase RAG Index](../wms-codebase-rag/README.md)
- [Historical Archive](../archive/README.md)
- [WMS MCP Server Guide](../../wms-code-rag/README.md)
