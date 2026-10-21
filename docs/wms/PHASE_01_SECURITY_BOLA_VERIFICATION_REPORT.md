# Phase 01 Security, BOLA Isolation & Performance Verification Report

## 1. Executive Summary
- **Phase Scope**: Phase 01 Remediation (`DEF-27`, `DEF-28`, `DEF-29`, `DEF-30`), N+1 Query Regression Resolution, and Full Suite Stabilization.
- **Verification Status**: `[VERIFIED]`
- **Full Backend Test Suite (`.\mvnw.cmd test`)**:
  - **Tests run**: `369`
  - **Failures**: `0`
  - **Errors**: `0`
  - **Skipped**: `0`
  - **Total time**: `35.136 s`
  - **Exit Code**: `0` (`BUILD SUCCESS`)

---

## 2. Root Causes & Code Remediations (`DEF-27` – `DEF-30`)

### 2.1 `DEF-27`: Out-of-Band Human Confirmation & 15-Minute TTL for Destructive AI Tool Operations
- **Root Cause**:
  1. `InventoryMutatingAiTools.adjustInventoryStock` previously lacked structured pending-operation parameter persistence (`targetEntityId`, `newQuantity`, `reason`) in `AiToolSecurityBoundary.PendingConfirmation`, preventing out-of-band human confirmation in `AiChatController.confirmOperation(operationId)` from deterministically executing the stock adjustment.
  2. Pending confirmation TTL and autonomous confirmation blocking needed strict enforcement across both `DELETE_ORDER` and `ADJUST_INVENTORY_STOCK` flows.
- **Code Remediation**:
  - Extended `AiToolSecurityBoundary.PendingConfirmation` to store `targetEntityId`, `newQuantity`, and `reason` with a 15-minute TTL (`PENDING_TTL_MINUTES = 15`).
  - Updated `InventoryMutatingAiTools.adjustInventoryStock` to initiate a `PENDING HUMAN CONFIRMATION` operation via `securityBoundary.initiatePendingOperation(...)` and reject any autonomous confirmation token passed directly to the AI tool with `AccessDeniedException` (`"Autonomous AI tool confirmation is disabled"`).
  - Updated `AiChatController.confirmOperation` to handle `ADJUST_INVENTORY_STOCK` by resolving the authenticated human supervisor's user ID and invoking `inventoryAdjustmentService.adjustStock(...)`, throwing `IllegalStateException` on unsupported action types.

### 2.2 `DEF-28`: OrderLine BOLA Protection & `OrderStatus.CREATED` Lifecycle Guards
- **Root Cause**:
  1. `OrderLineService` (`createOrderLine`, `addOrderLine`, `updateOrderLine`, `deleteOrderLine`, `getOrderLineById`, `getAll`) did not consistently enforce object-level supervisor ownership checks or restrict line mutations to orders in `OrderStatus.CREATED`.
- **Code Remediation**:
  - Added `OrderLineService.validateOrderAccess(Order)` verifying that non-`ROLE_DEV` callers either created the parent order (`createdBy`), match the transient `order.getSupervisor()`, or supervise a task on the order (`orderRepository.findSupervisorUsernamesByOrder(order)`).
  - Enforced `OrderStatus.CREATED` state guards on `createOrderLine`, `addOrderLine`, `updateOrderLine` (both current and target order), and `deleteOrderLine`.
  - Scoped `OrderLineService.getAll()` for non-`ROLE_DEV` callers to return only order lines belonging to orders supervised or created by the caller.

