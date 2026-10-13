# WMS Codebase RAG — Documentation Index

This directory contains the complete technical documentation, architectural guides, adversarial audits, and evaluation reports for the **WMS Codebase RAG** subsystem (`inbound-storage-dispatch`).

---

## 1. Directory Structure & Document Map

| Document | Type | Description |
| :--- | :---: | :--- |
| [`WMS_CODE_RAG_POST_FIX_EVALUATION.md`](WMS_CODE_RAG_POST_FIX_EVALUATION.md) | **Benchmark Audit** | Empirical post-fix retrieval quality evaluation report comparing baseline vs post-fix states across 36 frozen benchmark queries and 28 adversarial hard negatives. |
| [`WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](WMS_CODE_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) | **Architecture Guide** | Technical architecture reference, AST chunking rules, vector database configuration, and security boundary guarantees. |
| [`WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md) | **Adversarial Audit** | Primary source of truth for defect verification across DEF-01 through DEF-26 with concrete empirical evidence and test reproductions. |
| [`WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md`](WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md) | **Defect Catalog** | Comprehensive inventory and disposition catalog of all verified WMS codebase and RAG defects. |
| [`WMS_REMEDIATION_ROADMAP.md`](WMS_REMEDIATION_ROADMAP.md) | **Roadmap** | Phased remediation implementation plan organized into four strategic execution waves. |

---

## 2. Core Capabilities & Architectural Scope

The WMS Codebase RAG system indexes the full warehouse management codebase:
- **Backend:** Java 21, Spring Boot 3, Spring Data JPA repositories, `@PreAuthorize` security annotations, Flyway SQL DDL migrations (`V1__...`, `V2__...`).
- **Frontend:** Vue 3 Composition API (`<script setup>`, `<template>`), Pinia state stores, Vite configs.
- **Tools Provided:**
  - `search_wms_code(query, top_n)`
  - `find_symbol_declaration(symbol_name)`
  - `get_entity_and_schema(table_or_entity)`
  - `search_wms_security(topic)`
  - `get_rag_status()`
  - `reindex_wms_codebase()`

---

## 3. Related Documentation

- [Root Documentation Index](../README.md)
- [Ospysin Documentation RAG Index](../ospysin-docs-rag/README.md)
- [Historical Archive](../archive/README.md)
- [WMS MCP Server Guide](../../wms-code-rag/README.md)
