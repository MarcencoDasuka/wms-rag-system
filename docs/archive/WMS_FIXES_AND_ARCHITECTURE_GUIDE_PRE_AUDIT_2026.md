# WMS Core Architectural Reference Guide (ARCHIVED & SUPERSEDED)

> [!WARNING] ARCHIVED & SUPERSEDED (2026-10-04)
> **Historical Artifact Only:** This document contains historical hypotheses and claimed remediations (e.g., regarding DEF-01..DEF-05, BOLA, concurrency, and client `userId` data flow) that have been **refuted or proven incomplete** by the independent adversarial verification audit.
> 
> * **Active Source of Truth:** [`docs/WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](../WMS_ADVERSARIAL_VERIFICATION_REPORT.md)
> * **Active Remediation Roadmap:** [`docs/WMS_REMEDIATION_ROADMAP.md`](../WMS_REMEDIATION_ROADMAP.md)

> **Document Status:** Historical registry of **18** baseline architectural decisions in the WMS core, pre-audit outcomes, and superseded defect claims.  
> **Basis:** Analysis of the complete Git commit graph (`git log`), adversarial audit findings, and verification on an active PostgreSQL 16 catalog (**245 automated tests:** 215 Java 21 backend + 30 Node.js frontend).

---

# PART 1. BASELINE ARCHITECTURAL DECISIONS OF THE WMS CORE

---

### 1.1. [SEC-01] Rejection of Default JWT Secret in Production Profile
* **Commit:** `9bc4ea4`
* **Vulnerability Description:**
  In `JwtUtil`, when environment variable `JWT_SECRET` was absent, a hardcoded dev key (`default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`) was loaded. In a production deployment, an attacker could sign arbitrary JWT tokens with maximum privileges (`ROLE_DEV`, `ROLE_SUPERVISOR`) and achieve complete system compromise.
* **Location:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`.
* **Resolution:**
  Introduced `validateSecretConfiguration()` invoked in `@PostConstruct`. If the active profile is `prod` or `production` and the secret matches the default dev key, an `IllegalStateException` is thrown, aborting the Spring context before the network port opens.
* **Test:** `JwtUtilTest.java`.

---

### 1.2. [DATA-01] Idempotency and Atomicity of Operator Step Completion
* **Commit:** `5c6da66`
* **Vulnerability Description:**
  In `PickingOperatorStrategy.executeStep` and `ReplenishmentOperatorStrategy.executeStep`, a network failure or operator double-click could decrement stock twice. Terminal task state checks were missing.
* **Location:** `service/allocation/PickingOperatorStrategy.java`, `ReplenishmentOperatorStrategy.java`.
* **Resolution:**
  1. Added idempotency guard: repeated calls for an already completed allocation return the current state without duplicate stock mutations.
  2. All quantity adjustments and status transitions are combined into strict transactions with invariant validations.
* **Test:** `PickingFlowIntegrationTest.java`, `ReplenishmentFlowIntegrationTest.java`.

---

### 1.3. [DATA-02] Prevention of Negative Stock and Location Overflow
* **Commit:** `6c10e30`
* **Vulnerability Description:**
  During manual adjustments (`InventoryService`) or concurrent reservations, stock quantities could drop below zero and warehouse locations could exceed their configured capacity.
* **Location:** `service/InventoryService.java`, `entity/Stock.java`, `entity/Location.java`.
* **Resolution:**
  1. Enforced `quantity >= 0` and `reservedQuantity >= 0` checks at entity and service levels.
  2. In `LocationService`, added maximum capacity validation prior to product intake.
* **Test:** `InventoryServiceTest.java`.

---

### 1.4. [CONC-01] Pessimistic Locking in Task Dispatch
* **Commit:** `b30a1cd`
* **Vulnerability Description:**
  When multiple idle operators concurrently requested the next available task via `TaskService.getNextAvailableTask`, a race condition occurred: the same task was dispatched to two operators simultaneously.
* **Location:** `repository/TaskRepository.java`, `service/TaskService.java`.
* **Resolution:**
  Added repository method annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Task t WHERE t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
  List<Task> findAvailableTasksWithLock(Pageable pageable);
  ```
* **Test:** `TaskConcurrencyIntegrationTest.java`.

---

### 1.5. [CONC-02] Optimistic Locking Exception Handling and HTTP 409 Mapping
* **Commit:** `76ce83b`
* **Vulnerability Description:**
  Upon concurrent order modifications by multiple users, Hibernate threw `ObjectOptimisticLockingFailureException` or `OptimisticLockException`. Lacking an explicit handler in `GlobalExceptionHandler`, clients received `HTTP 500 Internal Server Error`, obscuring the business conflict nature.
* **Location:** `GlobalExceptionHandler.java`.
* **Resolution:**
  Added `@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})` returning standardized `HTTP 409 Conflict` response with advice to retry the operation.
* **Test:** `GlobalExceptionHandlerTest.java`.

---

### 1.6. [TEST-01] Updating Outdated Unit Tests and Signatures
* **Commits:** `839933b`, `621faa9`
* **Defect Description:**
  Following business logic evolution in allocation services and controller constructors, legacy tests failed to compile or broke against modified execution strategies (`PickingAllocationStrategy`, `ReplenishmentAllocationCompletionStrategy`).
* **Location:** Test classes `OrderServiceTest`, `ReplenishmentServiceTest`, `AllocationExecutionServiceTest`, `CategoryServiceTest`.
* **Resolution:**
  Refreshed mocks, updated constructor signatures, and aligned assertions with current business process behaviors.

---

### 1.7. [INFRA-01] WMS Stack Containerization via Docker Compose
* **Commit:** `d40ea94`
* **Objective:**
  Lacked an isolated environment for local deployment of the full WMS stack (backend, frontend, PostgreSQL, vector index).
* **Resolution:**
  Created `docker-compose.yaml` with services `postgres`, `wms-backend` (multi-stage OpenJDK 21 build), `wms-frontend` (Nginx + Vue 3), and network isolation.

---

### 1.8. [S-1] Denial of Authentication for Inactive Users (Account Deactivation Bypass)
* **Commit:** `b1169d5`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  During authentication via `/api/auth/login` (`AuthService`) and JWT verification in `JwtRequestFilter`, the active flag `user.getIsActive()` was not checked. A user deactivated or terminated by an administrator could still log in, obtain a valid JWT, and execute protected warehouse operations.
* **Location:** `service/AuthService.java`, `service/CustomUserDetailsService.java`, `security/JwtRequestFilter.java`.
* **Resolution:**
  1. In `CustomUserDetailsService.loadUserByUsername`, active status passed to Spring Security `User(..., enabled=user.getIsActive())`.
  2. In `AuthService.authenticate`, added strict account activity check throwing `AccountDeactivatedException`.
  3. In `GlobalExceptionHandler`, mapped `AccountDeactivatedException` to `HTTP 403 Forbidden`.
  4. In `JwtRequestFilter`, added per-request user activity verification.
* **Test:** `AuthServiceTest.java`, `CustomUserDetailsServiceTest.java`, `AuthControllerTest.java`.

---

### 1.9. [S-2] Prevention of Inactive Supervisor Self-Reactivation via `/register`
* **Commit:** `2e1f0e9`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  In `/api/auth/register` (`UserService.registerUser`), when receiving a request with the username or email of an existing user, the service overwrote their password and unconditionally set `userToSave.setIsActive(true)`. This allowed any blocked or terminated employee (including supervisors) to reactivate their account without administrator consent.
* **Location:** `service/UserService.java`.
* **Resolution:**
  1. In `UserService.registerUser`, added active status verification for existing accounts: if the user is deactivated (`!existingUser.getIsActive()`), re-registration attempts are rejected with `AccessDeniedException` ("Cannot reactivate a deactivated user through registration. Contact an administrator.").
  2. Added caller context check: creating or updating accounts with `ROLE_SUPERVISOR` is restricted to active supervisors.
  3. New users are created with `isActive = false` pending email token verification.
* **Test:** `UserServiceReactivationSecurityTest.java`.

---

### 1.10. [S-3] Eradication of Hardcoded Secrets and SHA-256 Fingerprint Validation
* **Commits:** `79b229b`, `5b445a3`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  Default passwords and secrets remained hardcoded in the repository (`docker-compose.yaml`, `EmailService.java`). Prior validation in `JwtUtil` (`9bc4ea4`) compared secrets against the plaintext string `default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`, preserving the compromised key in compiled bytecode and creating exposure upon decompilation.
* **Location:** `security/JwtUtil.java`, `service/EmailService.java`, `docker-compose.yaml`.
* **Resolution:**
  1. Removed hardcoded passwords from `EmailService.java` and `docker-compose.yaml`.
  2. In `JwtUtil`, replaced plaintext check with cryptographic SHA-256 fingerprint matching (`b428d00346a0661266e7b57fa0d238ecfef5f0bc59a68b9264c39b7d87bc7d96`).
  3. Under `prod` or `production` profiles, presence of the compromised key aborts application startup with `IllegalStateException`.
* **Test:** `JwtUtilTest.java`.

---

### 1.11. [B-1] Protection of Active Orders from Scheduled DB Cleanup
* **Commit:** `d61e887`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  Background task `DataCleanupJob` purged aged entities, deleting orders (`orders`), order lines (`order_lines`), replenishments (`replenishments`), and tasks (`tasks`) older than a cutoff threshold (`cutoffDate`) without filtering by status. This caused physical deletion of active orders in `CREATED` and `IN_PROGRESS` statuses, permanently losing reserved inventory state.
* **Location:** `OrderRepository.java`, `OrderLineRepository.java`, `ReplenishmentRepository.java`, `TaskRepository.java`, `DataCleanupJob.java`.
* **Resolution:**
  Repository purge queries are strictly restricted to terminal statuses:
  - Orders: `status IN ('COMPLETED', 'CANCELED')`.
  - Order lines: `status IN ('COMPLETED', 'CANCELED')`.
  - Replenishments: `status IN ('COMPLETED', 'CANCELED')`.
  - Tasks: `status IN ('COMPLETED', 'CANCELED')`.
* **Test:** `DataCleanupProtectionIntegrationTest.java`.

---

### 1.12. [D-1] Elimination of Destructive Flyway Migrations (`TRUNCATE TABLE ... CASCADE`)
* **Commit:** `ede81dc`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  In Flyway versioned migrations `V19__populate_isd_database.sql` and `V31__seed_warehouse_data_final.sql`, the opening line executed:
  ```sql
  TRUNCATE TABLE products, locations, orders, replenishments, stocks RESTART IDENTITY CASCADE;
  ```
  Because these scripts were part of the primary migration chain, running them against a production database permanently deleted all existing warehouse data.
* **Location:** `db/migration/V19__populate_isd_database.sql`, `V31__seed_warehouse_data_final.sql`.
* **Resolution:**
  1. Completely removed `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` statements from both migrations.
  2. Converted all data seeding inserts to idempotent PostgreSQL syntax `INSERT INTO ... ON CONFLICT DO NOTHING`.
* **Test:** Verified execution of Flyway migration chain on fresh and populated databases without data loss.

---

### 1.13. [B-2] Prevention of Lost Update During Concurrent Order Picking (`OrderLine`)
* **Commit:** `b131be1`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  When multiple operators concurrently picked items across different allocations for the same `OrderLine`, `PickingOperatorStrategy.executeStep` read the order line without locking, computed `deliveredQuantity = currentDelivered + pickedQuantity`, and saved the entity. Concurrent executions produced a classic Lost Update where one update silently overwrote another.
* **Location:** `service/allocation/PickingOperatorStrategy.java`, `repository/OrderLineRepository.java`.
* **Resolution:**
  Enforced serialization via pessimistic write lock:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ol FROM OrderLine ol WHERE ol.task.id = :taskId")
  Optional<OrderLine> findByTaskIdWithLock(@Param("taskId") Long taskId);
  ```
