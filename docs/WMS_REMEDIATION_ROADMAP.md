# WMS Remediation Roadmap (ISD Subsystem)

**Authoritative Baseline:** [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md)  
**Verification Catalog:** [`docs/WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md`](WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md)  
**Status:** In Active Execution  
**Execution Strategy:** Strict phased remediation across prioritized batches (Zero breaking regressions, test-driven falsification, immutable V1–V36 historical migrations).

---

## Executive Progress Summary

| Phase / Batch | Scope | Target Defects | Status | Tests Verified |
| :--- | :--- | :--- | :---: | :---: |
| **Batch 1 (Wave 1 Core)** | Critical business & baseline security stoppers | **DEF-02, DEF-13, DEF-18, DEF-05** | **`COMPLETED`** | 16/16 Passed (13.1s) |
| **Batch 2 (Wave 1 & 2 Auth/Schema)** | Object authorization (BOLA/IDOR), schema index & deletion guards | **DEF-03, DEF-04, DEF-06, DEF-11, DEF-12** | `PLANNED` | Awaiting execution |
| **Batch 3 (Wave 2 Concurrency)** | Concurrency controls, versioning, async dispatch & integrity | **DEF-09, DEF-10, DEF-15, DEF-20** | `QUEUED` | — |
| **Batch 4 (Wave 3 REST & DTO)** | Pagination, authorization scopes & input boundary validation | **DEF-07, DEF-17, DEF-19, DEF-21** | `QUEUED` | — |
| **Batch 5 (Wave 4 AI, Frontend & QA)** | AI tool boundaries, frontend config & authentic test harness | **DEF-01, DEF-14, DEF-16, DEF-22, DEF-23, DEF-24, DEF-25, DEF-26** | `QUEUED` | — |

---

## Wave 1 — Critical Security, Authorization & Lifecycle Blockers

*Priority: Immediate (Blockers for secure multi-user operations).*

