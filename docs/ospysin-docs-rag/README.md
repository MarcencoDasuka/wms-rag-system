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

The Ospysin Documentation RAG system indexes the full architectural specification, ADRs, audit reports, and remediation plans of the Ospysin security platform:
- **Specifications & Audit Corpus:** 49 markdown files:
  - 16 core architectural specifications (`00-VISION.md` to `15-GLOSSARY.md`).
  - 5 Architecture Decision Records (`adr/0001` through `0005`).
  - 14 phase audit reports (`audits/phase-03-audit.md` to `phase-16-audit.md`).
  - 14 phase remediation plans (`remediation/phase-03-remediation.md` to `phase-16-remediation.md`).
- **Snapshot Exclusion Invariant:** Raw code snapshots (`audits/snapshots/*.md`, containing full file dumps and test logs) are explicitly filtered via `ignore_dirs: ["snapshots"]`, preventing 3,687 chunks of multi-version code dumps from diluting vector retrieval.
- **Canonical Repository Binding:** Configured to index the primary active repository directly from `Desktop/Ospysin/docs` (`../../Ospysin/docs`).
- **Collection Isolation:** Stored in an isolated ChromaDB collection `ospysin_docs_knowledge` (`data/chroma`, 460 chunks total).
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
