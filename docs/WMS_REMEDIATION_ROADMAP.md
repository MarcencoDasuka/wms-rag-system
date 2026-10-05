# WMS Remediation Roadmap (ISD Subsystem)

**Authoritative Baseline:** [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md)  
**Status:** Approved for Implementation Planning  
**Execution Strategy:** Strict phased remediation across 4 waves (Zero breaking regressions, test-driven falsification, read-only boundary during audits).

---

## Executive Overview

This roadmap defines the prioritized, four-wave engineering plan to remediate the **23 confirmed defects** and **3 operational/residual risks** identified during the independent adversarial verification audit.

Every task includes:
1. Target files and components.
2. The exact invariant to enforce.
3. Remediation specifications.
4. Falsification and verification criteria.

---

## Wave 1 — Critical Security, Authorization & Lifecycle Blockers

*Priority: Immediate (Blockers for secure multi-user operations).*

### Task 1.1: AI Confirmation Boundary Isolation (`DEF-01`)
* **Severity:** `CRITICAL`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/ai/security/AiToolSecurityBoundary.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/ai/security/AiToolSecurityBoundary.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/ai/service/ChatbotService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/ai/service/ChatbotService.java)
* **Invariant:** The LLM must NEVER receive the confirmation token in tool return strings or conversation messages. Mutating actions require explicit out-of-band user approval.
* **Remediation:**
  1. Refactor `requireConfirmation()` so that tokens are stored in the server session/cache and dispatched to the frontend via a separate WebSocket / SSE / UI event channel.
  2. The tool response returned to the LLM must only contain: `"ACTION_PENDING: Confirmation requested from human supervisor. Awaiting UI approval."`
  3. Ensure tool execution fails if the LLM attempts to pass a fabricated or self-generated token.
* **Verification:** Automated unit test verifying that prompt tool response contains no token string, and autonomous tool calling without out-of-band approval is rejected.

---

### Task 1.2: Purge Plaintext Seed Credentials (`DEF-02`)
* **Severity:** `CRITICAL`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/resources/db/migration/V31__seed_warehouse_data_final.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/resources/db/migration/V31__seed_warehouse_data_final.sql)
* **Invariant:** Passwords must never exist in plaintext in version control or migration files.
* **Remediation:**
  1. Remove lines 6–39 from `V31` (the markdown comments containing plaintext passwords).
  2. Create a clean migration or developer documentation note explaining that dev passwords follow the local secure secret convention, without hardcoding them in SQL.
* **Verification:** Automated grep assertion ensuring zero plaintext passwords remain in `src/main/resources/db/migration/`.

---

### Task 1.3: Enforce Supervisor Object-Level Authorization (BOLA/IDOR) (`DEF-03`, `DEF-04`, `DEF-06`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/controller/OrderController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/controller/OrderController.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/controller/ReplenishmentController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/controller/ReplenishmentController.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/ReplenishmentService.java)
* **Invariant:** Supervisors can only view, update, cancel, or delete orders and replenishments they own (or admins across all).
* **Remediation:**
  1. In `OrderService`:
     * Introduce `validateSupervisorOwnership(Order order)` comparing `order.getCreatedBy()` with `securityFacade.getCurrentUsername()`.
     * Apply check to `getOrder`, `updateOrder`, `updateExtendedOrder`, and `deleteOrderById`.
     * In `getAllExtendedOrders()`, filter by `findByCreatedBy(currentUsername)` unless caller has `ROLE_DEV` / `ROLE_ADMIN`.
  2. In `ReplenishmentService`:
     * Validate ownership in `cancelReplenishment` and `deleteReplenishment`.
* **Verification:** Integration tests verifying that Supervisor A attempting to mutate or read Supervisor B's entities receives `403 Forbidden`.

---

### Task 1.4: Eliminate Client `userId` Data Flow in Audit Trail (`DEF-05`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/dto/inventory/AddStockRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/dto/inventory/AddStockRequest.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/dto/inventory/RemoveStockRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/dto/inventory/RemoveStockRequest.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/InventoryService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/InventoryService.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/validator/InventoryAdjustmentValidator.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/validator/InventoryAdjustmentValidator.java)
* **Invariant:** Audit trail entries must strictly identify the authenticated principal; client-supplied actor IDs are prohibited.
* **Remediation:**
  1. Remove `userId` field completely from `AddStockRequest` and `RemoveStockRequest`.
  2. Remove `userId` validation from `InventoryAdjustmentValidator`.
  3. In `InventoryService`, obtain the actor via `userRepository.findByUsername(securityFacade.getCurrentUsername())`.