* **Test:** `OrderLinePickingConcurrencyIntegrationTest.java`.

---

### 1.14. [B-3] Enforcement of Warehouse Location Exclusivity Under Concurrent Placement
* **Commit:** `da8d654`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  A core WMS invariant requires location exclusivity: a warehouse cell cannot concurrently store stock of different products. Constraint `uk_stocks_product_location` only guarded against duplicates of the same product. When two operators concurrently placed product `A` and product `B` into the same empty location, both threads observed no stock (`stocks.isEmpty() == true`) and inserted records, causing two distinct products to occupy a single location.
* **Location:** `service/InventoryService.java`, `service/allocation/ReplenishmentOperatorStrategy.java`, `repository/LocationRepository.java`.
* **Resolution:**
  1. Created Flyway migration `V34__add_unique_constraint_stock_active_location.sql` with partial unique index:
     ```sql
     CREATE UNIQUE INDEX IF NOT EXISTS uk_stocks_active_location
         ON stocks (location_id)
         WHERE available = true;
     ```
  2. Added `findByIdWithLock(Long id)` with `LockModeType.PESSIMISTIC_WRITE` to `LocationRepository`.
  3. In `InventoryService.addStock` and `ReplenishmentOperatorStrategy`, acquired row lock on `Location` before evaluating availability.
* **Test:** `LocationProductExclusivityConcurrencyIntegrationTest.java`.

---

### 1.15. [B-4] Prevention of Race Condition Between Stock Write-off and Allocation Picking
* **Commit:** `edb5a9b`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  A real-time race condition existed: while a supervisor wrote off damaged inventory and canceled allocations via `InventoryAdjustmentApplier`, an operator concurrently completed picking via `AllocationExecutionService.completeAllocation`. In parallel execution, the operator could confirm picking on an already canceled allocation, or `Stock.reservedQuantity` was decremented twice into negative values.
* **Location:** `service/AllocationExecutionService.java`, `service/InventoryAdjustmentPlanner.java`, `service/InventoryAdjustmentApplier.java`, `repository/AllocationRepository.java`.
* **Resolution:**
  1. Added locking methods `findByIdWithLock` and `findActiveByStockIdWithLock` to `AllocationRepository`.
  2. In `AllocationExecutionService.completeAllocation`, allocation is loaded via `findByIdWithLock` and checked for terminal status (`if (allocation.getStatus() == Status.CANCELED) throw new InvalidRequestException(...)`).
  3. In `InventoryAdjustmentPlanner` and `InventoryAdjustmentApplier`, pessimistic locks are acquired across all active allocations of the stock prior to cancellation.
