# WMS Core Subsystem — Documentation Index

This directory contains the engineering documentation, independent adversarial audits, defect catalogs, and remediation implementation roadmaps for the core **Warehouse Management System (WMS)** application (`inbound-storage-dispatch`).

---

## 1. Directory Structure & Document Map

| Document | Type | Scope | Description |
| :--- | :---: | :---: | :--- |
| [`WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md) | **Adversarial Audit** | Spring Boot & Vue 3 | Independent, evidence-first adversarial verification report covering defect claims DEF-01 through DEF-26 and baseline items against live source code, transactional boundaries, and security filters. |
| [`WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md`](WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md) | **Defect Catalog** | Core Application | Comprehensive inventory, categorization, and formal disposition catalog of all verified WMS application defects and risks. |
| [`WMS_REMEDIATION_ROADMAP.md`](WMS_REMEDIATION_ROADMAP.md) | **Remediation Roadmap** | Full Stack (ISD) | Phased remediation implementation plan organized across 5 execution batches/waves (Security, Auth, Concurrency, DTO/REST, and AI Boundaries). |

---

## 2. Subsystem Architectural Scope

The core WMS application (`inbound-storage-dispatch`) comprises:
- **Backend:** Java 21, Spring Boot 3.4, Spring Data JPA, Flyway migrations (`V1`..`V36`), Spring Security (JWT, BOLA/IDOR protection), PostgreSQL 16.
- **Frontend:** Vue 3 Composition API (`<script setup>`, `<template>`), PrimeVue, Pinia state stores, Vite.
- **Core Business Domains:**
  - Inbound order receiving & dock assignment.
  - Storage allocation, zoning, and capacity validation.
  - Wave picking, operator picking strategy, and dispatch execution.
  - Automatic inventory replenishment and replenisher assignment.
  - Audit logging and human-in-the-loop AI tool confirmation boundaries.

---

## 3. Related Documentation

- [Central Documentation Hub](../README.md)
- [WMS Domain Specification & REST API](../../inbound-storage-dispatch/README.md)
- [WMS Codebase RAG Documentation](../wms-codebase-rag/README.md)
- [Ospysin Documentation RAG](../ospysin-docs-rag/README.md)
- [Historical Technical Archive](../archive/README.md)