### 2.3 `DEF-29`: Supervisor Ownership Resolution, Replenishment Allocation Retention, N+1 Elimination & Workload Balancing
- **Root Cause**:
  1. **Inverse `@OneToOne` N+1 Regression**: Adding `@OneToOne(fetch = FetchType.LAZY, mappedBy = "task") private OrderLine orderLine;` to `Task.java` caused Hibernate to issue an extra SQL `SELECT` per `Task` entity (because optional inverse `@OneToOne(mappedBy = ...)` associations cannot be lazily proxied without bytecode enhancement), pushing `NPlusOneQueryPerformanceIntegrationTest.measureReplenishmentQueries()` above its strict `<= 3` query bound.
  2. **Transient `Order.getSupervisor()` N+1 / Null Filtering Bug**: Iterating `orderLines` inside `Order.getSupervisor()` risked lazy-loading N+1 queries, while filtering solely by `order.getSupervisor()` in `OrderService.searchOrders` dropped database-backed orders where `@Transient supervisor` was `null`.
  3. **Dropped Replenishment Allocations**: `AllocationService.getAllAllocations()` filtered solely by `allocation.getOrder() != null`, which dropped all `REPLENISHMENT` allocations (where `orderId` is `null`) for non-`ROLE_DEV` supervisors.
  4. **Replenishment Search Leak**: `ReplenishmentService.searchReplenishments` treated any `Status.CREATED` replenishment as `isUnassigned = true` even when `r.getCreatedBy()` explicitly belonged to another supervisor (`"supervisor_bob"`), breaking supervisor isolation in `Def17ReplenishmentSecurityRemediationTest`.
  5. **AI Tool Isolation & Workload Calculation**: `OrderAiTools.getActiveOrdersInfo()`, `ReplenishmentAiTools.getActiveReplenishmentsInfo()`, and `WarehouseAiTools.autoDistributeWorkload()` required consistent supervisor ownership predicates (including unowned/system items), and `WarehouseAiTools.calculateInitialWorkload()` needed to count both active orders (`ASSIGNED`, `IN_PROGRESS`) and active replenishment tasks per operator.
- **Code Remediation**:
  - **`Task.java` & `Allocation.java`**: Removed the inverse `@OneToOne(mappedBy = "task") private OrderLine orderLine;` field and `getOrderLine()` from `Task.java`; simplified `Allocation.getOrder()` to return the `@Transient` `order` field. Restored `.isLessThanOrEqualTo(3)` in `NPlusOneQueryPerformanceIntegrationTest.measureReplenishmentQueries()`.
  - **`AllocationService.java`**: Updated `getAllAllocations()` to retain allocations for non-`ROLE_DEV` callers when `allocation.getSupervisorId()` matches `currentUserId`, `allocation.getSupervisorUsername()` matches `currentUsername`, or `allocation.getOrder().getSupervisor().getId()` matches `currentUserId`.
  - **`Order.java` & `OrderService.java`**: Simplified `Order.getSupervisor()` to return the `@Transient` field without iterating `orderLines`. Updated `OrderService.validateOrderAccess` to check `isCreator`, `isSupervisor`, `isTaskSupervisor`, and allow `ROLE_SUPERVISOR` to access/assign unowned `OrderStatus.CREATED` orders. Updated `OrderService.searchOrders` to check `order.getSupervisor()`, `order.getCreatedBy()`, and lazily resolved `orderRepository.findAllAccessibleBySupervisor(currentUsername)` IDs.
  - **`ReplenishmentService.java`**: Fixed `searchReplenishments` so a replenishment is treated as unowned only when `(r.getCreatedBy() == null || "system".equalsIgnoreCase(r.getCreatedBy())) && taskSupervisor == null && r.getSupervisor() == null`; otherwise requires `isCreator || isTaskSupervisor || isSupervisorById`.
  - **`OrderAiTools.java`, `ReplenishmentAiTools.java` & `WarehouseAiTools.java`**: Unified supervisor ownership predicates across read/distribution tools and included active orders alongside active replenishments in `calculateInitialWorkload`.

### 2.4 `DEF-30`: Strong Password Enforcement, Token Log Redaction & Dashboard End-Date Boundary
- **Root Cause**:
  1. `StrongPasswordValidator` regex rejected common special characters (`._#-`) and `UserService.verifyEmail` did not validate password complexity before hashing and activating accounts.
  2. `UserService` logged raw email verification tokens in plaintext.
  3. `SupervisorDashboardController` / `SupervisorDashboardService` truncated `endDate` to `00:00:00` instead of `LocalTime.MAX` (`23:59:59.999999999`), excluding same-day completed orders and activity history.
- **Code Remediation**:
  - Updated `StrongPasswordValidator` to accept `[@$!%*?&._#-]` and exposed `StrongPasswordValidator.isStrongPassword(String)`, enforcing it in `UserService.verifyEmail`.
  - Redacted verification token logging in `UserService`.
  - Normalized `endDate` to `atTime(LocalTime.MAX)` in `SupervisorDashboardService`.