* **Test:** `AllocationAdjustmentConcurrencyIntegrationTest.java`.

---

### 1.16. [AI-1] Authorization Boundary and Two-Phase Confirmation for Mutating AI Tools
* **Commit:** `6ceb759`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  AI assistant tools (`ChatbotService`) permitted mutating warehouse state (order cancellation, stock relocation) without privilege checks and without operator confirmation. A prompt injection or LLM hallucination could trigger uncontrolled modification or deletion of critical warehouse resources.
* **Location:** `service/ChatbotService.java`, `service/ai/OrderAiTools.java`, `service/ai/InventoryAiTools.java`.
* **Resolution:**
  1. Mutating methods isolated into dedicated classes (`OrderMutatingAiTools`, `InventoryMutatingAiTools`), keeping safe tools read-only.
  2. Created security boundary component `AiToolSecurityBoundary` enforcing `ROLE_SUPERVISOR` or `ROLE_DEV`.
  3. Implemented two-phase confirmation protocol with cryptographic token: mutation calls generate `confirmation_token`, and only a subsequent call with this token executes the DB changes.
* **Test:** `AiToolSecurityBoundaryTest.java`, `InventoryAiToolsSecurityTest.java`, `OrderAiToolsSecurityTest.java`.

---

### 1.17. [AI-2] Object-Level Access Control (BOLA / IDOR) and Domain Zone Validation in AI Tools
* **Commit:** `75cc3fa`
* **Status:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **Vulnerability Description:**
  Checking coarse `ROLE_SUPERVISOR` in AI tools failed to prevent BOLA/IDOR attacks: a supervisor could cancel another supervisor's order or replenishment, delegate tasks to non-operator users, or move goods into incompatible warehouse zones (e.g. `DISPATCH`).
* **Location:** `service/ai/AiToolSecurityBoundary.java`, `service/ai/OrderMutatingAiTools.java`, `service/ai/InventoryMutatingAiTools.java`, `service/ai/ReplenishmentAiTools.java`, `repository/OrderRepository.java`.
* **Resolution:**
  1. Added query `findSupervisorUsernamesByOrder(Long orderId)` in `OrderRepository`.
  2. Implemented object ownership enforcement in `AiToolSecurityBoundary`: `enforceOrderAccess(orderId)` and `enforceReplenishmentAccess(replenishmentId)`.
  3. Added `enforceTargetOperator(username)` validating existence, active status, and `ROLE_OPERATOR` role.
  4. Added zone validation `validateDestinationZone(Location loc, Zone expectedZone)`.
* **Test:** `AiToolObjectLevelAuthorizationTest.java`.

---

# PART 2. WMS AUDIT TRACEABILITY MATRIX

| Code | Defect / Invariant | Domain | Verification Status | Audit Evidence / Notes |
|:-----|:-------------------|:-------|:--------------------|:-----------------------|
| **S-1** | Inactive users authentication bypass | Security | `[AWAITING VERIFICATION]` | `b1169d5` / `AuthServiceTest` |
| **S-2** | Inactive supervisor self-reactivation via `/register` | Security | `[AWAITING VERIFICATION]` | `2e1f0e9` / `UserServiceReactivationSecurityTest` |
| **S-3** | Hardcoded credentials and insecure secret fallbacks | Security | `[AWAITING VERIFICATION]` | `79b229b`, `5b445a3` / `JwtUtilTest` |
| **S-4** | Overly broad CORS trust boundary | Security | `[PARTIALLY VERIFIED]` | `fe04b2f` / Wildcards eliminated; port 80 present in default origins without profile gating |
| **S-5** | JS-readable access-token storage in `localStorage` | Security | `[PARTIALLY VERIFIED]` | `83a6047` / HttpOnly cookie active; residual risk: token returned in JSON body and sent via Bearer header; `user_id` desync resolved in DEF-02 |
| **B-1** | Active orders destroyed by scheduled DB cleanup | Data Integrity | `[AWAITING VERIFICATION]` | `d61e887` / `DataCleanupProtectionIntegrationTest` |
| **B-2** | Lost update during order picking (`OrderLine`) | Concurrency | `[AWAITING VERIFICATION]` | `b131be1` / `OrderLinePickingConcurrencyIntegrationTest` |
| **B-3** | Cell/location monopoly race | Concurrency | `[AWAITING VERIFICATION]` | `da8d654` / `LocationProductExclusivityConcurrencyIntegrationTest` |
| **B-4** | Allocation vs inventory adjustment race | Concurrency | `[AWAITING VERIFICATION]` | `edb5a9b` / `AllocationAdjustmentConcurrencyIntegrationTest` |
| **B-5** | Concurrent stock reservation / allocation integrity | Concurrency | `[VERIFIED]` | `dce5b8c` / Pessimistic locking + canonical order + DB CHECK |
| **D-1** | Destructive Flyway migrations (`TRUNCATE TABLE`) | DB Integrity | `[AWAITING VERIFICATION]` | `ede81dc` / Idempotent migrations V19, V31 |
| **D-2** | Stock/Location mapping integrity | DB Integrity | `[VERIFIED]` | `53a8803` / Partial index `uk_stocks_active_location` + location deletion guard |
| **D-3** | `logic_id` uniqueness and integrity | DB Integrity | `[PARTIALLY VERIFIED]` | `f44b0af` / Indexes active; DEF-03 (case mismatch) and GAP-01 (409 mapping) resolved in baseline; awaiting end-to-end audit |
| **D-4** | Missing FK indexes across warehouse tables | Performance | `[VERIFIED]` | `eb1b6b5` / 100% coverage (20/20 foreign keys supported by B-Tree indexes) |
| **D-5** | N+1 query problem | Performance | `[VERIFIED]` | `5f0f112`, GAP-03 / Queries for orders, replenishments, stock, and history optimized via `@EntityGraph` (1–2 queries) |
| **F-1** | Centralized 401/403/409 interceptors in frontend | Frontend UX | `[PARTIALLY VERIFIED]` | `aed97d3` / Interceptors active; DEF-01, DEF-04, DEF-05 resolved in baseline; awaiting end-to-end audit |
| **DEF-01** | False session expiry on bad login credentials | Frontend / Auth | `[VERIFIED]` | `interceptors.js` / Predicate `isAuthLoginRequest` excludes login 401 from session purge, 4 tests in `def01_login_401_interceptor.test.js` |
| **DEF-02** | User ID storage desync & fallback to mock IDs | Frontend / Data | `[VERIFIED]` | `useCurrentUserId.js`, `OrderWithLinesForm.vue`, `InventoryView.vue`, `auth.js` / Legacy localStorage and mock IDs removed, fail-closed validation, 8 tests in `def02_user_id_dataflow.test.js` |
| **DEF-03** | Index case mismatch (`lower` vs `upper`) | Backend / DB | `[VERIFIED]` | `OrderRepository.java`, `ReplenishmentRepository.java` / Explicit JPQL `LOWER(logicId) = LOWER(:logicId)` for findBy and existsBy, verified via `LogicIdUniquenessIntegrationTest` |
| **DEF-04** | Unvalidated open redirect in LoginView | Frontend / Sec | `[VERIFIED]` | `redirectSanitizer.js`, `LoginView.vue` / Internal path canonicalization and validation, stripping `//`, scheme, backslash, and encoded paths, 7 tests in `def04_open_redirect.test.js` |
| **DEF-05** | Dead code `wms:conflict` event dispatch | Frontend / Arch | `[VERIFIED]` | `useConflictListener.js`, `InventoryView.vue`, `OrderView.vue` / Subscription to CustomEvent `wms:conflict` with auto-reload and lifecycle cleanup, 4 tests in `def05_conflict_event.test.js` |
| **GAP-01** | Map DataIntegrityViolationException (`logic_id`) to HTTP 409 | Backend / API | `[VERIFIED]` | `GlobalExceptionHandler.java` / Maps `logic_id` unique constraint collisions to HTTP 409 Conflict instead of 500, 4 tests in `GlobalExceptionHandlerTest` |
| **GAP-02** | Require secure JWT cookies in production profile | Security | `[VERIFIED]` | `JwtUtil.java`, `AuthController.java` / Fail-fast enforcement of `wms.jwt.cookie-secure=true` under `prod`/`production` profiles, 16 tests in `JwtUtilTest` and `AuthControllerTest` |
| **GAP-03** | Missing EntityGraphs in Inventory & History queries | Backend / DB | `[VERIFIED]` | `StockRepository.java`, `InventoryHistoryRepository.java` / `@EntityGraph` on stock (`product`, `location`) and history (`product`, `sourceLocation`, `destinationLocation`, `user`), verified 1 SQL query in `NPlusOneQueryPerformanceIntegrationTest` |
| **GAP-04** | Consistent pessimistic resource lock ordering (Stock Lock Ordering) | Concurrency / DB | `[VERIFIED]` | `StockRepository.java`, `InventoryService.java`, `InventoryAdjustmentApplier.java` / Added `findByIdWithLock` and `findAllByIdInWithLock` with canonical `ORDER BY s.id ASC`, eliminating deadlocks during concurrent write-offs and reallocations; 7 tests in `StockLockOrderingConcurrencyIntegrationTest`, `StockReservationConcurrencyIntegrationTest`, `AllocationAdjustmentConcurrencyIntegrationTest` |

