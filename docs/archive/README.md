# Archive Documentation

This directory contains historical analytical and verification artifacts produced during the initial development, security audit, and retrieval evaluation of the WMS Codebase RAG system.

These documents are preserved to maintain reproducibility, compliance audit history, and retrospective analysis. For the current active project state, refer to the links below.

---

## Relationship to Active Documentation

| Archived Document | Purpose / Description | Active Replacement |
| :--- | :--- | :--- |
| [`WMS_FIXES_AND_ARCHITECTURE_GUIDE_PRE_AUDIT_2026.md`](WMS_FIXES_AND_ARCHITECTURE_GUIDE_PRE_AUDIT_2026.md) | Historical architectural guide containing pre-audit assumptions and superseded defect remediation claims. | [`docs/wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md),<br>[`docs/wms/WMS_REMEDIATION_ROADMAP.md`](../wms/WMS_REMEDIATION_ROADMAP.md) |
| [`WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) | Historical unified registry of 29 decisions and 8 audit defect roadmaps. Deprecated and split into two specialized guides. | [`docs/wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md),<br>[`docs/wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`WMS_CODE_RAG_TECHNICAL_AUDIT.md`](WMS_CODE_RAG_TECHNICAL_AUDIT.md) | Initial architectural and security audit (Defects 01–11). All findings remediated and covered by tests. | [`docs/wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`RAG_RETRIEVAL_EVALUATION.md`](RAG_RETRIEVAL_EVALUATION.md) | First search retrieval benchmark across 36 queries (1,163 chunks index, prior to Java AST parser). | [`docs/wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md`](../wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) |
| [`RAG_RETRIEVAL_EVALUATION_REVIEW.md`](RAG_RETRIEVAL_EVALUATION_REVIEW.md) | Methodological adversarial review of the first evaluation, establishing Strict vs. Lenient metric separation. | [`docs/wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md`](../wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) |

---

## Active Documentation Index
1. [`README.md`](../../README.md) — Monorepo overview and root entry point.
2. [`docs/README.md`](../README.md) — Central platform documentation hub.
3. [`docs/wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../wms/WMS_ADVERSARIAL_VERIFICATION_REPORT.md) — Primary authoritative source of truth: full adversarial verification pass over DEF-01..DEF-26 and baseline items.
4. [`docs/wms/WMS_REMEDIATION_ROADMAP.md`](../wms/WMS_REMEDIATION_ROADMAP.md) — Prioritized remediation backlog and implementation roadmap across 5 waves.
5. [`docs/wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../wms-codebase-rag/WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) — Active architectural decision registry, AST parsing, and security boundary for Code RAG.
6. [`docs/wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md`](../wms-codebase-rag/WMS_CODE_RAG_POST_FIX_EVALUATION.md) — Verified post-fix retrieval quality benchmark (1,261 chunks, Hit@1 83.3%).
7. [`docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md`](../ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_POST_FIX_EVALUATION.md) — Post-fix comparative evaluation report for Ospysin documentation RAG (246 chunks, Hit@4 96.4%).
8. [`docs/ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md`](../ospysin-docs-rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md) — Technical investigation and 4-phase remediation roadmap (DEF-01 through DEF-09).
9. [`wms-code-rag/README.md`](../../wms-code-rag/README.md) — RAG MCP server guide, tools, and execution workflows.
10. [`inbound-storage-dispatch/README.md`](../../inbound-storage-dispatch/README.md) — WMS domain specification (workflows, REST API, migrations).