* **Verification:** Test verifying that client JSON cannot supply `userId` and audit history accurately records the authenticated principal.

---

### Task 1.5: Fix Fulfillment Order Status Completion State Machine (`DEF-13`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/strategy/PickingOperatorStrategy.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/strategy/PickingOperatorStrategy.java)
* **Invariant:** Orders with 100% completed order lines must transition to `OrderStatus.COMPLETED`.
* **Remediation:**
  1. In `handleOrderCompletion(Order order)`:
     ```java
     boolean allCancelled = order.getOrderLines().stream()
         .allMatch(line -> line.getStatus() == OrderLineStatus.CANCELLED);
     boolean allCompletedOrCancelled = order.getOrderLines().stream()
         .allMatch(line -> line.getStatus() == OrderLineStatus.COMPLETED || line.getStatus() == OrderLineStatus.CANCELLED);
     boolean anyCompleted = order.getOrderLines().stream()
         .anyMatch(line -> line.getStatus() == OrderLineStatus.COMPLETED);

     if (allCancelled) {
         order.setStatus(OrderStatus.CANCELLED);
     } else if (allCompletedOrCancelled && anyCompleted) {
         order.setStatus(OrderStatus.COMPLETED);
     } else {
         order.setStatus(OrderStatus.PARTIALLY_COMPLETED);
     }
     ```
* **Verification:** Integration test verifying order reaches `COMPLETED` when all lines are picked.

---

## Wave 2 — Concurrency, Transactions & Database Integrity

*Priority: High (Prevents data corruption, deadlocks, and connection exhaustion).*

### Task 2.1: Implement Concurrency Locks on Order & Replenishment (`DEF-09`, `DEF-10`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/model/Order.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/model/Order.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/model/Replenishment.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/model/Replenishment.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/repository/OrderRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/repository/OrderRepository.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/repository/ReplenishmentRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/repository/ReplenishmentRepository.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/ReplenishmentService.java)
* **Invariant:** Concurrent requests to assign an order or replenishment must be mutually exclusive.
* **Remediation:**
  1. Add `@Version private Long version;` to `Order` and `Replenishment` entities.
  2. Add pessimistic lock lookup methods: `findByIdForUpdate(Long id)`.
  3. In `OrderService.assignOrder` and `ReplenishmentService.assignReplenishment`, acquire write lock before validating status and creating tasks.
* **Verification:** Concurrency integration test executing parallel threads on the same entity ID asserting that exactly one succeeds and the other fails gracefully with 409 Conflict.

---

### Task 2.2: Fix Cross-Product Replenishment Location Unique Constraint (`DEF-11`)
* **Severity:** `HIGH`
* **Affected Files:**
  * New Flyway migration: `V37__fix_replenishment_destination_unique_index.sql`
* **Invariant:** A destination location cannot have multiple active replenishments, regardless of product ID.
* **Remediation:**
  1. Drop index `uq_active_replenishment_product_destination`.
  2. Create partial index on destination only:
     ```sql
     CREATE UNIQUE INDEX uq_active_replenishment_destination
     ON replenishments(destination_location_id)
     WHERE status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');
     ```
* **Verification:** DB test asserting that creating two replenishments for different products into the same location fails with unique constraint violation immediately on insertion.

---

### Task 2.3: Order Deletion Lifecycle Guard (`DEF-12`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/OrderService.java)
* **Invariant:** Active orders (`ASSIGNED`, `IN_PROGRESS`) must not be physically deleted.
* **Remediation:**
  1. In `deleteOrderById(Long id)`, validate:
     ```java
     if (order.getStatus() == OrderStatus.ASSIGNED || order.getStatus() == OrderStatus.IN_PROGRESS) {
         throw new IllegalStateException("Cannot delete active order in status: " + order.getStatus());
     }
     ```
* **Verification:** Test verifying that attempting to delete an `IN_PROGRESS` order throws 409/400.

---

