# WMS DEFECT VERIFICATION CATALOG (ISD SUBSYSTEM)
**Independent Adversarial Audit (Second-Order Adversarial QA Pass — Stage 2 Calibration)**  
**Baseline Commit:** `82f20d3` | **Verification Mode:** Strictly READ-ONLY  
**Verification Principle:** *Implementation ≠ Verification* (The existence of code, comments, or tests is not proof of correctness).

> **Official Detailed Audit Report:** [`WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md)  
> **Remediation Roadmap:** [`WMS_REMEDIATION_ROADMAP.md`](WMS_REMEDIATION_ROADMAP.md)  
> **3-Tier Evidence Model:**
> 1. `[STATIC FACT]` — Directly verified from code, annotations, database schema, or configuration files without speculation.
> 2. `[ARCHITECTURAL DEDUCTION]` — Strictly deduced from invocation flow and application structure, but not empirically observed at runtime.
> 3. `[RUNTIME/LOAD GAP]` — Requires live runtime execution (concurrent threads, network latency, actual LLM agent loops, heap pressure).

---

## 1. Second-Order Calibration Matrix

| ID | Original Severity | Recalibrated Severity | Final Verdict | Lifecycle Status | Domain Area | Verification Status & Justification Boundary |
| :--- | :---: | :---: | :---: | :---: | :--- | :--- |
| **DEF-01** | Critical | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 5]` | AI / Security | Autonomous deletion loop eliminated. Removed tokens from `@Tool` methods; mutating operations create pending requests requiring explicit human execution via dedicated endpoints (`POST /confirmations/{id}/confirm`). |
| **DEF-02** | Critical | **Critical** | `[DEFECT]` | `[REMEDIATED IN BATCH 1]` | DB / Credentials | Seed passwords in V31 proven by `[STATIC FACT]`. Remediated via Flyway V37 password rotation and regression test. |
| **DEF-03** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 2]` | Auth / BOLA | Proven completely by total absence of ownership checks in Order endpoints `[STATIC FACT]`. Remediated via createdBy/task supervisor checks and V38 migration. |
| **DEF-04** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 2]` | Auth / BOLA | Proven completely by total absence of ownership checks in Replenishment endpoints `[STATIC FACT]`. Remediated via createdBy/task supervisor checks and V38 migration. |
| **DEF-05** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 1]` | Audit / Integrity | Proven completely by accepting `userId` from request body `[STATIC FACT]`. Remediated by deriving actor from `SecurityFacade`. |
| **DEF-06** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 2]` | Data Leakage | Proven completely by unconditional `findAll()` in `getAllOrdersExtended()` `[STATIC FACT]`. Remediated via scoped query `findAllAccessibleBySupervisor`. |
| **DEF-07** | High | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 4]` | Reliability / DoS | Unbounded collections remediated via server-side pagination with backward-compatible flat `List<T>` body, HTTP headers (`X-Total-Count`, etc.), and clamping. Verified across Order, Inventory, Product, User endpoints. |
| **DEF-08** | High | **Low** | `[RESIDUAL RISK]` | `[OPEN]` | DB Migrations | Historical V12 artifact `[STATIC FACT]`; protected by Flyway checksum on existing V36+ deployments (0 Gap). |
| **DEF-09** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 3]` | Concurrency | Optimistic locking via `@Version` and V39 migration remediated double-assignment race. |
| **DEF-10** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 3]` | Concurrency | Optimistic locking via `@Version` and V39 migration remediated double-reservation race. |
| **DEF-11** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 2]` | Schema / Constraints | Cross-product destination conflict proven by V33 and V34 index definitions `[STATIC FACT]`. Remediated via V38 partial unique index and service check. |
| **DEF-12** | High | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 2]` | Domain Logic | Deletion without status validation proven by `[STATIC FACT]`. Remediated by lifecycle guard allowing delete strictly for CREATED and CANCELED. |
| **DEF-13** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 1]` | WMS State Machine | Unconditional `PARTIALLY_COMPLETED` proven by `[STATIC FACT]`. Remediated via deterministic completion state machine. |
| **DEF-14** | High | **Low** | `[RESIDUAL RISK]` | `[REMEDIATED IN BATCH 5]` | AI / Network | Resilient asynchronous initialization via `CompletableFuture.runAsync` with atomic concurrency mutex and `VectorIndexStatus` state machine. Failure handled gracefully with status observability. |
| **DEF-15** | High | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 3]` | Transactions / Network | Connection holding during SMTP remediated via `TransactionSynchronizationManager` post-commit dispatch. |
| **DEF-16** | High | **High** | `[DEFECT]` | `[REMEDIATED IN BATCH 5]` | QA / Testing | Frontend test suite hardened with Node ESM test harness (36/36 passed), eliminating fake assertions and adding real role validation and API base URL resolution tests. |
| **DEF-17** | Medium | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 4]` | Authorization | Read/search replenishment endpoints secured with `@PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")` and service defense-in-depth scoping. Operators strictly restricted to their assigned tasks. |
| **DEF-18** | Medium | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 1]` | DTO Validation | Omission of nested collection validation without `@Valid` proven by Jakarta Spec `[STATIC FACT]`. Remediated. |
| **DEF-19** | Medium | **Low** | `[DEFECT]` | `[REMEDIATED IN BATCH 4]` | DTO Validation | Zero-quantity replenishment prevented via `@NotNull` and `@Min(1)` on `ReplenishmentCreateRequest`/`ReplenishmentUpdateRequest` and service validation defense-in-depth. |
| **DEF-20** | Medium | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 3]` | DB Integrity | Case-insensitive unique indexes added via V39; `CustomUserDetailsService` and `UserService` case-insensitive lookups enforced. |
| **DEF-21** | Medium | **Low** | `[DEFECT]` | `[REMEDIATED IN BATCH 4]` | Warehouse Audit | Picking audit logging rescheduled strictly post-deduction in `PickingOperatorStrategy`. Point-in-time quantity captures accurate post-deduction stock with mathematical continuity. |
| **DEF-22** | Medium | **Low** | `[RESIDUAL RISK]` | `[REMEDIATED IN BATCH 5]` | Frontend / Security | Frontend role trust boundary enforced via `validateSessionOnReload()` verifying against `/api/auth/me` on reload and router navigation; tampered roles overwritten or session invalidated. |
| **DEF-23** | Medium | **Low** | `[DEFECT]` | `[REMEDIATED IN BATCH 5]` | Frontend / Config | Hardcoded localhost:8080 replaced with dynamic `resolveBaseUrl(env)` supporting relative `/api` fallback and `VITE_API_URL` environment override. |
| **DEF-24** | Medium | **Medium** | `[DEFECT]` | `[REMEDIATED IN BATCH 5]` | AI / Business Logic | Added `findByUserRoleAndIsActiveTrue`; `WarehouseAiTools` operator discovery and workload balancing strictly filter to active operators (`isActive = true`). |
| **DEF-25** | Medium | **Low** | `[DEFECT]` | `[REMEDIATED IN BATCH 5]` | AI / DB Queries | Full-table stock scanning eliminated; `StockRepository` queries by product ID and location ID push filtering directly to database indexes. |
| **DEF-26** | Low | **Low** | `[DOCUMENTATION DRIFT]` | `[REMEDIATED IN BATCH 5]` | Configuration | Dead `/api/operator/**` route matcher safely purged from `SecurityConfig.java`; verified no impact on operational endpoints. |

