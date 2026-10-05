# Archive Documentation

This directory contains historical analytical and verification artifacts produced during the initial development, security audit, and retrieval evaluation of the WMS Codebase RAG system.

These documents are preserved to maintain reproducibility, compliance audit history, and retrospective analysis. For the current active project state, refer to the links below.

---

## Relationship to Active Documentation

| Archived Document | Purpose / Description | Active Replacement |
| :--- | :--- | :--- |
| [`WMS_FIXES_AND_ARCHITECTURE_GUIDE_PRE_AUDIT_2026.md`](WMS_FIXES_AND_ARCHITECTURE_GUIDE_PRE_AUDIT_2026.md) | Historical architectural guide containing pre-audit assumptions and superseded defect remediation claims. | [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../WMS_ADVERSARIAL_VERIFICATION_REPORT.md),<br>[`docs/WMS_REMEDIATION_ROADMAP.md`](../WMS_REMEDIATION_ROADMAP.md) |
| [`WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) | Historical unified registry of 29 decisions and 8 audit defect roadmaps. Deprecated and split into two specialized guides. | [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../WMS_ADVERSARIAL_VERIFICATION_REPORT.md),<br>[`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`WMS_CODE_RAG_TECHNICAL_AUDIT.md`](WMS_CODE_RAG_TECHNICAL_AUDIT.md) | Initial architectural and security audit (Defects 01–11). All findings remediated and covered by tests. | [`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`RAG_RETRIEVAL_EVALUATION.md`](RAG_RETRIEVAL_EVALUATION.md) | First search retrieval benchmark across 36 queries (1,163 chunks index, prior to Java AST parser). | [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) |
| [`RAG_RETRIEVAL_EVALUATION_REVIEW.md`](RAG_RETRIEVAL_EVALUATION_REVIEW.md) | Methodological adversarial review of the first evaluation, establishing Strict vs. Lenient metric separation. | [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) |

---

## Active Documentation Index
1. [`README.md`](../../README.md) — Monorepo overview and root entry point.
2. [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../WMS_ADVERSARIAL_VERIFICATION_REPORT.md) — Primary authoritative source of truth: full adversarial verification pass over DEF-01..DEF-26 and baseline items.
3. [`docs/WMS_REMEDIATION_ROADMAP.md`](../WMS_REMEDIATION_ROADMAP.md) — Prioritized remediation backlog and implementation roadmap across 4 waves.
4. [`wms-code-rag/README.md`](../../wms-code-rag/README.md) — RAG MCP server guide, tools, and execution workflows.
5. [`inbound-storage-dispatch/README.md`](../../inbound-storage-dispatch/README.md) — WMS domain specification (workflows, REST API, migrations).
6. [`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) — Active architectural decision registry, AST parsing, and security boundary for Code RAG.
7. [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) — Verified post-fix retrieval quality benchmark (1,261 chunks, Hit@1 83.3%).