---

## 3. Concurrency & Integration Test Remediations

1. **`PickingAllocationCompletionStrategy.java` (`AllocationAdjustmentConcurrencyIntegrationTest`)**:
   - Overrode `handle(Allocation allocation)` to update `allocation.setStatus(...)` (`CANCELED` when `pickedQuantity <= 0`, `PARTIALLY_COMPLETED` when `pickedQuantity < quantity`, `COMPLETED` otherwise) and persist via `allocationRepository.save(allocation)` during `WorkflowService.executeAllocationCompletion(allocation)`.
2. **`AiToolObjectLevelAuthorizationTest.java`**:
   - Updated `deleteOrder_whenAuthorizedSupervisorOwnsOrder_succeedsWithConfirmation`, `deleteOrder_whenDevCaller_allowsAccessAcrossSupervisors`, and `confirmationMechanism_remainsIntactAndSingleUse` to verify the `PENDING HUMAN CONFIRMATION` out-of-band contract via `securityBoundary.confirmOperationByHuman(opId)` and verify that direct token passing to `orderMutatingAiTools.deleteOrder("ORD-001", opId)` throws `AccessDeniedException`.
3. **`OrderLinePickingConcurrencyIntegrationTest.java`**:
   - Added `OrderStatus.PICKED` to the post-pick order status assertion (`PickingOperatorStrategy.handleOrderCompletion` transitions 100%-picked orders to `OrderStatus.PICKED` prior to dispatch).
4. **`LocationProductExclusivityConcurrencyIntegrationTest.java`**:
   - Initialized `replA` and `replB` fixtures with `Status.PARTIALLY_COMPLETED` so setup insertion of two fixtures targeting the same `destLoc` does not violate the `V38` partial unique index `uk_active_replenishment_destination` (`WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS')`) prior to concurrent `replenishmentOperatorStrategy.dispatch(...)` execution.

---

## 4. Verified Query Counts & Test Suite Results (`[VERIFIED]`)

### 4.1 N+1 Query Performance Verification (`NPlusOneQueryPerformanceIntegrationTest`)
| Test Method | Entities Returned | Queries Executed | Strict Upper Bound | Status |
| :--- | :---: | :---: | :---: | :---: |
| `measureReplenishmentQueries` | `5` | `2` | `<= 3` | **PASS** |
| `measureOrderSearchQueries` | `8` | `3` | `<= 5` | **PASS** |
| `measureExtendedOrderQueries` | `8` | `5` | `<= 10` | **PASS** |
| `measureGetAllStockQueries` | `62` | `1` | `<= 2` | **PASS** |
| `measureGetAllHistoryQueries` | `28` | `1` | `<= 2` | **PASS** |

### 4.2 Key Security, Concurrency & Remediation Test Classes
| Test Class | Tests Run | Failures | Errors | Skipped | Time Elapsed |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `Def27Def30SecurityAndIsolationTest` | `14` | `0` | `0` | `0` | `0.055 s` |
| `Def17ReplenishmentSecurityRemediationTest` | `11` | `0` | `0` | `0` | `0.103 s` |
| `AiToolObjectLevelAuthorizationTest` | `13` | `0` | `0` | `0` | `0.131 s` |
| `AiToolSecurityBoundaryTest` | `20` | `0` | `0` | `0` | `0.031 s` |
| `AllocationAdjustmentConcurrencyIntegrationTest` | `3` | `0` | `0` | `0` | `0.374 s` |
| `OrderLinePickingConcurrencyIntegrationTest` | `1` | `0` | `0` | `0` | `0.226 s` |
| `StockReservationConcurrencyIntegrationTest` | `2` | `0` | `0` | `0` | `0.256 s` |
| `LocationProductExclusivityConcurrencyIntegrationTest` | `1` | `0` | `0` | `0` | `0.130 s` |
| `NPlusOneQueryPerformanceIntegrationTest` | `5` | `0` | `0` | `0` | `0.087 s` |
| **Full Suite Total (`.\mvnw.cmd test`)** | **`369`** | **`0`** | **`0`** | **`0`** | **`35.136 s`** |