### Task 1.1: AI Confirmation Boundary Isolation (`DEF-01`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/isd/wms/service/ai/AiToolSecurityBoundary.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/AiToolSecurityBoundary.java)
  * [`inbound-storage-dispatch/src/main/java/com/isd/wms/service/ai/ChatbotService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ChatbotService.java)
* **Invariant:** The LLM must NEVER receive the confirmation token in tool return strings or conversation messages. Mutating actions require explicit out-of-band user approval.

---

### Task 1.2: Purge Plaintext Seed Credentials (`DEF-02`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 1)
* **Severity:** `CRITICAL`
* **Remediation Note:** V31 kept immutable. New Flyway migration `V37__rotate_seed_user_passwords.sql` applied with fresh BCrypt hashes for all 17 seed accounts.
* **Verification:** `Def02SeedCredentialsRemediationTest` (3/3 passed).

---

### Task 1.3: Enforce Supervisor Object-Level Authorization (BOLA/IDOR) (`DEF-03`, `DEF-04`, `DEF-06`)
* **Status:** `IN PROGRESS` (Scheduled for Batch 2)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/OrderController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/OrderController.java)
  * [`inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java)
  * [`inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java)
  * [`inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
* **Invariant:** Supervisors can only view, update, cancel, or delete orders and replenishments they own (or admins across all).

---

### Task 1.4: Eliminate Client `userId` Data Flow in Audit Trail (`DEF-05`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 1)
* **Severity:** `HIGH`
* **Remediation Note:** Actor identity derived strictly from `SecurityFacade.getCurrentUser()` in `InventoryService` and `InventoryAdjustmentValidator`. Client payload `userId` ignored.
* **Verification:** `Def05ActorSpoofingRemediationTest` (3/3 passed).

---

### Task 1.5: Fix Fulfillment Order Status Completion State Machine (`DEF-13`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 1)
* **Severity:** `HIGH`
* **Remediation Note:** In `PickingOperatorStrategy.handleOrderCompletion`, implemented deterministic completion logic: 100% completed lines $\rightarrow$ `COMPLETED`, all cancelled $\rightarrow$ `CANCELED`, shortage/mixed $\rightarrow$ `PARTIALLY_COMPLETED`.
* **Verification:** `PickingOperatorStrategyCompletionTest` (4/4 passed).

---

## Wave 2 — Concurrency, Transactions & Database Integrity

*Priority: High (Prevents data corruption, deadlocks, and connection exhaustion).*

### Task 2.1: Implement Concurrency Locks on Order & Replenishment (`DEF-09`, `DEF-10`)
* **Status:** `OPEN` (Scheduled for Batch 3)
* **Severity:** `HIGH`
* **Affected Files:** `Order.java`, `Replenishment.java`, `OrderService.java`, `ReplenishmentService.java`
* **Invariant:** Concurrent requests to assign an order or replenishment must be mutually exclusive.

---

### Task 2.2: Fix Cross-Product Replenishment Location Unique Constraint (`DEF-11`)
* **Status:** `IN PROGRESS` (Scheduled for Batch 2)
* **Severity:** `HIGH`
* **Affected Files:** New Flyway migration `V38__fix_replenishment_destination_unique_index.sql`
* **Invariant:** A destination location cannot have multiple active replenishments, regardless of product ID.

---

### Task 2.3: Order Deletion Lifecycle Guard (`DEF-12`)
* **Status:** `IN PROGRESS` (Scheduled for Batch 2)
* **Severity:** `MEDIUM`
* **Affected Files:** [`OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java)
* **Invariant:** Active or in-progress orders (`ASSIGNED`, `IN_PROGRESS`) must not be physically deleted.

---

### Task 2.4: Asynchronous Email Dispatch (`DEF-15`)
* **Status:** `OPEN` (Scheduled for Batch 3)
* **Severity:** `MEDIUM`
* **Affected Files:** `UserService.java`, `EmailService.java`
* **Invariant:** SMTP calls must execute asynchronously outside active database transactions.

---

## Wave 3 — REST API Robustness, Validations & Data Quality

*Priority: Medium (Prevents DoS, invalid data ingestion, and audit divergence).*

### Task 3.1: Enforce REST API Pagination (`DEF-07`)
* **Status:** `OPEN` (Scheduled for Batch 4)
* **Severity:** `MEDIUM`
* **Affected Controllers:** `OrderController`, `InventoryController`, `ProductController`, `UserController`.

---

### Task 3.2: Method Security on Replenishments (`DEF-17`)
* **Status:** `OPEN` (Scheduled for Batch 4)
* **Severity:** `MEDIUM`
* **Affected Files:** `ReplenishmentController.java`

---

### Task 3.3: Cascading Validation on Composite Requests (`DEF-18`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 1)
* **Severity:** `MEDIUM`
* **Remediation Note:** Added `@NotNull @Valid` to `order` and `lines` collections in `ExtendedOrderCreateRequest`.
* **Verification:** `ExtendedOrderCreateRequestValidationTest` (2/2 passed).

---

### Task 3.4: Replenishment Positive Quantity Validation (`DEF-19`)
* **Status:** `OPEN` (Scheduled for Batch 4)
* **Severity:** `LOW`
* **Affected Files:** `ReplenishmentCreateRequest.java`

---

### Task 3.5: Case-Insensitive Unique Indexes (`DEF-20`)
* **Status:** `OPEN` (Scheduled for Batch 3)
* **Severity:** `MEDIUM`
* **Affected Files:** New Flyway migration `V39__add_lower_unique_indexes.sql`

---

### Task 3.6: Synchronize Picking Audit Quantity (`DEF-21`)
* **Status:** `OPEN` (Scheduled for Batch 4)
* **Severity:** `LOW`
* **Affected Files:** `AllocationExecutionService.java`

---

## Wave 4 — AI Tool Boundaries, Frontend & QA Hardening

*Priority: Normal (Operational hygiene, UI integrity, and automated test reliability).*

### Task 4.1: Decouple Startup Vector Indexing (`DEF-14`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `LOW`
* **Affected Files:** `ProductVectorIndexer.java`

---

### Task 4.2: AI Worker Active Status Filter (`DEF-24`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `MEDIUM`
* **Affected Files:** `WarehouseAiTools.java`

---

### Task 4.3: Optimize AI Stock Querying (`DEF-25`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `LOW`
* **Affected Files:** `InventoryMutatingAiTools.java`

---

### Task 4.4: Authentic Frontend Test Suite (`DEF-16`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `HIGH`
* **Affected Files:** `wmsFront/test/def02_user_id_dataflow.test.js`, `def05_conflict_event.test.js`

---

### Task 4.5: Validate Stored Roles on App Reload (`DEF-22`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `LOW`
* **Affected Files:** `wmsFront/src/stores/auth.js`

---

### Task 4.6: Dynamic Base API URL Configuration (`DEF-23`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `LOW`
* **Affected Files:** `wmsFront/src/api/index.js`

---

### Task 4.7: Clean Up Dead Route in SecurityConfig (`DEF-26`)
* **Status:** `OPEN` (Scheduled for Batch 5)
* **Severity:** `LOW`
* **Affected Files:** `SecurityConfig.java`

---

## Definition of Done (DoD) per Wave

1. [x] **Code Changes:** Minimal, targeted changes adhering to established project patterns.
2. [x] **Tests:** Unit and integration tests written and passing for all changed code.
3. [x] **No Regression:** All existing test suites pass.
4. [x] **Verification:** Verification pass confirms that defect is resolved and no longer reachable.
5. [x] **Documentation:** Status updated in `WMS_ALL_DEFECTS_VERIFICATION_CATALOG.md` and `WMS_REMEDIATION_ROADMAP.md`.