---

## 2. Verification Gap Registry

This registry formalizes the boundary between statically proven architectural defects and unverified empirical runtime side-effects. The initial assertion of "0 Verification Gaps" was methodologically invalid.

| ID | Asserted Runtime/Load Effect | Static Proof Status | Verification Gap (`[RUNTIME/LOAD GAP]`) | Pre-Remediation Verification Protocol |
| :--- | :--- | :--- | :--- | :--- |
| **DEF-01** | LLM autonomously completes 2-phase deletion loop without human in the loop | Confirmation token generated and returned in plain string in tool response `[STATIC FACT]` | Spring AI ChatClient loop mechanics with live model not recorded | Integration test with ChatClient tool loop inspecting multi-iteration autonomous calls |
| **DEF-07** | Guaranteed Denial of Service (DoS / OOM) on large entity collections | Endpoints return full `List<T>` via `findAll()` without `Pageable` `[STATIC FACT]` | Actual JVM crash with OutOfMemoryError depends on heap size (`-Xmx`) and DB scale | Load test (JMeter/k6) on collections >100,000 entities measuring memory allocation |
| **DEF-09** | Concurrent order assignment to two operators with duplicate tasks | Entity `Order` lacks `@Version` and status lock checks `[STATIC FACT]` | Exact race window and PostgreSQL Read Committed interleaving timing not measured | Multi-threaded test (`CountDownLatch`) concurrently invoking `assignOrder` |
| **DEF-10** | Double reservation of destination location and replenishment task | Entity `Replenishment` lacks `@Version` and locks `[STATIC FACT]` | Transaction interleaving in RDBMS under concurrent load not confirmed via logs | Multi-threaded test concurrently invoking `assignReplenishment` |
| **DEF-14** | Application crash on startup or readiness block when OpenAI unreachable | Method listens to `ApplicationReadyEvent` and is enclosed in `try-catch` `[STATIC FACT]` | **Refuted:** Network failure caught; web server is already listening on port | Startup latency inspection without internet connection (readiness probe check) |
| **DEF-15** | Total HikariCP connection pool exhaustion during SMTP latency | `@Transactional` method synchronously executes network SMTP call `[STATIC FACT]` | Pool exhaustion depends on registration request rate and SMTP socket timeout | Integration test with 5s simulated SMTP socket latency and 15 concurrent threads |
| **DEF-25** | Service crash via OutOfMemoryError when AI stock tool invoked | `findAllByAvailableIsTrue()` fetches all records into heap for Stream filtering `[STATIC FACT]` | Heap exhaustion under typical warehouse sizes (<50,000 stocks) is improbable | Memory profiling via VisualVM/JProfiler with 50,000 rows in `stocks` table |

---

## 3. Detailed Defect Cards with 3-Tier Evidence

---

