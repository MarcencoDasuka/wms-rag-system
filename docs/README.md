# Platform Documentation Hub

Welcome to the centralized documentation repository for the **Warehouse Management System (WMS)** application and its integrated **Codebase & Documentation RAG MCP Server**.

---

## 1. Documentation Architecture Overview

The repository documentation is partitioned into distinct technical domains:

```text
docs/
├── README.md                                             # [You are here] Central Platform Documentation Hub
│
├── wms/                                                  # Domain: WMS Core Application Architecture & Remediation
│   ├── README.md                                         # WMS application docs index and navigation
│   ├── WMS_ADVERSARIAL_VERIFICATION_REPORT.md            # Independent adversarial audit (DEF-01..DEF-26)
│   ├── WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md           # Application defect catalog and disposition
│   └── WMS_REMEDIATION_ROADMAP.md                        # Phased remediation implementation plan (Waves 1-5)
│
├── wms-codebase-rag/                                     # Subsystem: WMS Codebase RAG Intelligence
│   ├── README.md                                         # Codebase RAG index and architecture overview
│   ├── WMS_CODE_RAG_POST_FIX_EVALUATION.md              # Empirical post-fix retrieval benchmark (Hit@1 83.3%, Hit@5 100%)
│   └── WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md     # AST chunking rules, vector store, and security boundaries
│
├── ospysin-docs-rag/                                     # Subsystem: Ospysin Architecture Specs RAG
│   ├── README.md                                         # Documentation RAG index and navigation
│   ├── OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md  # Post-fix comparative evaluation report (Hit@4 96.4%, MRR 0.964)
│   └── OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md  # Phased remediation roadmap (DEF-01..DEF-09)
│
└── archive/                                              # Historical Technical Audits & Superseded Guides
    ├── README.md                                         # Archive index and historical traceability map
    ├── WMS_CODE_RAG_TECHNICAL_AUDIT.md                   # Initial architectural and security audit
    ├── RAG_RETRIEVAL_EVALUATION.md                       # Initial search retrieval benchmark
    ├── RAG_RETRIEVAL_EVALUATION_REVIEW.md                # Methodological review (Strict vs. Lenient)
    ├── WMS_FIXES_AND_ARCHITECTURE_GUIDE_PRE_AUDIT_2026.md# Pre-audit architectural guide
    └── WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md           # Legacy unified decision register
```

---

## 2. Active RAG Subsystems Comparison

| Dimension | WMS Codebase RAG | Ospysin Documentation RAG |
| :--- | :--- | :--- |
| **Domain Scope** | Warehouse execution codebase (`inbound-storage-dispatch`) | Security platform specifications (`Ospysin/docs`) |
| **Indexed Content** | Java 21 Spring Boot, Vue 3, Flyway SQL DDL | 21 Markdown specifications, 5 ADRs |
| **Chroma Collection** | `wms_codebase_knowledge` | `ospysin_docs_knowledge` |
| **Chunking Logic** | `CodeAwareChunker` (AST, regex syntax) | `MarkdownSpecChunker` (Heading tree, tables) |
| **Active Chunks** | 1,261 code chunks | 246 specification chunks |
| **Context Tag** | `<untrusted_wms_codebase_context>` | `<untrusted_ospysin_docs_context>` |
| **Reranker Cutoff** | `-7.0` (code logits) | `-2.5` (`config.ospysin.min_score`) |
| **Primary Tools** | `search_wms_code`, `find_symbol_declaration`, `get_entity_and_schema`, `search_wms_security` | `search_ospysin_docs`, `get_ospysin_doc`, `reindex_ospysin_docs` |
| **Evaluation Report** | [`wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md`](wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) | [`ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md`](ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) |

---

## 3. Quick Reference Links

### WMS Core Application Architecture & Remediation
- [WMS Application Documentation Hub](wms/README.md)
- [WMS Adversarial Verification Report](wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md) — Independent evidence pass (DEF-01..DEF-26).
- [WMS Remediation Roadmap](wms/WMS_REMEDIATION_ROADMAP.md) — Phased batch execution plan across Waves 1 to 5.
- [Inbound Storage Dispatch Domain Spec & REST API](../inbound-storage-dispatch/README.md)

### RAG Subsystems & MCP Guides
- [Root Repository README](../README.md)
- [WMS Code RAG Server Guide](../wms-code-rag/README.md)
- [WMS Code RAG Post-Fix Evaluation](wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) — 1,261 chunks, Hit@1 83.3%, Hit@5 100%, MRR 0.894.
- [Ospysin Docs RAG Post-Fix Evaluation](ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) — 246 chunks, Hit@1 96.4%, Hit@4 96.4%, MRR 0.964.
- [Historical Technical Archive](archive/README.md) — Traceability and initial audit logs.