### Task 2.4: Asynchronous Email Dispatch (`DEF-15`)
* **Severity:** `HIGH`
* **Affected Files:**
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/UserService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/UserService.java)
  * [`inbound-storage-dispatch/src/main/java/com/wms/service/EmailService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/wms/service/EmailService.java)
* **Invariant:** SMTP calls must execute asynchronously and outside active database transactions.
* **Remediation:**
  1. Annotate `EmailService.sendVerificationEmail` with `@Async`.
  2. Or trigger email dispatch via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
* **Verification:** Unit test asserting transaction commits before SMTP network socket opens.

---

## Wave 3 — REST API Robustness, Validations & Data Quality

*Priority: Medium (Prevents DoS, invalid data ingestion, and audit divergence).*

### Task 3.1: Enforce REST API Pagination (`DEF-07`)
* **Severity:** `HIGH`
* **Affected Controllers:** `OrderController`, `InventoryController`, `ProductController`, `UserController`.
* **Remediation:** Migrate all collection endpoints from `List<T>` to `Page<T>` with `Pageable` parameters and default max limit of 100.

### Task 3.2: Method Security on Replenishments (`DEF-17`)
* **Severity:** `MEDIUM`
* **Affected Files:** `ReplenishmentController.java`
* **Remediation:** Add `@PreAuthorize("hasRole('SUPERVISOR') or hasRole('ADMIN')")` to read methods.

### Task 3.3: Cascading Validation on Composite Requests (`DEF-18`)
* **Severity:** `MEDIUM`
* **Affected Files:** `ExtendedOrderCreateRequest.java`
* **Remediation:** Add `@Valid @NotNull` to `private List<OrderLineCreateRequest> items;`.

### Task 3.4: Replenishment Positive Quantity Validation (`DEF-19`)
* **Severity:** `LOW`
* **Affected Files:** `ReplenishmentCreateRequest.java`
* **Remediation:** Replace `@Min(0)` with `@Min(1)`.

### Task 3.5: Case-Insensitive Unique Indexes (`DEF-20`)
* **Severity:** `MEDIUM`
* **Affected Migrations:** Add `V38__add_lower_unique_indexes.sql` creating functional unique indexes using `LOWER(username)`, `LOWER(email)`, `LOWER(barcode)`.

### Task 3.6: Synchronize Picking Audit Quantity (`DEF-21`)
* **Severity:** `MEDIUM`
* **Affected Files:** `PickingOperatorStrategy.java`
* **Remediation:** Move history creation to occur post-decrement, logging actual units picked and remaining stock.

---

## Wave 4 — AI Tool Boundaries, Frontend & QA Hardening

*Priority: Normal (Operational hygiene, UI integrity, and automated test reliability).*

### Task 4.1: Decouple Startup Vector Indexing (`DEF-14`)
* **Severity:** `MEDIUM`
* **Affected Files:** `ProductVectorIndexer.java`
* **Remediation:** Gate startup indexing behind `wms.ai.indexing.enabled=false` by default, or make it run asynchronously after startup.

### Task 4.2: AI Worker Active Status Filter (`DEF-24`)
* **Severity:** `MEDIUM`
* **Affected Files:** `WarehouseAiTools.java`
* **Remediation:** Add `.filter(User::getIsActive)` when querying eligible operators.

### Task 4.3: Optimize AI Stock Querying (`DEF-25`)
* **Severity:** `MEDIUM`
* **Affected Files:** `InventoryMutatingAiTools.java`
* **Remediation:** Replace `findAllByAvailableIsTrue().stream().filter(...)` with targeted DB query `stockRepository.findAvailableByProductId(productId)`.

### Task 4.4: Authentic Frontend Test Suite (`DEF-16`)
* **Severity:** `HIGH`
* **Affected Files:** `wmsFront/test/def02_user_id_dataflow.test.js`, `def05_conflict_event.test.js`
* **Remediation:** Rewrite tests using Vue Test Utils and Pinia testing harnesses to test actual store actions and component interactions rather than dummy local mock functions.

### Task 4.5: Validate Stored Roles on App Reload (`DEF-22`)
* **Severity:** `MEDIUM`
* **Affected Files:** `wmsFront/src/stores/auth.js`
* **Remediation:** On app initialization, trigger `/api/v1/auth/me` to refresh role from the server rather than trusting `sessionStorage`.

### Task 4.6: Dynamic Base API URL Configuration (`DEF-23`)
* **Severity:** `LOW`
* **Affected Files:** `wmsFront/src/api/index.js`
* **Remediation:** Use `import.meta.env.VITE_API_BASE_URL || '/api'`.

### Task 4.7: Clean Up Dead Route in SecurityConfig (`DEF-26`)
* **Severity:** `LOW`
* **Affected Files:** `SecurityConfig.java`
* **Remediation:** Remove dead matcher `.requestMatchers("/api/operator/**")`.

---

## Definition of Done (DoD) per Wave

1. [ ] **Code Changes:** Minimal, targeted changes adhering to established project patterns.
2. [ ] **Tests:** Unit and integration tests written and passing for all changed code.
3. [ ] **No Regression:** All existing test suites pass.
4. [ ] **Verification:** Read-only verification pass confirms that defect is resolved and no longer reachable.
5. [ ] **Documentation:** Update status in `WMS_REMEDIATION_ROADMAP.md` from `PENDING` to `RESOLVED`.