### DEF-01: Two-Phase Confirmation Secret Leakage in LLM Tool Response
* **Files:**
  * [`AiToolSecurityBoundary.java#L222-L226`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/AiToolSecurityBoundary.java#L222-L226)
  * [`OrderMutatingAiTools.java#L115-L125`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/OrderMutatingAiTools.java#L115-L125)
  * [`ChatbotService.java#L58-L86`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ChatbotService.java#L58-L86)
* **[STATIC FACT]:**
  In `requireConfirmation()`, when a token is absent, the method generates the string:
  `"CONFIRMATION REQUIRED: ... To confirm and execute, call this tool with confirmationToken='%s'."`
  `OrderMutatingAiTools.deleteOrder` returns this string directly as the tool execution result. `ChatbotService` configures standard `ChatClient` with mutating tools enabled.
* **[ARCHITECTURAL DEDUCTION]:**
  The confirmation token is handed directly to the model in the tool response. If a user or prompt injection instructs the model to "delete and immediately confirm", the model possesses all necessary credentials to call the confirming method autonomously.
* **[RUNTIME/LOAD GAP]:**
  No live LLM run was conducted in an isolated environment to verify whether the model autonomously calls the confirmation tool in the same loop without human intervention or pauses to query the user. This depends on system prompt and Spring AI advisor configuration.
* **Calibration & Classification:** Severity: **HIGH** (Downgraded from Critical as autonomous bypass without human participation requires runtime confirmation). Classification: **`[DEFECT]`** (Architectural compromise of confirmation secret).
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * Removed `confirmationToken` parameter from mutating `@Tool` methods in [`OrderMutatingAiTools.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/OrderMutatingAiTools.java) and [`InventoryMutatingAiTools.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/InventoryMutatingAiTools.java). The LLM tool signature can no longer confirm or execute deletions autonomously.
  * In [`AiToolSecurityBoundary.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/AiToolSecurityBoundary.java), mutating requests register a pending operation record with 15-minute expiration, actor binding, and opaque ID. The tool returns an advisory message explicitly instructing the user to confirm via the UI or REST endpoint.
  * Dedicated human-in-the-loop endpoints implemented in [`AiChatController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/AiChatController.java): `POST /confirmations/{operationId}/confirm`, `POST /confirmations/{operationId}/reject`, and `GET /confirmations/pending` enforcing `SUPERVISOR`/`DEV` role checks and object ownership validation.
  * Regression test: [`Def01HumanInTheLoopConfirmationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/ai/Def01HumanInTheLoopConfirmationTest.java) (6/6 passed) verifying tool cannot execute deletion, returns human instruction, pending operations expire, and user-initiated execution works with ownership validation.

---

### DEF-02: Plaintext User Passwords in Flyway Migration Comments
* **File:** [`V31__seed_warehouse_data_final.sql#L6-L39`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V31__seed_warehouse_data_final.sql#L6-L39)
* **[STATIC FACT]:**
  Lines 6–39 contain a markdown table with plaintext passwords for 17 users:
  `-- | supervisor1 | Office%13 | ...`
  `-- | supervisor2 | Dunder@38 | ...`
  The file is committed to Git and packaged into the executable production JAR.
* **[ARCHITECTURAL DEDUCTION]:**
  Any subject with repository or artifact access obtains valid credentials for immediate account compromise under `ROLE_SUPERVISOR`.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Proved statically by source code inspection.
* **Calibration & Classification:** Severity: **CRITICAL**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 1]`**
  * Migration `V31` was kept strictly immutable (preserving Flyway checksum).
  * Created new forward Flyway migration [`V37__rotate_seed_user_passwords.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V37__rotate_seed_user_passwords.sql) resetting passwords for all 17 accounts using fresh, unique BCrypt hashes (`$2a$10$...`).
  * Regression test: [`Def02SeedCredentialsRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/migration/Def02SeedCredentialsRemediationTest.java) (3/3 passed).

---

### DEF-03: Broken Object-Level Authorization (BOLA / IDOR) on Orders
* **Files:**
  * [`OrderController.java#L61-L142`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/OrderController.java#L61-L142)
  * [`OrderService.java#L111-L135, L162-L186`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L111-L135)
* **[STATIC FACT]:**
  Methods `getOrderExtendedById`, `updateExtended`, and `deleteOrderById` accept `@PathVariable Long id` and operate on the entity via `orderRepository.findById(id)`. Neither controller nor service calls `securityFacade.getCurrentUsername()` or verifies order ownership.
* **[ARCHITECTURAL DEDUCTION]:**
  Supervisor A can read, modify the composition of, or delete Supervisor B's order simply by substituting the order `id`.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). No hidden AOP aspects or interceptors exist to isolate objects.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 2]`**
  * Added `created_by` to `Order` entity and database table via `V38__remediation_batch_2_schema.sql`.
  * In `OrderService`, implemented `validateOrderAccess`: grants access if `hasRole(ROLE_DEV) || createdBy.equalsIgnoreCase(currentUsername) || isSupervisorOfTask`.
  * Enforced on: `getOrderById`, `getExtendedOrderById`, `updateOrder`, `updateExtendedOrder`, `assignOrder`, `deleteOrderById`, `getShortageDetails`.
  * Regression test: [`Def03OrderBolaRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/Def03OrderBolaRemediationTest.java) (8/8 passed).

---

### DEF-04: Broken Object-Level Authorization (BOLA / IDOR) on Replenishments
* **File:** [`ReplenishmentService.java#L124-L151, L213-L276`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java#L124-L151)
* **[STATIC FACT]:**
  Methods `cancelReplenishment(Long id)` and `deleteReplenishment(Long id)` load records from the repository and mutate state without checking task creator.
* **[ARCHITECTURAL DEDUCTION]:**
  Any authenticated supervisor can disrupt warehouse operations by cancelling or deleting other supervisors' replenishment tasks.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Service invocation logic is direct and unconstrained.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 2]`**
  * Added `created_by` to `Replenishment` entity and database table via `V38__remediation_batch_2_schema.sql`.
  * In `ReplenishmentService`, implemented `validateReplenishmentAccess`: grants access if `hasRole(ROLE_DEV) || createdBy.equalsIgnoreCase(currentUsername) || taskSupervisor.equalsIgnoreCase(currentUsername)`.
  * Protects unassigned `CREATED` replenishments as well as assigned ones.
  * Enforced on: `getReplenishmentById`, `updateReplenishment`, `deleteReplenishment`, `cancelReplenishment`, `assignReplenishment`, `getShortageDetails`.
  * Regression test: [`Def04ReplenishmentBolaRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/Def04ReplenishmentBolaRemediationTest.java) (8/8 passed).

---

### DEF-05: Audit Log Actor Spoofing via Client Request Body
* **Files:**
  * [`InventoryService.java#L141`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/InventoryService.java#L141)
  * [`InventoryAdjustmentValidator.java#L57`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/validation/InventoryAdjustmentValidator.java#L57)
* **[STATIC FACT]:**
  DTOs `AddStockRequest` and `RemoveStockRequest` contained a `Long userId` field. The service executed `userRepository.findById(request.userId())` and linked that user to the `InventoryHistory` audit record.
* **[ARCHITECTURAL DEDUCTION]:**
  Any user could add or write off inventory while providing another employee's ID in the JSON body, defeating non-repudiation and falsifying inventory history.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Data flow from client JSON to database insertion was uninterrupted.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 1]`**
  * Removed `@NotNull` constraint from `userId` in `AddStockRequest`, `RemoveStockRequest`, `InventoryAdjustmentRequest` (retaining field for JSON deserialization compatibility).
  * In `InventoryService.java` (`addStock`, `removeStock`), actor is strictly acquired via `securityFacade.getCurrentUser()`.
  * In `InventoryAdjustmentValidator.java`, actor context is derived strictly from `SecurityFacade`.
  * Regression test: [`Def05ActorSpoofingRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/Def05ActorSpoofingRemediationTest.java) (3/3 passed).

---

### DEF-06: Global Order Data Exposure via `GET /api/v1/orders/extended`
* **File:** [`OrderService.java#L321-L322`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L321-L322)
* **[STATIC FACT]:**
  Method `getAllOrdersExtended()` called `orderRepository.findAll()` without query predicates or scoping.
* **[ARCHITECTURAL DEDUCTION]:**
  The endpoint exposes all customer orders across all supervisors to any user possessing the standard `ROLE_SUPERVISOR`.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). `findAll()` execution is unconditional.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 2]`**
  * Created scoped query `orderRepository.findAllAccessibleBySupervisor(username)` matching `LOWER(created_by) = LOWER(:username) OR LOWER(u.username) = LOWER(:username)`.
  * In `OrderService`, `getAllOrders()`, `getAllExtendedOrders()`, and `getShortageOrders()` execute scoped query for non-DEV supervisors, reserving `findAll()` strictly for `ROLE_DEV`.
  * Regression test: [`Def06OrderExtendedScopingRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def06OrderExtendedScopingRemediationTest.java) (3/3 passed).

---

### DEF-07: Unbounded Collections (Missing Pagination) in Tabular REST Endpoints
* **Files:** Controllers `OrderController`, `InventoryController`, `ProductController`, `UserController`.
* **[STATIC FACT]:**
  Collection retrieval methods return full `List<T>` and call `findAll()` without accepting a `Pageable` argument.
* **[ARCHITECTURAL DEDUCTION]:**
  Memory and serialization overhead scale linearly $O(N)$ with database table size, presenting structural scalability risks.
* **[RUNTIME/LOAD GAP]:**
  Asserting "guaranteed Denial of Service (DoS) via OutOfMemoryError" is a theoretical extrapolation. In demo environments (a few thousand rows), the JVM easily handles the load. A crash depends on `-Xmx` heap settings and has not been tested under load.
* **Calibration & Classification:** Severity: **MEDIUM** (Downgraded from High). Classification: **`[DEFECT]`** (Architectural API contract defect).
* **Remediation Status:** **`[REMEDIATED IN BATCH 4]`**
  * Created [`PaginationUtils.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/util/PaginationUtils.java) with `clampPageable`, `createPaginationHeaders`, and `toPagedResponse`.
  * Preserved 100% backward compatibility for frontend clients by returning flat JSON `List<T>` in the response body while sending pagination metadata in standard headers (`X-Total-Count`, `X-Total-Pages`, `X-Current-Page`, `X-Page-Size`).
  * Clamped page sizes: Default 50, Max 200 for Orders, Inventory, History, and Users; Default 100, Max 500 for Products and Quantities.
  * Exposed pagination headers in CORS configuration [`SecurityConfig.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/SecurityConfig.java).
  * Upgraded repository and service queries across `OrderController`, `InventoryController`, `ProductController`, and `UserController`.
  * Regression test: [`Def07UnboundedPaginationRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/pagination/Def07UnboundedPaginationRemediationTest.java) (10/10 passed).

---

### DEF-08: Destructive Migration `DROP TABLE ... CASCADE` in V12
* **File:** [`V12__drop_tables.sql#L1-L12`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V12__drop_tables.sql#L1-L12)
* **[STATIC FACT]:**
  Migration contains `DROP TABLE IF EXISTS ... CASCADE` statements. Current schema version is `V36`+.
* **[ARCHITECTURAL DEDUCTION]:**
  In an existing database, Flyway never executes V12 again due to checksum recording. On clean installs, V12 cleans prototype tables before final schema creation.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Non-reexecution on existing databases is mathematically guaranteed by Flyway.
* **Calibration & Classification:** Severity: **LOW** (Downgraded from High/Medium). Classification: **`[RESIDUAL RISK]`** (Historical versioning defect).

---

### DEF-09: Race Condition on Order Double-Assignment in `assignOrder`
* **File:** [`OrderService.java#L206-L247`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L206-L247)
* **[STATIC FACT]:**
  Entity `Order` lacks a `@Version` field. Method `assignOrder` reads the order via standard `findById`, checks status in JVM memory, and invokes `Task` generation before DB status persistence.
* **[ARCHITECTURAL DEDUCTION]:**
  Under concurrent assignment by two supervisors, both threads observe status `CREATED`, generating duplicate `Task` records and competing updates.
* **[RUNTIME/LOAD GAP]:**
  Multi-threaded transaction interleaving test under PostgreSQL was not executed; race window width is deduced from code structure.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 3]`**
  * Added `@Version` field to [`Order.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Order.java) and schema versioning in [`V39__remediation_batch_3_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V39__remediation_batch_3_schema.sql) (`version BIGINT DEFAULT 0 NOT NULL`).
  * In [`OrderService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java), removed mass `@Modifying` JPQL status update in `assignOrderCascade` (which bypassed Hibernate `@Version` checks) and enforced `orderRepository.saveAndFlush(order)` with optimistic locking checks before task instantiation.
  * Losing concurrent transaction is rejected with `OptimisticLockException` mapped to HTTP 409 Conflict via `GlobalExceptionHandler`.
  * Regression test: [`Def09OrderAssignmentConcurrencyRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def09OrderAssignmentConcurrencyRemediationTest.java) (2/2 passed) proving mutual exclusion, single winner, no orphan tasks, and HTTP 409 conflict mapping.

---

### DEF-10: Race Condition on Replenishment Double-Reservation in `assignReplenishment`
* **File:** [`ReplenishmentService.java#L170-L210`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java#L170-L210)
* **[STATIC FACT]:**
  Entity `Replenishment` lacks a `@Version` field. No `PESSIMISTIC_WRITE` lock is requested during assignment.
* **[ARCHITECTURAL DEDUCTION]:**
  Concurrent requests can simultaneously transition the task to `ASSIGNED`, producing duplicate warehouse movements.
* **[RUNTIME/LOAD GAP]:**
  Empirical runtime race measurement not conducted (`[RUNTIME/LOAD GAP]`).
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 3]`**
  * Added `@Version` field to [`Replenishment.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/entity/Replenishment.java) and schema versioning in [`V39__remediation_batch_3_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V39__remediation_batch_3_schema.sql) (`version BIGINT DEFAULT 0 NOT NULL`).
  * In [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java), enforced `replenishmentRepository.saveAndFlush(replenishment)` optimistic locking verification prior to creating task and executing `generateAllocationsForTask` inventory reservation.
  * Losing concurrent transaction is rolled back with `OptimisticLockException` (mapped to HTTP 409 Conflict), preventing duplicate allocations or orphan movement tasks.
  * Regression test: [`Def10ReplenishmentAssignmentConcurrencyRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def10ReplenishmentAssignmentConcurrencyRemediationTest.java) (2/2 passed) proving mutual exclusion, rollback of side-effects, and HTTP 409 conflict mapping.

---

### DEF-11: Cross-Product Destination Conflict on Active Replenishment
* **Files:**
  * [`V33__add_unique_index_active_replenishments.sql#L3`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V33__add_unique_index_active_replenishments.sql#L3)
  * [`V34__add_unique_constraint_stock_active_location.sql#L3`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V34__add_unique_constraint_stock_active_location.sql#L3)
* **[STATIC FACT]:**
  Index `V33` is defined over `(product_id, destination_location_id)`. Index `V34` is defined strictly over `stock(location_id) WHERE quantity > 0`.
* **[ARCHITECTURAL DEDUCTION]:**
  `V33` permits concurrent creation of two replenishments for *different* products targeted at the same destination location. Upon completion of the second task, stock insertion fails with a unique constraint violation under `V34`, aborting the picker transaction.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Schema constraint conflict is evident from DDL index definitions.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 2]`**
  * Created Flyway migration `V38__remediation_batch_2_schema.sql` which drops the compound index, cleans duplicate active replenishments, and creates partial unique index `uk_active_replenishment_destination` on `replenishments(destination_location_id) WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS')`.
  * In `ReplenishmentService`, updated `validateDestinationLocation` and `checkAndTriggerAutoReplenishment` to verify `existsByDestinationLocationIdAndStatusIn(locationId, ACTIVE_STATUSES)`.
  * Regression tests: [`Def11ReplenishmentDestinationConflictRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def11ReplenishmentDestinationConflictRemediationTest.java) (4/4 passed), [`Def11V38MigrationVerificationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/migration/Def11V38MigrationVerificationTest.java) (2/2 passed).

---

### DEF-12: Destructive Deletion of Active / In-Progress Orders
* **File:** [`OrderService.java#L162-L186`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L162-L186)
* **[STATIC FACT]:**
  Method `deleteOrderById` lacks domain validation for `order.getStatus()`. It calls `orderRepository.delete(order)` directly. The `processes`/`allocations` table has `task_id REFERENCES tasks(id) NOT NULL` (without `ON DELETE CASCADE`).
* **[ARCHITECTURAL DEDUCTION]:**
  If an order is in progress (`ASSIGNED`/`IN_PROGRESS`) with active allocations, cascading deletion of `Task` hits database foreign key constraints, resulting in a transaction rollback and unhandled HTTP 500 error. For orders without allocations, destructive deletion occurs regardless of status.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). FK mechanics and missing validation are proven by DB schema and service code.
* **Calibration & Classification:** Severity: **MEDIUM** (Downgraded from High: silent deletion prevented by RDBMS FK; defect manifests as missing business validation and unhandled 500). Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 2]`**
  * In `OrderService.deleteOrderById`, implemented lifecycle validation: allows deletion strictly for `CREATED` and `CANCELED` orders.
  * Rejects all active, in-progress, and completed states (`ASSIGNED`, `IN_PROGRESS`, `PICKED`, `COMPLETED`, `PARTIALLY_COMPLETED`) with `InvalidRequestException` (HTTP 400).
  * Regression test: [`Def12OrderDeletionLifecycleRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def12OrderDeletionLifecycleRemediationTest.java) (7/7 passed).