---

# PART 3. DETAILED AUDIT DEFECT REGISTRY AND VERIFICATION (S-4 – F-1)

---

### 3.1. [S-4] Overly broad CORS trust boundary (`allowedOriginPatterns("*")`)
* **Audit Status:** `[PARTIALLY VERIFIED]`
* **Severity:** High
* **Domain:** Security / Network Boundary
* **Location:** `config/WebConfig.java`, `security/SecurityConfig.java`, `application.properties`.
* **Problem Statement:**
  Previously, the wildcard pattern `allowedOriginPatterns("*")` was used in combination with `allowCredentials(true)`. Modern browsers reject this combination, but under specific conditions the configuration allowed trusting any origin on the local network.
* **Implementation (`fe04b2f`):**
  Completely removed `allowedOriginPatterns("*")`. Introduced strict origin whitelist via `allowedOrigins` in `WebConfig.java` and `SecurityConfig.java`. Allowed origins are parameterized via `wms.cors.allowed-origins` and `wms.frontend.url`. Enabled Preflight OPTIONS handling and `Vary: Origin` header.
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Zero occurrences of `allowedOriginPatterns` or uncontrolled `@CrossOrigin` remain in backend codebase. Verified strict validation of headers, methods (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`), and `maxAge(3600)`.
  - `[RESIDUAL RISK]`: By default in `application.properties`, allowed origins include `http://localhost:80`, `http://127.0.0.1:80`, `http://localhost`. In production without explicit override via `WMS_CORS_ALLOWED_ORIGINS`, any local web server on port 80 receives credentialed access (`allowCredentials(true)`). Profile-specific isolation (`prod` vs `dev`) is required.

---

### 3.2. [S-5] JS-readable access-token storage in `localStorage`
* **Audit Status:** `[PARTIALLY VERIFIED]`
* **Severity:** Medium
* **Domain:** Security / Web Session
* **Location:** `controller/AuthController.java`, `security/JwtRequestFilter.java`, `wmsFront/src/stores/auth.js`, `src/api/authApi.js`.
* **Problem Statement:**
  JWT token was stored in `localStorage`, exposing it to immediate theft via any XSS vulnerability in frontend dependencies (PrimeVue, Chart.js, Marked).
* **Implementation (`83a6047`):**
  Server switched to issuing `HttpOnly` cookie (`wms_token`) with `SameSite=Lax` and security flag `wms.jwt.cookie-secure`. Frontend token persistence in `localStorage` was removed. Implemented `/api/auth/logout` endpoint invalidating the cookie on the server.
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Cookie `wms_token` is generated via `ResponseCookie`, configured with `HttpOnly`, `Path=/api`, `SameSite=Lax`. Filter `JwtRequestFilter` successfully extracts token from cookie on requests with `withCredentials: true`.
  - `[RESIDUAL RISK]`: Endpoint `AuthController.login` continues returning raw JWT in JSON response body (`Map.of("token", token)`). Pinia store `auth.js` retains this token in memory and transmits `Authorization: Bearer <token>` on every request. This reduces `HttpOnly` efficacy as the token remains readable in application memory via JS.
  - `[DEFECT]`: Critical user data reading desync identified in frontend (detailed in `DEF-02`).

---

### 3.3. [B-5] Concurrent stock reservation / allocation integrity
* **Audit Status:** `[VERIFIED]`
* **Severity:** High
* **Domain:** Concurrency / Transactional Correctness
* **Location:** `service/OrderService.java`, `repository/StockRepository.java`, `V32__add_stocks_check_constraint.sql`.
* **Problem Statement:**
  When two or more orders were concurrently assigned to the same scarce stock, both threads read available stock (`quantity - reservedQuantity`), passed validation, and simultaneously increased `reservedQuantity`. This led to over-reservation where reserved stock exceeded physical warehouse inventory.
* **Implementation (`dce5b8c`):**
  1. Added query with pessimistic write lock in `StockRepository`:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT s FROM Stock s WHERE s.product.id = :productId AND s.available = true ORDER BY s.id ASC")
     List<Stock> findAvailableStocksForProductWithLock(@Param("productId") Long productId);
     ```
  2. In `OrderService.assignOrder`, introduced canonical sorting of order lines by `productId`, ensuring uniform lock acquisition order across transactions and eliminating database deadlocks.
  3. In migration `V32`, added PostgreSQL integrity constraint: `CHECK (quantity_reserved <= quantity)`.
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: `@Transactional` boundary in `OrderService` encapsulates the reservation chain. Pessimistic `FOR UPDATE` lock serializes concurrent threads at the PostgreSQL level. Database catalog inspection confirmed active status of `stocks_check` constraint. Multithreaded integration test `StockReservationConcurrencyIntegrationTest` confirms clean rollback when stock is insufficient.

---

### 3.4. [D-2] Stock/Location mapping integrity
* **Audit Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Database Integrity / Lifecycle
* **Location:** `entity/Stock.java`, `entity/Location.java`, `service/LocationService.java`, `V34__add_unique_constraint_stock_active_location.sql`.
* **Problem Statement:**
  The relationship between stock and location was vulnerable: warehouse locations could be deactivated or deleted even while containing active goods, compromising inventory accounting. Absence of uniqueness constraints permitted parallel placement collisions.
* **Implementation (`53a8803`):**
  1. Created migration `V34` adding partial unique index `uk_stocks_active_location` on `stocks (location_id) WHERE available = true`.
  2. In `LocationService`, implemented preemptive lifecycle guards: deletion or deactivation of a location is blocked if active stock exists (`quantity > 0`) or pending warehouse tasks are assigned (`TaskStatus.PENDING`, `IN_PROGRESS`).
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Existence of partial index `uk_stocks_active_location` confirmed in PostgreSQL catalog `pg_indexes`. Service validations ensure occupied locations cannot be deleted, and partial index prevents more than one active stock entry per warehouse cell.

---

### 3.5. [D-3] `logic_id` uniqueness and integrity
* **Audit Status:** `[PARTIALLY VERIFIED]`
* **Severity:** Medium
* **Domain:** Database Integrity
* **Location:** `entity/Order.java`, `entity/Replenishment.java`, `V35__enforce_logic_id_uniqueness.sql`, `OrderRepository.java`, `ReplenishmentRepository.java`.
* **Problem Statement:**
  Field `logic_id` (business invoice identifier, e.g. `ORD-2026-001`) lacked `NOT NULL` constraint and unique index in the database. Uniqueness was validated only at application level in Java, leading to race conditions during concurrent inserts via API or CSV import.
* **Implementation (`f44b0af`):**
  Created migration `V35` enforcing `NOT NULL` on `logic_id` columns in `orders` and `replenishments`, and adding functional lower-case unique indexes:
  ```sql
  CREATE UNIQUE INDEX uk_orders_logic_id_lower ON orders (LOWER(logic_id));
  CREATE UNIQUE INDEX uk_replenishments_logic_id_lower ON replenishments (LOWER(logic_id));
  ```
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Indexes `uk_orders_logic_id_lower` and `uk_replenishments_logic_id_lower` are active in PostgreSQL. Duplicate insertion attempts across any case variations are reliably rejected by the database.
  - `[RESIDUAL RISK]`: Concurrent collisions at database level throw `DataIntegrityViolationException`, returning `HTTP 500 Internal Server Error` instead of semantic `HTTP 409 Conflict`.
  - `[DEFECT]`: Fundamental case mismatch detected between index expressions and Hibernate query generation (detailed in `DEF-03`).

---

### 3.6. [D-4] Missing foreign-key indexes across warehouse tables
* **Audit Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Database Performance
* **Location:** `db/migration/V36__add_missing_foreign_key_indexes.sql`, PostgreSQL catalog `pg_index`.
* **Problem Statement:**
  In PostgreSQL, defining a `FOREIGN KEY` does not automatically create an index on the child table. During cascading checks and JOIN queries, the DBMS performed full sequential scans (Sequential Scan), degrading query throughput and holding table/row locks.
* **Implementation (`eb1b6b5`):**
  Created migration `V36` adding B-Tree indexes across all foreign keys in warehouse tables (`idx_fk_allocations_stock`, `idx_fk_orders_destination`, `idx_fk_stocks_location`, etc.).
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Complete review of PostgreSQL system catalog (`pg_constraint` joined with `pg_index`) confirmed that **100% of foreign keys (20 of 20)** have a covering B-Tree index as the leading column. Execution plans (`EXPLAIN`) confirm transition from Sequential Scan to Index Scan.

---

### 3.7. [D-5] N+1 query problem & batch fetching
* **Audit Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Performance / ORM
* **Location:** `OrderService.java`, `ReplenishmentService.java`, `InventoryService.java`, `repository/OrderRepository.java`, `repository/StockRepository.java`, `repository/InventoryHistoryRepository.java`, `application.properties`.
* **Problem Statement:**
  Lazy loading (`FetchType.LAZY`) of `@ManyToOne` and `@OneToMany` relationships during bulk fetches of orders, tasks, stock, and history caused cascading SQL queries per row (up to 304 queries across 82 orders), exhausting the DB connection pool.
* **Implementation (`5f0f112`, GAP-03):**
  Configured global batch fetching `spring.jpa.properties.hibernate.default_batch_fetch_size=50`. Added explicit `@EntityGraph` annotations on `OrderRepository`, `ReplenishmentRepository`, `StockRepository`, and `InventoryHistoryRepository` queries. Added integration test `NPlusOneQueryPerformanceIntegrationTest`.
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Significant query reduction recorded across key read flows: extended orders reduced from 304 to 9 queries, replenishments from 63 to 2 queries, stock retrieval `getAllStock` (154 records) executed in exactly 1 SQL query, inventory history `getAllHistory` (178 records) executed in exactly 1 SQL query. All performance tests in `NPlusOneQueryPerformanceIntegrationTest` passed (`5/5 passed`).

---

### 3.8. [F-1] Centralized Axios interceptors for 401, 403, and 409
* **Audit Status:** `[PARTIALLY VERIFIED]`
* **Severity:** Medium
* **Domain:** Frontend Architecture & UX
* **Location:** `wmsFront/src/api/interceptors.js`, `notificationService.js`, `stores/auth.js`, `views/auth/LoginView.vue`.
* **Problem Statement:**
  Lacked centralized HTTP error handling: on `403 Forbidden` users were erroneously logged out losing unsaved work, and concurrency collisions `409 Conflict` did not signal the UI to refresh stale data.
* **Implementation (`aed97d3`):**
  Developed `notificationService.js` with message debouncing (1500 ms). Implemented modular interceptors in `api/interceptors.js`: 401 invokes `authStore.logout()` and redirects to `/login?sessionExpired=true`; 403 preserves session and displays Toast "Access Denied"; 409 preserves session, displays warning "Conflict Detected", and dispatches `wms:conflict` event. Added unit test suite `interceptors.test.js` (7 tests).
* **Adversarial Audit Results & Boundary Guarantees:**
  - `[VERIFIED]`: Protection against false session purge on 403 verified by unit tests. Notification debouncing prevents alert flooding. All 7 tests in `interceptors.test.js` pass.
  - `[RESIDUAL RISK]`: Identified 3 critical implementation defects (false session drop on login failure `DEF-01`, unvalidated redirect parameter `DEF-04`, dead code `wms:conflict` event `DEF-05`), detailed in Part 4.

---

# PART 4. REMEDIATION DEFECT AND ARCHITECTURAL GAP REGISTRY (DEF-01 – DEF-05, GAP-01 – GAP-04)

---

### 4.1. [DEF-01] False session expiry on bad login credentials in login form
* **Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Frontend UX / Authentication
* **Affected Components:** `inbound-storage-dispatch/wmsFront/src/api/interceptors.js` (lines 16–50, 125–135), `src/views/auth/LoginView.vue`.
* **Problem Statement:**
  The global Axios response interceptor intercepted **any** `401 Unauthorized` status without inspecting the request URL. When an unauthenticated user entered invalid credentials on the login page (`POST /api/auth/login`), the backend returned `401 Unauthorized`. The interceptor triggered `authStore.logout()`, flashed "Session expired. Please log in again", and redirected to `/login?sessionExpired=true`, overwriting the login form's own authentication error message.
* **Resolution:**
  1. Implemented predicate `isAuthLoginRequest(config)` reliably identifying login authentication requests (`POST /auth/login`, `POST /api/auth/login`, absolute URLs).
  2. In `handle401Unauthorized` and `setupInterceptors`, added guard: when `isAuthLoginRequest(...) === true`, the handler returns immediately without invoking `logout()`, without showing a false "Session Expired" Toast, and without navigating.
  3. The `401` error propagates cleanly to `LoginView.vue`, which displays user-friendly feedback ("Incorrect username or password").
  4. For protected endpoints (`/orders`, `/inventory`, `/auth/me`, etc.), the standard session invalidation mechanism remains fully active.
* **Verification:**
  Authored regression test suite `test/def01_login_401_interceptor.test.js` (4 tests):
  - Verified request identification via `isAuthLoginRequest` (POST method, paths, query params, case sensitivity, no false triggers on `/auth/me`, `/auth/logout`, `/auth/verify`).
  - Verified no invocation of `authStore.logout()`, notifications, or navigation on 401 against `/auth/login`.
  - Verified proper session purge on 401 against protected endpoints (`/v1/orders/extended`, `/inventory`, `/auth/me`, `/inventory/add`).
  - Verified Axios interceptor wiring via `setupInterceptors`.
  - All tests passed.

---

### 4.2. [DEF-02] User ID storage desync & fallback to mock IDs
* **Status:** `[VERIFIED]`
* **Severity:** High
* **Domain:** Frontend Data Integrity / Audit Trail
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsFront/src/composables/useCurrentUserId.js`
  - `inbound-storage-dispatch/wmsFront/src/components/OrderWithLinesForm.vue`
  - `inbound-storage-dispatch/wmsFront/src/views/supervisor/InventoryView.vue`
  - `inbound-storage-dispatch/wmsFront/src/stores/auth.js`
* **Problem Statement:**
  Under S-5 (`83a6047`), user data and JWT were migrated from `localStorage` into Pinia store state (`sessionStorage`). However, views `OrderWithLinesForm.vue` and `InventoryView.vue` retained legacy synchronous reads `localStorage.getItem('user_id')` with fallbacks to mock IDs 1, 2, 3 when absent from `localStorage`.
* **Resolution:**
  1. Created composable `useCurrentUserId.js` (`resolveCurrentUserId`), extracting a valid integer ID strictly from `authStore.user.id`.
  2. In `InventoryView.vue` and `OrderWithLinesForm.vue`, eliminated all references to `localStorage.getItem('user_id')` and fallback IDs (`|| 1`, `|| 2`, `|| 3`).
  3. Enforced fail-closed validation: if no authenticated user ID is available, execution aborts with a Toast error without dispatching the HTTP request.
  4. In `auth.js`, removed mock users (`seededUsers`) with hardcoded IDs. Login performs an authoritative fetch to `/api/auth/me` to obtain the actual database user ID.
* **Verification:**
  Authored adversarial test suite `test/def02_user_id_dataflow.test.js` (8 tests):
  - Verified end-to-end propagation of `userId = X` (42) and `userId = Y` (99) into adjustment and stock intake payloads.
  - Verified request blocking when `user` is missing or `user.id` is invalid.
  - Verified complete disregard of `localStorage.user_id`.
  - Static analysis confirmed zero occurrences of `localStorage.getItem('user_id')` or mock fallback IDs.
  - All 15 frontend tests (`interceptors.test.js` + `def02_user_id_dataflow.test.js`) passed.

---

### 4.3. [DEF-03] Index case mismatch (`lower` vs `upper`)
* **Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Backend Performance / Database Optimization
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V35__enforce_logic_id_uniqueness.sql`  
  - `com/isd/wms/repository/OrderRepository.java`  
  - `com/isd/wms/repository/ReplenishmentRepository.java`
* **Problem Statement:**
  Migration `V35` created unique functional indexes using **lower-case** expressions:
  ```sql
  CREATE UNIQUE INDEX uk_orders_logic_id_lower ON orders (LOWER(logic_id));
  CREATE UNIQUE INDEX uk_replenishments_logic_id_lower ON replenishments (LOWER(logic_id));
  ```
  Meanwhile, Spring Data JPA repositories declared derived methods `findByLogicIdIgnoreCase(String logicId)` and `existsByLogicIdIgnoreCase(String logicId)`. Per Hibernate and Spring Data JPA specifications, the `IgnoreCase` keyword generates SQL predicates using **upper-case**:
  ```sql
  WHERE UPPER(orders.logic_id) = UPPER(?)
  ```
  The PostgreSQL query optimizer could not match `UPPER(logic_id)` predicates against functional indexes built on `LOWER(logic_id)`.
* **Resolution:**
  1. In `OrderRepository`, replaced derived `findByLogicIdIgnoreCase` and `existsByLogicIdIgnoreCase` with explicit JPQL queries:
     ```java
     @Query("SELECT o FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
     Optional<Order> findByLogicIdIgnoreCase(@Param("logicId") String logicId);

     @Query("SELECT COUNT(o) > 0 FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
     boolean existsByLogicIdIgnoreCase(@Param("logicId") String logicId);
     ```
  2. In `ReplenishmentRepository`, identically replaced derived methods with explicit `LOWER()` JPQL queries.
  3. Generated SQL strictly emits predicates `lower(o.logic_id) = lower(?)`, aligning with functional indexes `uk_orders_logic_id_lower` and `uk_replenishments_logic_id_lower`.
* **Verification:**
  - PostgreSQL system catalog (`pg_indexes`) confirms index expressions on `lower((logic_id)::text)`.
  - Integration test `LogicIdUniquenessIntegrationTest` ran all 4 tests (DB uniqueness, service validation, case-insensitive lookup) successfully (`4/4 passed`).

---

### 4.4. [DEF-04] Unvalidated redirect parameter in authentication form (Open Redirect)
* **Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Web Security / Navigation Integrity
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsFront/src/utils/redirectSanitizer.js`
  - `inbound-storage-dispatch/wmsFront/src/views/auth/LoginView.vue` (lines 82, 118)
* **Problem Statement:**
  Upon successful login, `LoginView.vue` unconditionally navigated to the address supplied in `route.query.redirect`:
  ```javascript
  const redirect = route.query.redirect || '/'
  router.push(redirect)
  ```
  This created an Open Redirect vulnerability exploitable via phishing links with `//evil.com`, `https://evil.com`, `/\evil.com`, or encoded URLs (`%2f%2fevil.com`).
* **Resolution:**
  1. Developed `redirectSanitizer.js` (`sanitizeRedirect`), enforcing strict URL validation and canonicalization.
  2. Permits only safe relative internal paths (starting with a single `/`, no backslashes, no control characters, no external schemes `javascript:`, `http:`, `https:`).
  3. Performs iterative decoding to thwart encoding bypasses (`%2f`, `%5c`, `%252f`).
  4. Verifies path parsing via WHATWG URL parser asserting `origin === 'http://localhost'`.
  5. In `LoginView.vue`, wrapped `router.push` with `sanitizeRedirect(route.query.redirect, authStore.dashboardPath)`.
* **Verification:**
  Authored adversarial test suite `test/def04_open_redirect.test.js` (7 tests):
  - Verified valid navigation to legitimate paths (`/orders`, `/inventory`, `/foo?x=1`, `/supervisor/dashboard`).
  - Verified rejection of protocol-relative URLs (`//evil.example`, `///evil.example`).
  - Verified rejection of absolute schemes (`https://`, `http://`, `javascript:`, `data:`).
  - Verified rejection of backslash variants (`/\evil`, `\\evil`, `\orders`).
  - Verified rejection of encoded attack vectors (`%2f%2fevil`, `/%5cevil`, `%00/orders`).
  - Verified handling of `null`, `undefined`, numbers, arrays, and empty strings.
  - Static analysis confirmed mandatory sanitization call in `LoginView.vue`.
  - All tests passed.

---

### 4.5. [DEF-05] Dead code CustomEvent `wms:conflict` dispatch without listeners in frontend
* **Status:** `[VERIFIED]`
* **Severity:** Low
* **Domain:** Frontend Architecture
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsFront/src/api/interceptors.js`
  - `inbound-storage-dispatch/wmsFront/src/composables/useConflictListener.js`
  - `inbound-storage-dispatch/wmsFront/src/views/supervisor/InventoryView.vue`
  - `inbound-storage-dispatch/wmsFront/src/views/supervisor/OrderView.vue`
  - `inbound-storage-dispatch/wmsFront/test/def05_conflict_event.test.js`
* **Problem Statement:**
  Upon receiving `409 Conflict`, the Axios interceptor dispatched a custom event on the browser window:
  ```javascript
  window.dispatchEvent(new CustomEvent('wms:conflict', { detail: errorPayload }))
  ```
  However, no component subscribed to this event (`window.addEventListener('wms:conflict', ...)`). Order and inventory tables failed to refresh reactive state on data version conflicts, leaving users viewing stale data until a manual page refresh (F5).
* **Resolution:**
  1. Developed composable `useConflictListener.js` registering a subscription to `wms:conflict` on `window` with automatic cleanup in `onUnmounted` to prevent memory leaks.
  2. Implemented idempotent listener registration and callback exception isolation, safeguarding browser event loop execution.
  3. In `InventoryView.vue`, registered `useConflictListener(loadInventoryData)`, triggering automatic stock refresh upon collisions.
  4. In `OrderView.vue`, registered `useConflictListener(loadOrders)`, synchronizing order tables upon receiving 409 Conflict.
* **Verification:**
  Authored adversarial test suite `test/def05_conflict_event.test.js` (4 tests):
  - Verified `wms:conflict` event dispatch with error details and code 409 by `handle409Conflict`.
  - Verified reactive delivery to `useConflictListener` callback.
  - Verified callback error isolation.
  - Verified clean listener unsubscription upon lifecycle termination (`stopListening`/`onUnmounted`) and disregard of subsequent events.
  - Verified static integration of `useConflictListener` in `InventoryView.vue` and `OrderView.vue`.
  - All 30 frontend tests passed (`30/30 passed`).

---

### 4.6. [GAP-02] Fail-fast verification of `wms.jwt.cookie-secure` in production profile
* **Status:** `[VERIFIED]`
* **Severity:** High
* **Domain:** Web Security / Session Integrity
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/AuthController.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/JwtUtilTest.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/controller/AuthControllerTest.java`
* **Problem Statement:**
  By default, `wms.jwt.cookie-secure` is set to `false` for local HTTP development (`http://localhost:8080`). In production, issuing authentication cookies without the `Secure` flag exposes session tokens to interception (man-in-the-middle) across unencrypted channels.
* **Resolution:**
  1. In `JwtUtil`, injected parameter `@Value("${wms.jwt.cookie-secure:false}") boolean cookieSecure` and validated active Spring profiles (`Profiles.of("prod", "production")`).
  2. When profile is `prod`/`production` and `cookieSecure == false`, startup aborts with `IllegalStateException("Production startup aborted: wms.jwt.cookie-secure must be true in production profile. Set WMS_JWT_COOKIE_SECURE=true.")`.
  3. In `AuthController`, added identical startup validation during controller initialization.
  4. Dev/test profiles retain ability to operate with `cookieSecure == false`.
  5. `JwtUtilTest` migrated to `MockEnvironment` (POJO) to eliminate dynamic Java agent attachment overhead.
* **Verification:**
  - `JwtUtilTest`: verified exception thrown for `prod + cookieSecure=false`, successful startup for `prod + cookieSecure=true`, dev profile operation with `cookieSecure=false` (all 7 tests passed).
  - `AuthControllerTest`: verified validation for `prod + cookieSecure=false` and startup with `cookieSecure=true` (all 9 tests passed).
  - Total 16/16 tests passed in 9 seconds.

---

### 4.7. [GAP-01] Mapping DataIntegrityViolationException (`logic_id` collision) to HTTP 409 Conflict
* **Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Backend API / Error Handling Integrity
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/exception/GlobalExceptionHandler.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/exception/GlobalExceptionHandlerTest.java`
* **Problem Statement:**
  During concurrent insertions of orders or replenishments with duplicate `logic_id`, preemptive application lookups `findByLogicIdIgnoreCase` could return `false` in both threads, leading both transactions to issue `INSERT`. At the PostgreSQL level, unique functional indexes `uk_orders_logic_id_lower` / `uk_replenishments_logic_id_lower` correctly blocked the duplicate with error `23505 unique constraint violation`, but Spring Data surfaced an unhandled `DataIntegrityViolationException`, translating into `HTTP 500 Internal Server Error` instead of semantic `HTTP 409 Conflict`.
* **Resolution:**
  1. In `GlobalExceptionHandler`, registered handler `@ExceptionHandler(DataIntegrityViolationException.class)`.
  2. The handler inspects `mostSpecificCause` and identifies `logic_id` constraints (`uk_orders_logic_id_lower`, `uk_replenishments_logic_id_lower`, `logic_id`), producing `HTTP 409 Conflict` with structured body `ApiErrorResponse` ("A resource with the specified logic_id already exists.").
  3. Other unique constraint violations similarly map to `HTTP 409 Conflict` with constraint collision messages, eliminating unhandled 500 errors during concurrent insertions.
* **Verification:**
  - Unit tests in `GlobalExceptionHandlerTest`:
    - `handleDataIntegrityViolation_withLogicIdUniqueConstraint_returnsConflict`: verified 409 status and `logic_id` conflict message.
    - `handleDataIntegrityViolation_withGenericUniqueConstraint_returnsConflict`: verified 409 for generic unique constraint violations.
  - All tests in `GlobalExceptionHandlerTest` passed (`4/4 passed`).

---

### 4.8. [GAP-03] Optimization of N+1 Queries for Warehouse Stock and Inventory History
* **Status:** `[VERIFIED]`
* **Severity:** Medium
* **Domain:** Performance / Database Query Optimization
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/StockRepository.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/InventoryHistoryRepository.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/NPlusOneQueryPerformanceIntegrationTest.java`
* **Problem Statement:**
  Queries for stock (`InventoryService.getAllStock()`) and transaction history (`InventoryService.getAllHistory()`) mapped entities to DTOs (`StockMapper`, `InventoryHistoryMapper`), traversing lazy associations:
  - `Stock`: `product`, `location`
  - `InventoryHistory`: `product`, `sourceLocation`, `destinationLocation`, `user`
  `StockRepository` and `InventoryHistoryRepository` lacked `@EntityGraph` annotations, causing Hibernate to issue batched lazy loads in chunks of 50 ($O(N / 50)$ SQL queries), overburdening the database as warehouse records grew.
* **Resolution:**
  1. In `StockRepository`, annotated methods `findAll()`, `findAllByAvailableIsTrue()`, `findByLocationId(Long)`, and `findAllByLocationId(Long)` with `@EntityGraph(attributePaths = {"product", "location"})`.
  2. In `InventoryHistoryRepository`, annotated methods `findAll()` and `findByProductIdAndSourceLocationIdOrProductIdAndDestinationLocationId(...)` with `@EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})`.
  3. Because all affected associations are `@ManyToOne` (to-one), JPA fetches them eagerly via `LEFT OUTER JOIN` in a single SQL query without row multiplication or cartesian product.
* **Verification:**
  - In `NPlusOneQueryPerformanceIntegrationTest`, added query counter tests using Hibernate statistics (`Statistics.getPrepareStatementCount()`):
    - `measureGetAllStockQueries`: retrieval of 154 active stock records executed in exactly **1 SQL query** (`queries <= 2`).
    - `measureGetAllHistoryQueries`: retrieval of 178 inventory history records executed in exactly **1 SQL query** (`queries <= 2`).
  - All 5 performance integration tests passed (`5/5 passed`, `BUILD SUCCESS`).

---

### 4.9. [GAP-04] Consistent Pessimistic Resource Lock Ordering (Stock Lock Ordering)
* **Status:** `[VERIFIED]`
* **Severity:** High
* **Domain:** Concurrency / Database Lock Ordering Integrity
* **Affected Components:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/StockRepository.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/InventoryService.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/inventoryadjustment/InventoryAdjustmentApplier.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/allocation/StockLockOrderingConcurrencyIntegrationTest.java`
* **Problem Statement:**
  An architectural asymmetry existed in pessimistic stock synchronization mechanisms:
  1. `WorkflowService` and `ShortageResolver` locked `Stock` rows in canonical ascending ID order (`ORDER BY s.id ASC`) via `findAvailableStocksByProductIdAndZoneForUpdate`.
  2. In `InventoryService.removeStock()`, stock was fetched via standard `stockRepository.findById(...)` without acquiring `PESSIMISTIC_WRITE`, creating race condition risks (lost updates and overselling) during concurrent write-offs or order allocations.
  3. In `InventoryAdjustmentApplier.applyAdjustmentPlan()`, alternative stock for reallocation was loaded via `stockRepository.findAllById(stockIds)` without pessimistic locks and without deterministic ordering. Concurrently executing inventory adjustments and order allocations touching overlapping locations/stocks created a direct cyclic deadlock risk (Transaction A holding stock 10 requesting 5, while Transaction B holding stock 5 requesting 10).
* **Resolution:**
  1. Added pessimistic locking methods in `StockRepository`:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT s FROM Stock s WHERE s.id = :id")
     Optional<Stock> findByIdWithLock(@Param("id") Long id);

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("""
         SELECT s FROM Stock s
         WHERE s.id IN :ids
         ORDER BY s.id ASC
         """)
     List<Stock> findAllByIdInWithLock(@Param("ids") Collection<Long> ids);
     ```
  2. In `InventoryService.removeStock()`, stock loading switched to `stockRepository.findByIdWithLock(...)`, ensuring strict serialization of concurrent write-offs and preventing lost updates.
  3. In `InventoryAdjustmentApplier.applyAdjustmentPlan()`, aggregated all affected stock IDs (target stock plus alternative reallocation stocks):
     ```java
     List<Long> allStockIdsToLock = Stream.concat(
             Stream.of(context.stockId()),
             alternativeStockIds.stream()
         )
         .distinct()
         .sorted()
         .toList();
     ```
     All stocks are locked in a single query `stockRepository.findAllByIdInWithLock(allStockIdsToLock)` strictly in ascending order `s.id ASC`. This satisfies Dijkstra/Havender linear resource hierarchy, mathematically eliminating cyclic deadlocks with order allocation (`WorkflowService`).
* **Verification:**
  - Authored multithreaded integration test `StockLockOrderingConcurrencyIntegrationTest`:
    - `concurrentRemoveStock_serializesAndPreventsOverselling`: two concurrent threads attempt to write off 7 units each from a stock of 10 (total demand 14 > 10). Verified exactly one succeeds, the second deterministically receives `InsufficientStockException`, and final stock in DB is exactly 3 (0 lost updates, 0 negative stock).
    - `concurrentMultiStockOperations_orderedAscending_doesNotDeadlock`: two concurrent threads perform cross-stock adjustments with opposing ID orders. Verified canonical lock ordering allows both operations to complete in fractions of a second with zero deadlocks (`errorCount == 0`).
  - Verified combined concurrency test suite:
    - `StockLockOrderingConcurrencyIntegrationTest`: 2/2 passed.
    - `StockReservationConcurrencyIntegrationTest`: 2/2 passed.
    - `AllocationAdjustmentConcurrencyIntegrationTest`: 3/3 passed.
    - Total 7/7 concurrency tests passed without errors (`BUILD SUCCESS`).
