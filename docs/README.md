# Platform Documentation Hub

Welcome to the centralized documentation repository for the **Warehouse Management System (WMS)** and its integrated **Codebase & Documentation RAG MCP Server**.

---

## 1. Documentation Architecture Overview

The system features two specialized, isolated Retrieval-Augmented Generation (RAG) domains operating through a single FastMCP server interface:

```text
docs/
├── README.md                                             # [You are here] Central Documentation Hub
│
├── wms-codebase-rag/                                     # Domain 1: WMS Codebase Intelligence
│   ├── README.md                                         # Subsystem index and navigation
│   ├── WMS_CODE_RAG_POST_FIX_EVALUATION.md              # Empirical post-fix retrieval evaluation report
│   ├── WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md     # Architecture registry, AST rules, and security
│   ├── WMS_ADVERSARIAL_VERIFICATION_REPORT.md            # Adversarial verification pass (DEF-01..DEF-26)
│   ├── WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md           # Defect inventory and verification catalog
│   └── WMS_REMEDIATION_ROADMAP.md                        # Phased remediation implementation plan
│
├── ospysin-docs-rag/                                     # Domain 2: Ospysin Architecture Specs Intelligence
│   ├── README.md                                         # Subsystem index and navigation
│   ├── OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md  # Post-fix comparative evaluation report
│   └── OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md  # 4-phase remediation roadmap (DEF-01..DEF-09)
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

## 2. Active Domains Comparison

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

### System Implementation & MCP Guides
- [Root Repository README](../README.md)
- [WMS Code RAG Server Guide](../wms-code-rag/README.md)
- [Inbound Storage Dispatch Domain Spec](../inbound-storage-dispatch/README.md)

### Verification & Evaluation Benchmarks
- [WMS Code RAG Post-Fix Evaluation](wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) — 1,261 chunks, Hit@1 83.3%, Hit@5 100%, MRR 0.894.
- [Ospysin Docs RAG Post-Fix Evaluation](ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) — 246 chunks, Hit@1 96.4%, Hit@4 96.4%, MRR 0.964.
- [Historical Archive](archive/README.md) — Historical traceability and initial audit logs.