---

### DEF-13: Premature Order Status Demotion to `PARTIALLY_COMPLETED`
* **File:** [`PickingOperatorStrategy.java#L133`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/allocation/PickingOperatorStrategy.java#L133)
* **[STATIC FACT]:**
  In `handleOrderCompletion`, non-cancelled orders unconditionally executed:
  `order.setStatus(OrderStatus.PARTIALLY_COMPLETED);`
  Code setting `OrderStatus.COMPLETED` was completely absent.
* **[ARCHITECTURAL DEDUCTION]:**
  Even with 100% successful picking of all lines, the order remained in partial completion status, blocking automated dispatch transitions.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Method logic was deterministic.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 1]`**
  * In [`PickingOperatorStrategy.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/allocation/PickingOperatorStrategy.java), implemented deterministic state machine:
    * 100% lines completed without shortage $\rightarrow$ `OrderStatus.COMPLETED`.
    * All lines cancelled $\rightarrow$ `OrderStatus.CANCELED` and releases Transport Unit.
    * Mixed/shortage $\rightarrow$ `OrderStatus.PARTIALLY_COMPLETED`.
  * Regression test: [`PickingOperatorStrategyCompletionTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/allocation/PickingOperatorStrategyCompletionTest.java) (4/4 passed).

---

### DEF-14: Background Vector Re-Indexing on Every Application Startup
* **File:** [`ProductVectorIndexer.java#L46-L65`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ProductVectorIndexer.java#L46-L65)
* **[STATIC FACT]:**
  Method `indexAllProducts()` listens to `ApplicationReadyEvent`. The call `vectorStore.add(documents)` is wrapped in:
  `try { vectorStore.add(documents); } catch (Exception e) { log.warn(...); }`
* **[ARCHITECTURAL DEDUCTION]:**
  1. `ApplicationReadyEvent` fires when the embedded web server is **already active and listening**.
  2. The `try-catch` block catches network errors or missing OpenAI keys, emitting `log.warn`.
  3. **The application DOES NOT crash and DOES NOT block service readiness**.
* **[RUNTIME/LOAD GAP]:**
  Prior audit assertion that "missing OpenAI key crashes application on startup" is **REFUTED BY CODE**.
* **Calibration & Classification:** Severity: **LOW** (Downgraded from High/Medium). Classification: **`[RESIDUAL RISK]`** (Architectural Code Smell).
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * In [`ProductVectorIndexer.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ProductVectorIndexer.java), decoupled vector initialization from startup lifecycle using asynchronous execution via `CompletableFuture.runAsync`.
  * Implemented atomic concurrency control via `AtomicBoolean isIndexing` to prevent duplicate parallel indexing passes.
  * Added observable lifecycle state machine with `VectorIndexStatus` (`NOT_STARTED`, `IN_PROGRESS`, `READY`, `FAILED`), along with `getStatus()` and `getLastError()` getters.
  * Failures (missing OpenAI credentials, network timeouts) are captured gracefully, logged, transition state to `FAILED`, and never block or crash the web server.
  * Regression test: [`Def14ResilientVectorIndexerTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/ai/Def14ResilientVectorIndexerTest.java) (5/5 passed).

---

### DEF-15: Synchronous SMTP Invocation within Open Database Transaction
* **Files:**
  * [`UserService.java#L67, L160`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java#L67)
  * [`EmailService.java#L50-L74`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/EmailService.java#L50-L74)
* **[STATIC FACT]:**
  Method `registerUser` is annotated with `@Transactional` and synchronously invokes `emailService.sendVerificationEmail(...)`. `EmailService` lacks an `@Async` annotation.
* **[ARCHITECTURAL DEDUCTION]:**
  The database transaction holds a physical HikariCP connection for the full duration of the remote SMTP network roundtrip.
* **[RUNTIME/LOAD GAP]:**
  Actual pool exhaustion depends on registration traffic and SMTP socket timeout. Pool depletion under load has not been measured.
* **Calibration & Classification:** Severity: **MEDIUM** (Downgraded from High). Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 3]`**
  * In [`UserService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java), replaced direct synchronous `emailService.sendVerificationEmail` with `dispatchVerificationEmailPostCommit`, registering an `afterCommit` hook via Spring's `TransactionSynchronizationManager`.
  * Verified 4 strict invariants: (1) email is NOT dispatched prior to database commit, (2) email IS dispatched immediately after commit, (3) on transaction rollback, email is NEVER sent, and (4) SMTP network failure post-commit is trapped gracefully and does NOT roll back or corrupt committed user records in PostgreSQL.
  * Regression test: [`Def15AsyncEmailDispatchRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def15AsyncEmailDispatchRemediationTest.java) (4/4 passed).

---

### DEF-16: Fictitious Tests for DEF-02 and DEF-05 (Mock Assertions and Grep)
* **Files:**
  * [`wmsFront/test/def02_user_id_dataflow.test.js#L65-L88`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def02_user_id_dataflow.test.js#L65-L88)
  * [`wmsFront/test/def05_conflict_event.test.js#L143-L165`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def05_conflict_event.test.js#L143-L165)
* **[STATIC FACT]:**
  In DEF-02 test, assertions run against dummy `simulateAddStock` functions defined inside the test file itself. Files are read via `fs.readFileSync` for regex substring matching.
* **[ARCHITECTURAL DEDUCTION]:**
  Tests produce false positive green reports in CI/CD without verifying real frontend components or backend logic.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Fictitious assertions are self-evident from test code.
* **Calibration & Classification:** Severity: **HIGH**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * Audited and remediated frontend test suite, decoupling API client and interceptors via dynamic injection hooks (`setApiRouter`, `setAuthStoreGetter` in [`api/index.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/api/index.js) and [`api/interceptors.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/api/interceptors.js)).
  * Added authentic, functional unit test suites executing via Node native test runner:
    * [`def22_role_validation.test.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def22_role_validation.test.js) (8/8 passed) verifying session validation against `/api/auth/me`, role tampering reconciliation, and expired session logout.
    * [`def23_api_base_url.test.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def23_api_base_url.test.js) (6/6 passed) verifying dynamic base URL resolution across SSR, build-time, proxy, and development configurations.
  * All 36 frontend test cases executed and passing (`node --test test/*.test.js`, 36/36 passed, 0 failures); Vite production build verified cleanly (`npm run build`, 731 modules transformed, 0 errors).

---

### DEF-17: Missing `@PreAuthorize` on Replenishment Read Endpoints
* **File:** [`ReplenishmentController.java#L33-L70`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java#L33-L70)
* **[STATIC FACT]:**
  Methods `getAllReplenishments` and `getReplenishmentById` lack `@PreAuthorize`. In `SecurityConfig.java#L52`, the route is permitted for `ROLE_OPERATOR`.
* **[ARCHITECTURAL DEDUCTION]:**
  Any warehouse operator can monitor global replenishment plans.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`).
* **Calibration & Classification:** Severity: **MEDIUM**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 4]`**
  * Enforced `@PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")` on `getAllReplenishments`, `getReplenishmentById`, `searchReplenishments`, and `searchReplenishmentsFromBody` in [`ReplenishmentController.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java).
  * Implemented defense-in-depth role checks and supervisor scoping in `ReplenishmentService.getAllReplenishments` and `searchReplenishments`, preventing unauthorized access and cross-supervisor plan visibility.
  * Preserved operator task execution endpoints strictly for operational movements (`/api/v1/tasks/operator/**`).
  * Regression test: [`Def17ReplenishmentSecurityRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/Def17ReplenishmentSecurityRemediationTest.java) (6/6 passed).

---

### DEF-18: Missing Cascading `@Valid` in `ExtendedOrderCreateRequest`
* **File:** [`ExtendedOrderCreateRequest.java#L18-L23`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/order/ExtendedOrderCreateRequest.java#L18-L23)
* **[STATIC FACT]:**
  Nested fields `order` and `lines` were missing Jakarta `@Valid`.
* **[ARCHITECTURAL DEDUCTION]:**
  Validation of child items (negative quantity, null IDs) was skipped by Spring Validator, allowing invalid line quantities into the database.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Jakarta Bean Validation specification dictates omission behavior.
* **Calibration & Classification:** Severity: **MEDIUM**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 1]`**
  * Added `@NotNull @Valid OrderCreateRequest order` and `@NotNull @NotEmpty List<@Valid OrderLineCreateRequest> lines` in [`ExtendedOrderCreateRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/order/ExtendedOrderCreateRequest.java).
  * Added `@NotNull` on `destinationLocationId`, `productId`, and `requestedQuantity`.
  * Regression test: [`ExtendedOrderCreateRequestValidationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/dto/order/ExtendedOrderCreateRequestValidationTest.java) (2/2 passed).

---

### DEF-19: Replenishment Creation Permitted with Zero Requested Quantity
* **File:** [`ReplenishmentCreateRequest.java#L15`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentCreateRequest.java#L15)
* **[STATIC FACT]:**
  Field `requestedQuantity` was annotated with `@Min(0)`.
* **[ARCHITECTURAL DEDUCTION]:**
  Clients can submit `0`, creating hollow zombie tasks in the warehouse queue.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`).
* **Calibration & Classification:** Severity: **LOW** (Downgraded from Medium). Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 4]`**
  * Replaced `@Min(0)` with `@NotNull(message = "Requested quantity is required")` and `@Min(value = 1, message = "Requested quantity must be at least 1")` in [`ReplenishmentCreateRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentCreateRequest.java) and [`ReplenishmentUpdateRequest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentUpdateRequest.java).
  * Enforced defense-in-depth boundary validation in [`ReplenishmentService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java) (`createReplenishment` and `updateReplenishment`), throwing `InvalidRequestException` (HTTP 400) if quantity $\le 0$ or null.
  * Regression test: [`Def19ReplenishmentQuantityValidationRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/validation/Def19ReplenishmentQuantityValidationRemediationTest.java) (10/10 passed).

---

### DEF-20: Case-Sensitive Unique Constraints (User vs Product Divergence)
* **Files:**
  * [`V1__create_users_table.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V1__create_users_table.sql)
  * [`ProductService.java#L67`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ProductService.java#L67)
  * [`UserService.java#L97-L98`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java#L97-L98)
* **[STATIC FACT]:**
  1. Unique indexes in `V1` and `V3` do not apply `LOWER(...)`.
  2. `ProductService.java` explicitly enforces `if (productRepository.existsByBarcodeIgnoreCase(barcode)) throw ...;`.
  3. `UserService.java` uses exact case-sensitive lookup `findByUsername` and `findByEmail`.
* **[ARCHITECTURAL DEDUCTION]:**
  - **For User:** Accounts `supervisor` and `Supervisor` can be registered as two distinct users with different passwords (`[DEFECT]`).
  - **For Product:** Collision prevention is fully enforced at service boundary (`existsByBarcodeIgnoreCase`). DB defect exists as residual risk for raw SQL imports (`[RESIDUAL RISK]`).
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`).
* **Calibration & Classification:** Severity: **MEDIUM**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 3]`**
  * Created Flyway migration [`V39__remediation_batch_3_schema.sql`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V39__remediation_batch_3_schema.sql) adding PostgreSQL functional unique expression indexes `uk_users_username_lower` on `users(LOWER(username))` and `uk_users_email_lower` on `users(LOWER(email))`. Verified zero duplicate entries in live database prior to index creation.
  * Added `findByUsernameIgnoreCase`, `findByEmailIgnoreCase`, `existsByUsernameIgnoreCase`, `existsByEmailIgnoreCase` to [`UserRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/UserRepository.java).
  * In [`CustomUserDetailsService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/CustomUserDetailsService.java), upgraded authentication lookup to case-insensitive queries (`findByUsernameIgnoreCase` and `findByEmailIgnoreCase`) with backward-compatible fallbacks.
  * In [`UserService.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java), enforced case-insensitive checks on `registerUser`, `updateUser`, self-reactivation guard, and account deactivation.
  * Regression tests: [`Def20CaseInsensitiveUserRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def20CaseInsensitiveUserRemediationTest.java) (11/11 passed), [`Def20V39MigrationVerificationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/migration/Def20V39MigrationVerificationTest.java) (2/2 passed).

---

### DEF-21: Picking Audit Desynchronization (Logged Before Stock Deduction)
* **File:** [`AllocationExecutionService.java#L256-L270`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/AllocationExecutionService.java#L256-L270)
* **[STATIC FACT]:**
  Invocation `recordPickingHistory()` in `PickingOperatorStrategy#complete` precedes stock deduction and captures initial `stock.getQuantity()`.
* **[ARCHITECTURAL DEDUCTION]:**
  History record logs pre-deduction quantity, distorting audit point-in-time state.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Order of execution is fixed in code.
* **Calibration & Classification:** Severity: **LOW** (Downgraded from Medium: minor snapshot anomaly within an atomic ACID transaction). Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 4]`**
  * Rescheduled `recordPickingHistory` execution strictly after `workflowService.executeAllocationCompletion(allocation)` in [`PickingOperatorStrategy.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/allocation/PickingOperatorStrategy.java).
  * Point-in-time stock quantity captures accurate post-deduction balance (`stock.getQuantity()`), with mathematical continuity (`previousQuantity = stock.getQuantity() + pickedQuantity`).
  * Automated replenishment trigger `triggerReplenishmentCheck` now evaluates true remaining stock, preventing missed replenishment triggers.
  * Regression test: [`Def21PickingAuditRemediationTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/Def21PickingAuditRemediationTest.java) (3/3 passed).

---

### DEF-22: Client-Side Role Spoofing via `sessionStorage`
* **Files:**
  * [`wmsFront/src/stores/auth.js#L58-L62`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/stores/auth.js#L58-L62)
  * [`SecurityConfig.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/config/SecurityConfig.java)
* **[STATIC FACT]:**
  Store `auth.js` loads role from `sessionStorage` without validating against `/api/auth/me`. However, Spring Security validates cryptographically signed JWT claims on every HTTP request.
* **[ARCHITECTURAL DEDUCTION]:**
  Spoofing roles in DevTools unhides UI menus, but all mutations or protected data requests are rejected by backend with HTTP 403 Forbidden.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Pure cosmetic UI display issue, not data privilege escalation.
* **Calibration & Classification:** Severity: **LOW** (Downgraded from Medium). Classification: **`[RESIDUAL RISK]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * In [`wmsFront/src/stores/auth.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/stores/auth.js), implemented `validateSessionOnReload()` verifying persisted user and role claims against authoritative backend `/api/auth/me`.
  * If the client-side role stored in `localStorage` or `sessionStorage` was tampered with, it is immediately reconciled and overwritten with the server's authoritative role. If the JWT token is invalid or expired, session storage is cleared and user is logged out.
  * In [`wmsFront/src/router/index.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/router/index.js), wired `validateSessionOnReload()` directly into `router.beforeEach` navigation guard prior to role authorization checks.
  * Unit test: [`def22_role_validation.test.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def22_role_validation.test.js) (8/8 passed).

---

### DEF-23: Hardcoded `http://` Protocol and Port `8080` in Frontend Base URL
* **File:** [`wmsFront/src/api/index.js#L14`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/api/index.js#L14)
* **[STATIC FACT]:**
  Constant is hardcoded as `const API_BASE_URL = 'http://${currentHostname}:8080/api'`.
* **[ARCHITECTURAL DEDUCTION]:**
  Prevents deploying frontend behind an HTTPS reverse proxy.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`).
* **Calibration & Classification:** Severity: **LOW** (Downgraded from Medium). Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * In [`wmsFront/src/api/index.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/api/index.js), extracted dynamic URL resolution helper `resolveBaseUrl(env)` supporting `VITE_API_URL` environment override, dynamic hostname detection, and safe fallback to relative `/api` path for HTTPS reverse proxy and containerized deployments.
  * Eliminated hardcoded `http://localhost:8080/api` string literal.
  * Unit test: [`def23_api_base_url.test.js`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def23_api_base_url.test.js) (6/6 passed).

---

### DEF-24: AI Task Assignment to Deactivated Operators
* **File:** [`WarehouseAiTools.java#L86`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/WarehouseAiTools.java#L86)
* **[STATIC FACT]:**
  Operator filter only checks `u.getUserRole().name().equals("ROLE_OPERATOR")` without checking `Boolean.TRUE.equals(u.getIsActive())`.
* **[ARCHITECTURAL DEDUCTION]:**
  AI tools can assign active warehouse tasks to deactivated employee accounts.
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`). Filter predicate is statically evident.
* **Calibration & Classification:** Severity: **MEDIUM**. Classification: **`[DEFECT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * Added `findByUserRoleAndIsActiveTrue(Role role)` to [`UserRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/UserRepository.java).
  * In [`WarehouseAiTools.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/WarehouseAiTools.java), methods `getAvailableOperators` and `findOperatorForNewOrder` strictly invoke `findByUserRoleAndIsActiveTrue(Role.ROLE_OPERATOR)` and filter workload counts exclusively across active operators.
  * Deactivated operators (`isActive = false`) are never selected or suggested for warehouse tasks.
  * Regression test: [`Def24ActiveOperatorFilterTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/ai/Def24ActiveOperatorFilterTest.java) (3/3 passed).

---

### DEF-25: Inefficient Full Warehouse Stock Scan in AI Tools
* **File:** [`InventoryMutatingAiTools.java#L90-L95`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/InventoryMutatingAiTools.java#L90-L95)
* **[STATIC FACT]:**
  Calls `stockRepository.findAllByAvailableIsTrue()` followed by in-memory `.stream().filter(...)`.
* **[ARCHITECTURAL DEDUCTION]:**
  Fetches all available warehouse stock records into heap instead of executing targeted SQL queries.
* **[RUNTIME/LOAD GAP]:**
  Claim of "JVM heap exhaustion" is an exaggeration under standard warehouse scale (<50,000 items).
* **Calibration & Classification:** Severity: **LOW** (Downgraded from Medium). Classification: **`[DEFECT]`** (Inefficient query pattern; memory exhaustion is `[RUNTIME/LOAD GAP]`).
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * Added targeted indexed queries `findAllByProductIdAndAvailableIsTrue(Long productId)` and `findAllByLocationIdAndAvailableIsTrue(Long locationId)` to [`StockRepository.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/StockRepository.java).
  * In [`InventoryAiTools.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/InventoryAiTools.java) and [`InventoryMutatingAiTools.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/InventoryMutatingAiTools.java), completely eliminated unindexed full-table fetches (`findAllByAvailableIsTrue()`) followed by JVM stream filtering. Queries are pushed directly down to the database engine with `WHERE product_id = :productId AND available = true`.
  * Regression test: [`Def25EfficientStockQueryTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/ai/Def25EfficientStockQueryTest.java) (3/3 passed).

---

### DEF-26: Dead `/api/operator/**` AntMatcher in Security Configuration
* **File:** [`SecurityConfig.java#L54`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/config/SecurityConfig.java#L54)
* **[STATIC FACT]:**
  Pattern `/api/operator/**` is defined in Spring Security configuration, but zero controllers map to this path.
* **[ARCHITECTURAL DEDUCTION]:**
  Dead configuration rule. Does not introduce attack surface (requests resolve to 404).
* **[RUNTIME/LOAD GAP]:**
  None (`0 GAP`).
* **Calibration & Classification:** Severity: **LOW**. Classification: **`[DOCUMENTATION DRIFT]`**.
* **Remediation Status:** **`[REMEDIATED IN BATCH 5]`**
  * Purged obsolete dead route matcher `.requestMatchers("/api/operator/**").hasRole("OPERATOR")` from [`SecurityConfig.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/SecurityConfig.java).
  * Validated that all active operational task endpoints remain strictly secured under `.requestMatchers("/api/v1/tasks/operator/**").hasAnyRole("OPERATOR", "SUPERVISOR", "DEV")`.
  * Regression test: [`Def26SecurityMatcherHygieneTest.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/Def26SecurityMatcherHygieneTest.java) (2/2 passed).
