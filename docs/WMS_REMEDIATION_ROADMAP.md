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
| **Batch 2 (Wave 1 & 2 Auth/Schema)** | Object authorization (BOLA/IDOR), schema index & deletion guards | **DEF-03, DEF-04, DEF-06, DEF-11, DEF-12** | **`COMPLETED`** | 32/32 Passed (10.9s) |
| **Batch 3 (Wave 2 Concurrency)** | Concurrency controls, versioning, async dispatch & integrity | **DEF-09, DEF-10, DEF-15, DEF-20** | **`COMPLETED`** | 21/21 Passed (5.8s) |
| **Batch 4 (Wave 3 REST & DTO)** | Pagination, authorization scopes & input boundary validation | **DEF-07, DEF-17, DEF-19, DEF-21** | **`COMPLETED`** | 29/29 Passed (6.6s) |
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
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 2)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`Order.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Order.java) & [`Replenishment.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Replenishment.java) (added `createdBy`)
  * [`OrderRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/OrderRepository.java) & [`ReplenishmentRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/ReplenishmentRepository.java) (`findAllAccessibleBySupervisor`)
  * [`OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java) & [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
  * [`V38__remediation_batch_2_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V38__remediation_batch_2_schema.sql)
* **Invariant:** Access granted if `isDev() OR createdBy == currentUser OR task.supervisor == currentUser`. Unassigned `CREATED` entities accessible to creator. List endpoints return scoped results.
* **Verification:** `Def03OrderBolaRemediationTest` (8/8 passed), `Def04ReplenishmentBolaRemediationTest` (8/8 passed), `Def06OrderExtendedScopingRemediationTest` (3/3 passed).

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
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 3)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`V39__remediation_batch_3_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V39__remediation_batch_3_schema.sql)
  * [`Order.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Order.java)
  * [`Replenishment.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Replenishment.java)
  * [`OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java)
  * [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
* **Invariant:** Concurrent requests to assign an order or replenishment must be mutually exclusive.
* **Remediation Note:** Added `@Version` to `Order` and `Replenishment` along with Flyway `V39` DDL. Eliminated mass `@Modifying` status update in `assignOrderCascade`, enforcing `saveAndFlush` optimistic checks before task allocation. Confirmed losing transaction rollback without orphan task or allocation side-effects.
* **Verification:** `Def09OrderAssignmentConcurrencyRemediationTest` (2/2 passed), `Def10ReplenishmentAssignmentConcurrencyRemediationTest` (2/2 passed).

---

### Task 2.2: Fix Cross-Product Replenishment Location Unique Constraint (`DEF-11`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 2)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`V38__remediation_batch_2_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V38__remediation_batch_2_schema.sql)
  * [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
* **Invariant:** A destination location cannot have multiple active replenishments, regardless of product ID.
* **Remediation Note:** Dropped old compound index, cleaned up duplicate records in V38, added partial unique index `uk_active_replenishment_destination`, and enforced pre-check in `ReplenishmentService`.
* **Verification:** `Def11ReplenishmentDestinationConflictRemediationTest` (4/4 passed), `Def11V38MigrationVerificationTest` (2/2 passed).

---

### Task 2.3: Order Deletion Lifecycle Guard (`DEF-12`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 2)
* **Severity:** `MEDIUM`
* **Affected Files:** [`OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java)
* **Invariant:** Active or in-progress orders (`ASSIGNED`, `IN_PROGRESS`, `PICKED`, `COMPLETED`, `PARTIALLY_COMPLETED`) must not be physically deleted. Deletion permitted only for `CREATED` and `CANCELED`.
* **Remediation Note:** `OrderService.deleteOrderById` strictly enforces `status IN (CREATED, CANCELED)`, throwing `InvalidRequestException` for all non-deletable lifecycle states.
* **Verification:** `Def12OrderDeletionLifecycleRemediationTest` (7/7 passed).

---

### Task 2.4: Asynchronous Email Dispatch (`DEF-15`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 3)
* **Severity:** `MEDIUM`
* **Affected Files:** [`UserService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java), [`EmailService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/EmailService.java)
* **Invariant:** SMTP calls must execute asynchronously outside active database transactions.
* **Remediation Note:** Implemented `dispatchVerificationEmailPostCommit` using `TransactionSynchronizationManager` `afterCommit` hook. Network SMTP execution takes place strictly after connection release, eliminating pool starvation and preventing rollback on SMTP errors.
* **Verification:** `Def15AsyncEmailDispatchRemediationTest` (4/4 passed).

---

## Wave 3 — REST API Robustness, Validations & Data Quality

*Priority: Medium (Prevents DoS, invalid data ingestion, and audit divergence).*

### Task 3.1: Enforce REST API Pagination (`DEF-07`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 4)
* **Severity:** `MEDIUM`
* **Affected Files:**
  * [`PaginationUtils.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/util/PaginationUtils.java)
  * [`SecurityConfig.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/SecurityConfig.java)
  * `OrderController.java`, `InventoryController.java`, `ProductController.java`, `UserController.java`
  * Corresponding Service and Repository layers
* **Invariant:** Return flat JSON `List<T>` body with standard pagination headers (`X-Total-Count`, `X-Total-Pages`, `X-Current-Page`, `X-Page-Size`), preventing OOM without breaking Vue frontend array contract.
* **Remediation Note:** Implemented utility clamp limits (Orders/Inventory/Users: default 50, max 200; Products: default 100, max 500), CORS exposed headers, and Pageable query delegates.
* **Verification:** `Def07UnboundedPaginationRemediationTest` (10/10 passed).

---

### Task 3.2: Method Security on Replenishments (`DEF-17`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 4)
* **Severity:** `MEDIUM`
* **Affected Files:**
  * [`ReplenishmentController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java)
  * [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
* **Invariant:** Only `SUPERVISOR` and `DEV` can read, list, and search replenishment plans. Operators are restricted strictly to `/api/v1/tasks/operator/**`.
* **Remediation Note:** Added `@PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")` across replenishment read endpoints and service defense-in-depth scoping.
* **Verification:** `Def17ReplenishmentSecurityRemediationTest` (6/6 passed).

---

### Task 3.3: Cascading Validation on Composite Requests (`DEF-18`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 1)
* **Severity:** `MEDIUM`
* **Remediation Note:** Added `@NotNull @Valid` to `order` and `lines` collections in `ExtendedOrderCreateRequest`.
* **Verification:** `ExtendedOrderCreateRequestValidationTest` (2/2 passed).

---

### Task 3.4: Replenishment Positive Quantity Validation (`DEF-19`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 4)
* **Severity:** `LOW`
* **Affected Files:**
  * [`ReplenishmentCreateRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentCreateRequest.java)
  * [`ReplenishmentUpdateRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentUpdateRequest.java)
  * [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java)
* **Invariant:** Requested replenishment quantities must be strictly positive integers $\ge 1$.
* **Remediation Note:** Enforced `@NotNull` and `@Min(1)` at DTO validation level and defense-in-depth boundary validation throwing `InvalidRequestException` in `ReplenishmentService`.
* **Verification:** `Def19ReplenishmentQuantityValidationRemediationTest` (10/10 passed).

---

### Task 3.5: Case-Insensitive Unique Indexes (`DEF-20`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 3)
* **Severity:** `MEDIUM`
* **Affected Files:**
  * [`V39__remediation_batch_3_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V39__remediation_batch_3_schema.sql)
  * [`UserRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/UserRepository.java)
  * [`CustomUserDetailsService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/CustomUserDetailsService.java)
  * [`UserService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java)
* **Remediation Note:** Added functional indexes `uk_users_username_lower` and `uk_users_email_lower` on `LOWER(...)` in V39. Updated `UserRepository` with case-insensitive queries. Upgraded `CustomUserDetailsService` login lookup and `UserService` registration/update checks to case-insensitive semantics.
* **Verification:** `Def20CaseInsensitiveUserRemediationTest` (11/11 passed), `Def20V39MigrationVerificationTest` (2/2 passed).

---

### Task 3.6: Synchronize Picking Audit Quantity (`DEF-21`)
* **Status:** **`COMPLETED [VERIFIED]`** (Remediated in Batch 4)
* **Severity:** `LOW`
* **Affected Files:**
  * [`PickingOperatorStrategy.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/allocation/PickingOperatorStrategy.java)
* **Invariant:** Audit trail `recordPickingHistory` must record actual remaining stock post-deduction, preserving point-in-time accuracy and mathematical audit continuity.
* **Remediation Note:** Rescheduled `recordPickingHistory` post-deduction after `executeAllocationCompletion`. Point-in-time quantity captures `stock.getQuantity()` and `previousQuantity = stock.getQuantity() + pickedQuantity`. Post-deduction replenishment trigger verified.
* **Verification:** `Def21PickingAuditRemediationTest` (3/3 passed).

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
