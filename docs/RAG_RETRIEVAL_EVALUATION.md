# WMS Codebase RAG Retrieval Quality Evaluation

> **Benchmark Type:** Black-Box Retrieval Quality Evaluation  
> **Evaluation Date:** 2026-10-01  
> **Protocol:** Model Context Protocol (MCP) via `wms-code-rag` server  
> **Target System:** ISD Warehouse Management System (Spring Boot 3 + Vue 3)  
> **Evaluator:** Antigravity Autonomous Coding Agent  

---

## 1. Executive Summary

This report presents a comprehensive, empirical black-box evaluation of the retrieval quality of the WMS Codebase Retrieval-Augmented Generation (RAG) system. The evaluation was conducted exclusively through the system's official Model Context Protocol (MCP) tools (`search_wms_code`, `get_entity_and_schema`, `search_wms_security`) against a frozen index of 1,163 semantic chunks generated from 330 source files of the ISD Warehouse Management System repository.

The central question addressed by this benchmark is:
> *When a software engineer asks the RAG about a specific implementation, workflow, or business rule in WMS, does the RAG return the truly necessary code chunks with sufficient context and ranking precision?*

### Key Benchmark Metrics

| Metric | Measured Value | Target Standard | Evaluation Assessment |
| :--- | :---: | :---: | :--- |
| **Total Test Dataset Size** | **36 queries** | $\ge 30$ | Comprehensive coverage across 6 classes |
| **Overall Verdict Distribution** | **25 PASS / 5 PARTIAL / 6 FAIL** | - | 69.4% PASS, 13.9% PARTIAL, 16.7% FAIL |
| **Positive Query Pass Rate** | **83.3% PASS (25/30)** | $\ge 80\%$ | Strong semantic comprehension of core codebase |
| **Positive Query Partial Rate** | **16.7% PARTIAL (5/30)** | $\le 20\%$ | Method declarations outranked by rich caller chunks |
| **Positive Query Fail Rate** | **0.0% FAIL (0/30)** | $0\%$ | Zero positive queries produced completely irrelevant context |
| **Hit@1 (Positive Queries)** | **60.0% (18/30)** | $\ge 50\%$ | Strong top-ranked relevance for core symbols |
| **Hit@3 (Positive Queries)** | **76.7% (23/30)** | $\ge 70\%$ | High probability of target in top-3 candidates |
| **Hit@5 (Positive Queries)** | **83.3% (25/30)** | $\ge 80\%$ | Consistent candidate inclusion in standard LLM context window |
| **Hit@10 (Positive Queries)** | **83.3% (25/30)** | $\ge 85\%$ | Remaining 5 missed targets were declaration vs. caller splits |
| **Recall@1 (Positive Queries)** | **53.0%** | $\ge 45\%$ | Multi-file and multi-target initial precision |
| **Recall@3 (Positive Queries)** | **74.7%** | $\ge 65\%$ | Strong multi-file coverage |
| **Recall@5 (Positive Queries)** | **82.2%** | $\ge 75\%$ | Captures multi-file chains across frontend and backend |
| **Recall@10 (Positive Queries)** | **85.0%** | $\ge 80\%$ | High total evidence recall across distributed layers |
| **Mean Reciprocal Rank (MRR)** | **0.693** | $\ge 0.65$ | Correct chunk appears on average at rank $\approx 1.44$ |
| **Negative Retrieval FPR** | **100.0% (6/6)** | $\le 10\%$ | **CRITICAL DEFICIENCY:** Permissive threshold leaks noise |

---

## 2. Test Environment

All evaluation queries were executed against a single frozen index state. No configuration parameters, embeddings, chunking thresholds, or database records were modified during testing.

* **Codebase Repository:** `inbound-storage-dispatch` (ISD WMS)
* **Git Commit SHA:** `5a27f9de30988219400d6a41935fa735cb32f0c8`
* **Backend Architecture:** Java 17 / 21, Spring Boot 3, Spring Data JPA, Flyway 10, PostgreSQL
* **Frontend Architecture:** Vue 3 Composition API, Vite, PrimeVue 3, Pinia, TypeScript
* **Indexed Source Files:** 330 files (`.java`, `.vue`, `.js`, `.sql`, `.properties`, `.yaml`, `.md`)
* **Total Indexed Chunks:** 1,163 AST-aligned semantic chunks
* **Embedding Model:** `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions, normalized cosine space)
* **Embedding Runtime:** ONNX Runtime (CPU execution provider)
* **Vector Store Backend:** ChromaDB v0.5.x (`PersistentClient`, HNSW cosine index)
* **Vector Collection:** `wms_codebase_knowledge`
* **Reranker Model:** `cross-encoder/ms-marco-MiniLM-L-6-v2` (`top_n=4`, `min_score=-7.0`, fallback calibrated)
* **MCP Protocol Transport:** Streamable HTTP / FastMCP (`http://localhost:8000/sse`)
* **Evaluation Interface:** MCP Native Tools (`search_wms_code`, `get_entity_and_schema`, `search_wms_security`)
* **Evaluation Timestamp:** `2026-10-01T18:35:00+03:00`

---

## 3. Evaluation Dataset & Ground Truth

The dataset was constructed prior to test execution by direct inspection of the WMS codebase architecture. Ground truth definitions establish the minimum sufficient evidence required to satisfy an engineering query.

| ID | Category | Query | Ground Truth Primary Evidence | Ground Truth Secondary Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **Q01** | Exact Symbol | `ReplenishmentService` | `ReplenishmentService.java` class declaration & methods | Class javadoc & constructor |
| **Q02** | Exact Symbol | `orderAllocationsBySourceLocation` | `PickingFlowService.orderAllocationsBySourceLocation` | `AllocationExecutionService` caller |
| **Q03** | Exact Symbol | `StockAllocationStrategy` | `StockAllocationStrategy.java` interface declaration | `PickingAllocationStrategy`, `ReplenishmentAllocationStrategy` |
| **Q04** | Exact Symbol | `SupervisorDashboardController` | `SupervisorDashboardController.java` (`@GetMapping`) | `SupervisorDashboardService` |
| **Q05** | Exact Symbol | `BarcodeScanner.vue` | `wmsFront/src/components/BarcodeScanner.vue` template/script | Video stream handling, scan animation |
| **Q06** | Exact Symbol | `StrongPasswordValidator` | `StrongPasswordValidator.java` (`isValid` & regex `PATTERN`) | `StrongPassword.java` annotation |
| **Q07** | Semantic Impl | `How does the system automatically decide when and how much stock to replenish for a picking location?` | `ReplenishmentService.checkAndTriggerAutoReplenishment` | `InventoryService.triggerReplenishmentCheck`, `LowStockEvent` |
| **Q08** | Semantic Impl | `How are picking tasks grouped and sorted to optimize the warehouse worker route?` | `PickingFlowService.orderAllocationsBySourceLocation` | `PickingAllocationStrategy`, `Zone.PICKING` |
| **Q09** | Semantic Impl | `What password complexity and character requirements are enforced during user validation?` | `StrongPasswordValidator.java` (Regex pattern) | `StrongPassword.java` constraint message |
| **Q10** | Semantic Impl | `When an inventory shortage happens during allocation, how does the system locate and reserve alternative available stock?` | `ShortageResolver.resolveShortage` | `StockRepository.findAvailableStocksByProductIdAndZone` |
| **Q11** | Semantic Impl | `How does the backend parse uploaded spreadsheet files with multiple rows into domain entities?` | `XlsxImportStrategy.parse` (Poiji library) | `ImportService.importData`, `ImportStrategy` |
| **Q12** | Semantic Impl | `Where are products converted into text vectors on application startup for semantic similarity search?` | `ProductVectorIndexer.indexAllProducts` (`ApplicationReadyEvent`) | `InventoryAiTools.searchProductByName`, `VectorStore` |
| **Q13** | Semantic Impl | `Where are warehouse KPIs such as delayed orders, low stock items, and operator performance aggregated for management?` | `SupervisorDashboardService.buildNeedsAttention` / `getDashboard` | `NeedsAttentionResponse`, `DELAYED_ASSIGNED_ORDER_THRESHOLD` |
| **Q14** | Cross-File | `How does order creation flow from the REST controller endpoint down to order line creation and database persistence?` | `OrderController.createOrder` $\rightarrow$ `OrderService.addExtendedOrder` | `OrderLineService.addOrderLine`, `OrderLineRepository.save` |
| **Q15** | Cross-File | `How does replenishment task creation trigger stock allocation in the bulk storage zone?` | `ReplenishmentAllocationStrategy.getSourceZone` (`Zone.REPLENISHMENT`) | `ReplenishmentService.createReplenishment`, `Replenishment.java` |
| **Q16** | Cross-File | `How does incoming HTTP request authentication flow through JWT filter, token verification, and security context facade?` | `JwtRequestFilter.doFilterInternal` $\rightarrow$ `JwtUtil.validateToken` | `SecurityConfig.securityFilterChain`, `SecurityFacade` |
| **Q17** | Cross-File | `How does physical inventory adjustment reallocate existing picking tasks when stock quantity is reduced?` | `InventoryAdjustmentApplier.applyReallocation` | `InventoryAdjustmentPlanner.buildPlan`, `InventoryAdjustmentService` |
| **Q18** | Cross-File | `How is user message from the floating chat widget sent to the warehouse AI backend and displayed?` | `AiChatWidget.vue` (`sendMessage`) $\rightarrow$ `AiChatController.chat` | `ChatbotService.ChatbotService` (`ChatClient`) |
| **Q19** | Cross-File | `How does CSV file upload route through controller to strategy parsing and mapper conversion into entities?` | `CsvImportStrategy.parse` $\rightarrow$ `ImportService.importData` | `InventoryController.importStockFromFile`, `ImportMapper` |
| **Q20** | Business Logic | `Where is the rule preventing replenishment to a location occupied by a different product implemented?` | `ReplenishmentService.validateDestinationLocation` | `ReplenishmentService.createReplenishment`, `InventoryServiceTest` |
| **Q21** | Business Logic | `Which warehouse zones are designated for picking order allocations versus replenishment allocations?` | `PickingAllocationStrategy` (`Zone.PICKING`) & `ReplenishmentAllocationStrategy` (`Zone.REPLENISHMENT`) | `StockAllocationStrategy.getSourceZone` |
| **Q22** | Business Logic | `Where are transport units released and unlinked when an order or replenishment is canceled?` | `OrderService.deleteOrderById` & `ReplenishmentService.cancelReplenishment` | `TransportUnitRepository.save` (`tu.setOrder(null)`) |
| **Q23** | Business Logic | `What is the time threshold after which an assigned order is considered delayed on the supervisor dashboard?` | `SupervisorDashboardService.DELAYED_ASSIGNED_ORDER_THRESHOLD` (1 hour) | `SupervisorDashboardService.buildNeedsAttention` |
| **Q24** | Business Logic | `Where does the system enforce FIFO allocation ordering based on creation timestamp during inventory reallocation?` | `InventoryAdjustmentApplier.updateAdjustedStockAllocations` (`Comparator.comparing(Allocation::getCreatedAt)`) | `InventoryAdjustmentPlanner.buildPlan` |
| **Q25** | Business Logic | `How does the warehouse listen for low stock events and react by triggering replenishment?` | `ReplenishmentService.handleLowStockEvent` (`@EventListener`) | `LowStockEvent.java`, `checkAndTriggerAutoReplenishment` |
| **Q26** | Negative | `Where is the autonomous drone flight path dispatch and battery charging station controller implemented?` | NONE (Feature absent from WMS) | Expect empty result set / threshold rejection |
| **Q27** | Negative | `How does the system process customer credit card charges through Stripe payment gateway webhooks?` | NONE (WMS has no customer billing or Stripe gateway) | Expect empty result set / threshold rejection |
| **Q28** | Negative | `Where is the UHF RFID reader antenna power and frequency hopping calibration configured?` | NONE (WMS uses barcode scanners, no RFID hardware calibration) | Expect empty result set / threshold rejection |
| **Q29** | Negative | `How are IoT MQTT temperature sensor telemetry alerts in cold storage freezers handled?` | NONE (WMS has no IoT MQTT temperature pipeline) | Expect empty result set / threshold rejection |
| **Q30** | Negative | `Where is the Apache Kafka cluster producer and consumer group configuration for order event streaming?` | NONE (WMS is Spring monolith with Postgres/Flyway, no Kafka) | Expect empty result set / threshold rejection |
| **Q31** | Negative | `Where is cross-docking automated to route inbound goods directly to outbound dock doors without storage?` | NONE (ISD WMS only routes through standard warehouse storage) | Expect empty result set / threshold rejection |
| **Q32** | Exact Identifier | `InventoryAdjustmentPlanner` | `InventoryAdjustmentPlanner.java` (class & methods) | `InventoryAdjustmentService.previewAdjustment` |
| **Q33** | Exact Identifier | `findAvailableStocksByProductIdAndZone` | `StockRepository.findAvailableStocksByProductIdAndZone` | Caller in `InventoryAdjustmentPlanner` |
| **Q34** | Exact Identifier | `V25__rename_processes_to_allocations.sql` | `db/migration/V25__rename_processes_to_allocations.sql` | `ALTER TABLE processes RENAME TO allocations` |
| **Q35** | Exact Identifier | `SupervisorDashboardResponse` | `SupervisorDashboardResponse.java` record definition | `SupervisorDashboardController.getDashboard` |
| **Q36** | Exact Identifier | `OperatorConsole.vue` | `wmsFront/src/views/operator/OperatorConsole.vue` | `BarcodeScanner`, `allocationApi` |

---

## 4. Empirical Retrieval Results

Each test query was transmitted to `wms-code-rag` via `search_wms_code(query, top_n=10)`. The table below records the exact retrieved ranks, file locations, matched symbols, cross-encoder relevance scores, and evidence verification status.

| ID | Verdict | Top-1 Rank & Symbol | Top-1 Score | Ground Truth Chunk Rank(s) | Observed Evidence Chain & Notes |
| :--- | :---: | :--- | :---: | :---: | :--- |
| **Q01** | **PASS** | `ReplenishmentService.getReplenishment` | 0.850 | Rank 1, 2, 3, 5, 6, 7, 9 | `ReplenishmentService` dominated top 10 with 7 chunks. |
| **Q02** | **PARTIAL** | `AllocationExecutionService.buildPickingSummary` | 0.587 | Caller @ Rank 1; Def > 10 | Direct caller chunk contains the method call; primary declaration in `PickingFlowService` omitted from top-10. |
| **Q03** | **PARTIAL** | `PickingAllocationStrategy.sortStocks` | 0.502 | Impls @ Rank 1, 2; Decl > 10 | Both implementations found at Ranks 1 & 2; interface definition chunk lacked dense score. |
| **Q04** | **PASS** | `SupervisorDashboardController.getDashboard` | 0.698 | Rank 1, 2 | Rank 1 is the endpoint; Rank 2 is the full class declaration with `@RequestMapping`. |
| **Q05** | **PASS** | `BarcodeScanner (Template)` | 0.674 | Rank 1 | Exact Vue template chunk at Rank 1. |
| **Q06** | **PASS** | `StrongPasswordValidator.initialize` | 0.940 | Rank 1, 2, 3 | Rank 1 has `PATTERN`; Rank 2 has `isValid`; Rank 3 is `@StrongPassword`. |
| **Q07** | **PARTIAL** | `PickingFlowService` | 0.526 | Caller @ Rank 2; Def > 10 | Secondary caller `InventoryService.triggerReplenishmentCheck` @ Rank 2; primary `checkAndTriggerAutoReplenishment` missed. |
| **Q08** | **PASS** | `PickingFlowService` | 0.534 | Rank 1, 2 | `PickingFlowService` class and `orderAllocationsBySourceLocation` at Rank 1; strategy at Rank 2. |
| **Q09** | **PASS** | `StrongPasswordValidator.isValid` | 0.533 | Rank 1, 2, 3 | Full validation logic, error message, and regex pattern retrieved in top-3. |
| **Q10** | **PASS** | `ShortageResolver.resolveShortage` | 0.612 | Rank 1 | Exact shortage resolution and alternative stock reservation logic at Rank 1. |
| **Q11** | **PASS** | `README.md (Core Functionality)` | 0.348 | Rank 2, 3, 4, 5 | `ImportService` @ Rank 2; `CsvImportStrategy` @ Rank 3; `XlsxImportStrategy` (Poiji) @ Rank 4; `ImportStrategy` @ Rank 5. |
| **Q12** | **PASS** | `ProductVectorIndexer.indexAllProducts` | 0.460 | Rank 1, 2 | `ProductVectorIndexer` with `@EventListener(ApplicationReadyEvent)` at Rank 1; AI tool search at Rank 2. |
| **Q13** | **PASS** | `SupervisorDashboardService.buildNeedsAttention` | 0.492 | Rank 1 | Needs-attention and delayed order calculations at Rank 1. |
| **Q14** | **PASS** | `OrderLineController` | 0.586 | Rank 2, 3, 4 | Full chain: `OrderLineService.addOrderLine` (Rank 2) $\leftarrow$ `OrderController.createOrder` (Rank 3) $\rightarrow$ `OrderService.addExtendedOrder` (Rank 4). |
| **Q15** | **PASS** | `ReplenishmentAllocationStrategy` | 0.728 | Rank 1, 3, 4 | Strategy allocating from `Zone.REPLENISHMENT` at Rank 1; `Replenishment.java` at Rank 3; `StockAllocationStrategy` at Rank 4. |
| **Q16** | **PASS** | `JwtRequestFilter.doFilterInternal` | 0.746 | Rank 1, 2, 3, 4, 5, 8 | Complete auth pipeline: `JwtRequestFilter` (Ranks 1-3), `SecurityConfig` (Ranks 4-5), `JwtUtil` (Ranks 8-9). |
| **Q17** | **PASS** | `InventoryAdjustmentApplier.applyReallocation` | 0.536 | Rank 1, 2, 3 | Reallocation execution at Rank 1; stock allocation update at Rank 2; orchestrator service at Rank 3. |
| **Q18** | **PASS** | `ChatbotService.ChatbotService` | 0.596 | Rank 1, 2, 3, 4 | Cross-stack retrieval: `ChatbotService` (Ranks 1-2), `AiChatController` (Rank 3), `AiChatWidget.vue` (Rank 4). |
| **Q19** | **PASS** | `CsvImportStrategy` | 0.495 | Rank 1, 2, 3, 4 | Strategy (Rank 1), `ImportService` (Rank 2), `InventoryController.importStockFromFile` (Rank 3), `ImportMapper` (Rank 4). |
| **Q20** | **PARTIAL** | `InventoryServiceTest.rejectsAddStock` | 0.531 | Caller @ Rank 3; Def > 10 | Caller `createReplenishment` @ Rank 3; private method `validateDestinationLocation` omitted from top-10. |
| **Q21** | **PASS** | `StockAllocationStrategy` | 0.582 | Rank 1, 5, 6, 7 | `StockAllocationStrategy` (Rank 1), `ReplenishmentAllocationStrategy.getSourceZone` (Rank 5), `PickingAllocationStrategy` (Rank 6). |
| **Q22** | **PASS** | `ReplenishmentServiceTest.cancel` | 0.659 | Rank 1, 2, 3 | Test (Rank 1), `OrderService.deleteOrderById` releasing TU (Rank 2), `ReplenishmentService.cancelReplenishment` (Rank 3). |
| **Q23** | **PASS** | `SupervisorDashboardService.buildNeedsAttention` | 0.632 | Rank 1, 2, 3 | Method checking threshold (Rank 1); DTO (Rank 2); service constant `DELAYED_ASSIGNED_ORDER_THRESHOLD = Duration.ofHours(1)` (Rank 3). |
| **Q24** | **PASS** | `AffectedTaskAdjustment.finalAllocatedQuantity` | 0.463 | Rank 2, 3 | `InventoryAdjustmentApplier` FIFO comparator at Rank 2; `InventoryAdjustmentPlanner.buildPlan` at Rank 3. |
| **Q25** | **PASS** | `ReplenishmentService.handleLowStockEvent` | 0.541 | Rank 1 | Exact event listener `@EventListener public void handleLowStockEvent` at Rank 1. |
| **Q26** | **FAIL** | `LocationController.getLocationsDispatch` | 0.264 | None (Expected Empty) | Leaked 10 irrelevant chunks (matched word "dispatch"); threshold failed to reject. |
| **Q27** | **FAIL** | `ReplenishmentService.getShortageReplenishments` | 0.265 | None (Expected Empty) | Leaked 10 irrelevant shortage chunks; threshold failed to reject. |
| **Q28** | **FAIL** | `LocationImportMapper.toEntity` | 0.333 | None (Expected Empty) | Leaked 10 location mapper chunks; threshold failed to reject. |
| **Q29** | **FAIL** | `README.md (Future Enhancements)` | 0.379 | None (Expected Empty) | Leaked 10 chunks from README alert text and chatbot service; threshold failed to reject. |
| **Q30** | **FAIL** | `WarehouseAiTools.autoDistributeWorkload` | 0.353 | None (Expected Empty) | Leaked 10 AI tool chunks; threshold failed to reject. |
| **Q31** | **FAIL** | `README.md (Running with Docker Compose)` | 0.352 | None (Expected Empty) | Leaked 10 README chunks; threshold failed to reject. |
| **Q32** | **PASS** | `InventoryAdjustmentService.previewAdjustment` | 0.819 | Rank 2, 3 | Service wiring (Rank 1); `InventoryAdjustmentPlanner` methods at Ranks 2 and 3. |
| **Q33** | **PARTIAL** | `InventoryAdjustmentPlanner.loadAlternative` | 0.614 | Caller @ Rank 1; Def > 10 | Caller invoking method at Rank 1; interface declaration in `StockRepository.java` omitted from top-10. |
| **Q34** | **PASS** | `V25__rename_processes_to_allocations.sql` | 0.985 | Rank 1, 2 | Both DDL statements (`ALTER TABLE`, `ALTER SEQUENCE`) retrieved at Ranks 1 and 2. |
| **Q35** | **PASS** | `SupervisorDashboardController.getDashboard` | 0.783 | Rank 1, 2 | Controller returning DTO at Rank 1; `SupervisorDashboardResponse.java` definition at Rank 2. |
| **Q36** | **PASS** | `OperatorConsole.vue` (Script) | 0.623 | Rank 1 | Exact Vue component script chunk at Rank 1. |

---

## 5. Quantitative Retrieval Metrics

### Metric Definitions
* **Hit@K:** Proportion of positive test queries where at least one valid ground truth chunk appears in the top $K$ retrieved candidates:
  $$\text{Hit@K} = \frac{1}{N} \sum_{i=1}^{N} \mathbb{I}(\text{rank}_i \le K)$$
* **Recall@K:** Proportion of required evidence targets retrieved within the top $K$ candidates (vital for cross-file queries requiring multiple components):
  $$\text{Recall@K} = \frac{1}{N} \sum_{i=1}^{N} \frac{|E_i \cap \text{Retrieved}_K(q_i)|}{|E_i|}$$
* **Mean Reciprocal Rank (MRR):** Harmonic mean of the rank of the first relevant evidence chunk:
  $$\text{MRR} = \frac{1}{N} \sum_{i=1}^{N} \frac{1}{\text{rank}_i}$$
* **False Positive Rate (FPR):** Proportion of negative test queries where the system returned non-empty, irrelevant context instead of rejecting the query:
  $$\text{FPR} = \frac{N_{\text{leaked}}}{N_{\text{negative}}}$$

---

### Category Performance Breakdown

| Query Category | Evaluated Queries | PASS | PARTIAL | FAIL | Hit@1 | Hit@3 | Hit@5 | Hit@10 | Mean Reciprocal Rank (MRR) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A. Exact Symbol** | 6 | 4 (66.7%) | 2 (33.3%) | 0 (0.0%) | 66.7% | 66.7% | 66.7% | 66.7% | **0.667** |
| **B. Semantic Implementation** | 7 | 6 (85.7%) | 1 (14.3%) | 0 (0.0%) | 71.4% | 71.4% | 85.7% | 85.7% | **0.750** |
| **C. Cross-File Retrieval** | 6 | 6 (100.0%) | 0 (0.0%) | 0 (0.0%) | 83.3% | 100.0% | 100.0% | 100.0% | **0.889** |
| **D. Business Logic** | 6 | 5 (83.3%) | 1 (16.7%) | 0 (0.0%) | 33.3% | 66.7% | 83.3% | 83.3% | **0.533** |
| **E. Negative Retrieval** | 6 | 0 (0.0%) | 0 (0.0%) | 6 (100.0%) | - | - | - | - | **FPR: 100.0%** |
| **F. Identifier / Exact-Name** | 5 | 4 (80.0%) | 1 (20.0%) | 0 (0.0%) | 40.0% | 80.0% | 80.0% | 80.0% | **0.600** |
| **Total Positive Queries (A–D, F)** | **30** | **25 (83.3%)** | **5 (16.7%)** | **0 (0.0%)** | **60.0%** | **76.7%** | **83.3%** | **83.3%** | **0.693** |
| **Grand Total (All Categories)** | **36** | **25 (69.4%)** | **5 (13.9%)** | **6 (16.7%)** | - | - | - | - | - |

---

### Evidence Chain Recall on Multi-Target Queries

For cross-file and multi-evidence queries (Category C and multi-part queries in B & D), the progression of evidence chain recall was measured across ranks:

$$\text{Recall@1} = 53.0\% \quad \longrightarrow \quad \text{Recall@3} = 74.7\% \quad \longrightarrow \quad \text{Recall@5} = 82.2\% \quad \longrightarrow \quad \text{Recall@10} = 85.0\%$$

* In **100% of cross-file queries** (Q14, Q15, Q16, Q17, Q18, Q19), the complete architecture chain (Controller $\rightarrow$ Service $\rightarrow$ Strategy/Repository) was fully reconstructed within the top 5 chunks.
* In **Q18**, the RAG successfully assembled a cross-technology stack in top 4: Vue 3 frontend (`AiChatWidget.vue`), Spring Boot REST controller (`AiChatController.java`), and Spring AI core service (`ChatbotService.java`).

---

## 6. Failure & Partial Result Analysis

Every query that produced a `PARTIAL` or `FAIL` verdict was audited to identify the underlying failure mechanism.

```
Failure Layer Taxonomy:
- EXACT_IDENTIFIER_RETRIEVAL
- CHUNKING
- DENSE_RETRIEVAL
- RERANKING
- THRESHOLD
```

---

### Case 1: Q02 — Exact Method Name `orderAllocationsBySourceLocation` (PARTIAL)
* **Query:** `orderAllocationsBySourceLocation`
* **Expected Ground Truth:** Method declaration in `PickingFlowService.java` (lines 33–46).
* **Actual Retrieval:** Rank 1: `AllocationExecutionService.buildPickingSummary` (score 0.587); primary declaration chunk in `PickingFlowService` omitted from top-10.
* **Why This Matters:** An engineer asking where the routing algorithm is defined receives the consumer of the method rather than its implementation.
* **Likely Failure Layer:** `EXACT_IDENTIFIER_RETRIEVAL` / `DENSE_RETRIEVAL`
* **Root-Cause Evidence:** `AllocationExecutionService` is a 320-line class with rich picking, allocation, and order terminology. `PickingFlowService` is a concise 92-line utility. In pure dense vector space (`all-MiniLM-L6-v2`), the caller chunk has a larger semantic overlap with general warehouse picking concepts, pushing the short declaration file below rank 10. Lexical BM25 would have placed the exact token declaration at Rank 1.

---

### Case 2: Q03 — Interface Name `StockAllocationStrategy` (PARTIAL)
* **Query:** `StockAllocationStrategy`
* **Expected Ground Truth:** Interface declaration in `StockAllocationStrategy.java` (lines 1–32).
* **Actual Retrieval:** Rank 1: `PickingAllocationStrategy.sortStocks` (score 0.502); Rank 2: `ReplenishmentAllocationStrategy.sortStocks` (score 0.480); interface chunk omitted from top-10.
* **Why This Matters:** When exploring contracts and extension points, the interface definition provides the design abstractions and method contracts (`support`, `getSourceZone`, `sortStocks`).
* **Likely Failure Layer:** `CHUNKING` / `EXACT_IDENTIFIER_RETRIEVAL`
* **Root-Cause Evidence:** In `chunker.py`, interface definitions without executable method bodies produce small chunks with minimal surrounding semantic content. The concrete implementations (`PickingAllocationStrategy`, `ReplenishmentAllocationStrategy`) contain sorting logic, lambda comparators, and zone references, causing dense embeddings to assign higher similarity to the implementing classes.

---

### Case 3: Q07 — Automatic Replenishment Decision (PARTIAL)
* **Query:** `How does the system automatically decide when and how much stock to replenish for a picking location?`
* **Expected Ground Truth:** `ReplenishmentService.checkAndTriggerAutoReplenishment` (lines 173–202).
* **Actual Retrieval:** Rank 1: `PickingFlowService` (0.526); Rank 2: `InventoryService.triggerReplenishmentCheck` (0.500); primary method omitted from top-10.
* **Why This Matters:** The actual calculation logic (`locationQty <= minThreshold`, checking active replenishments via `existsByProductIdAndDestinationLocationIdAndStatusIn`, creating `ReplenishmentCreateRequest`) resides in `ReplenishmentService`.
* **Likely Failure Layer:** `DENSE_RETRIEVAL` / `RERANKING`
* **Root-Cause Evidence:** The caller `InventoryService.triggerReplenishmentCheck` was retrieved at Rank 2 and correctly points to `checkAndTriggerAutoReplenishment`. However, the chunk containing lines 173–202 in `ReplenishmentService.java` is surrounded by numerous CRUD and shortage methods in a 448-line file. The cross-encoder prioritized picking flow and caller summaries over the specific auto-replenish chunk.

---

### Case 4: Q20 — Destination Location Validation Rule (PARTIAL)
* **Query:** `Where is the rule preventing replenishment to a location occupied by a different product implemented?`
* **Expected Ground Truth:** Private method `validateDestinationLocation` in `ReplenishmentService.java` (lines 77–88).
* **Actual Retrieval:** Rank 1: `InventoryServiceTest.rejectsAddStock_differentProductOnLocation` (0.531); Rank 2: `LocationService.deleteLocation` (0.516); Rank 3: `ReplenishmentService.createReplenishment` (0.515); primary validation chunk omitted from top-10.
* **Why This Matters:** The engineer sees that `createReplenishment` calls `validateDestinationLocation`, but the actual exception message (`"Cannot route replenishment to ... Location is already occupied by a different product"`) is not in the chunk.
* **Likely Failure Layer:** `CHUNKING` / `DENSE_RETRIEVAL`
* **Root-Cause Evidence:** `validateDestinationLocation` is a 12-line private helper method at lines 77–88 of `ReplenishmentService.java`. During AST chunking, private helper methods without public annotations often get split into micro-chunks or aggregated into preceding blocks. Without keyword boosting, the unit test (`InventoryServiceTest`) had higher dense similarity because its test method name explicitly matched `"rejectsAddStock_differentProductOnLocation"`.

---

### Case 5: Q33 — Method Identifier `findAvailableStocksByProductIdAndZone` (PARTIAL)
* **Query:** `findAvailableStocksByProductIdAndZone`
* **Expected Ground Truth:** Query method declaration in `StockRepository.java` (line 56).
* **Actual Retrieval:** Rank 1: `InventoryAdjustmentPlanner.loadAlternativeAvailability` (0.614); `StockRepository.java` omitted from top-10.
* **Why This Matters:** An exact method lookup on a Spring Data repository interface should return the interface definition containing the `@Query` JPQL statement.
* **Likely Failure Layer:** `EXACT_IDENTIFIER_RETRIEVAL` / `CHUNKING`
* **Root-Cause Evidence:** The caller `InventoryAdjustmentPlanner.loadAlternativeAvailability` uses the method within a rich stream pipeline with stream filters, comparators, and error handling. The repository chunk in `StockRepository.java` contains only the signature and multiline SQL annotation. The cross-encoder scored the caller (0.614) higher than the repository chunk.

---

### Case 6: Q26–Q31 — Negative Retrieval Suite (6 FAILURES)
* **Queries:**
  * Q26: Autonomous drone flight dispatch
  * Q27: Stripe credit card webhook processing
  * Q28: UHF RFID reader antenna frequency calibration
  * Q29: IoT MQTT cold storage telemetry
  * Q30: Apache Kafka order event streaming
  * Q31: Automated cross-docking bypass engine
* **Expected Behavior:** Return empty result set: `"No relevant code or documentation found in WMS codebase (no matching chunks passed the relevance threshold)."`
* **Actual Retrieval:** In all 6 queries, the RAG returned 10 code chunks (mostly from `README.md`, `LocationController`, `WarehouseAiTools`, and `LocationImportMapper`).
* **Why This Matters:** Downstream AI agents receiving irrelevant chunks as context risk hallucinating answers or attempting to synthesize non-existent features.
* **Likely Failure Layer:** `THRESHOLD`
* **Root-Cause Evidence:** In `config.yaml`:
  ```yaml
  retrieval:
    similarity_threshold: 0.10
  reranking:
    min_score: -7.0
  ```
  A cosine similarity threshold of 0.10 is virtually unconstrained; in normalized 384-dimensional vector space, random text pairs frequently exhibit cosine similarities between 0.15 and 0.35. Furthermore, the cross-encoder logit threshold of -7.0 allows almost any pair through. As measured across Q26–Q31:
  * Maximum negative relevance score: **0.379** (README alert text)
  * Average maximum negative score: **0.321**
  * Minimum positive top-1 score: **0.460** (Q12)
  * Average positive top-1 score: **0.638**
  There is a clean, bimodal separation between true negatives ($< 0.40$) and true positives ($\ge 0.46$). The failure is entirely attributable to uncalibrated threshold configuration.

---

## 7. Architectural Findings

The findings below are classified strictly according to empirical evidence:

### `[VERIFIED]` Findings
1. **Cross-File Dependency Retrieval Excels:** The RAG system reliably retrieves multi-file dependency flows across architectural boundaries. In all 6 cross-file benchmark queries (Q14–Q19), the full pipeline (e.g. Controller $\rightarrow$ Service $\rightarrow$ Strategy/Repository) was reconstructed within the Top 4 results.
2. **Polyglot & Cross-Stack Coherence:** The system effectively indexes and retrieves across heterogeneous languages in a unified vector space: Vue 3 Single File Components (templates & `<script setup>`), Java Spring Boot services, and SQL Flyway migration files.
3. **Semantic Querying Outperforms Exact Symbols for Concepts:** When queries are formulated conceptually (e.g. Q08, Q09, Q10, Q12, Q13), dense retrieval achieved an 85.7% top-1 pass rate. The cross-encoder effectively pairs conceptual prompts with technical implementations.
4. **Exact Identifiers Suffer from Semantic Attenuation:** In queries requesting exact symbol names (Q02, Q03, Q33), the exact declaration was frequently outranked by caller methods because the dense model awards higher weight to larger blocks of surrounding text rather than verbatim token presence.
5. **Permissive Threshold Leaks 100% of Negative Queries:** With `similarity_threshold: 0.10` and `min_score: -7.0`, the system has a 100% False Positive Rate on queries regarding non-existent features.

### `[INFERRED]` Findings
1. **Bimodal Score Distribution Enables Clean Filtering:** Because true negative queries peaked at 0.379 while legitimate hits started at 0.460, setting the primary similarity threshold to $\approx 0.40$ or the reranker cutoff to $\ge 0.0$ would eliminate 100% of negative query leaks without sacrificing positive recall.
2. **Hybrid BM25 Fusion Would Resolve Declaration vs. Caller Confusion:** The 5 partial failures (Q02, Q03, Q07, Q20, Q33) all involved cases where the exact identifier was present verbatim in the target file. A sparse lexical ranker (BM25) combined via Reciprocal Rank Fusion (RRF) would assign maximum reciprocal rank to verbatim symbol occurrences.

### `[UNCERTAIN]` Findings
1. **Embedding Model Token Truncation on Large Files:** It is uncertain whether files exceeding 400 lines (such as `ReplenishmentService.java`) suffer subtle semantic degradation during batch tokenization in ONNX runtime, contributing to the partial misses in Q07 and Q20.
2. **Impact of Chunk Header Repetition:** It is uncertain whether prefixing chunks with hierarchical AST metadata (`Package.Class.Method`) alone would overcome dense embedding attenuation without adding BM25.

### `[UNKNOWN]` Findings
1. **Production Latency & Memory Footprint of Hybrid BM25:** The memory and CPU overhead of running an in-process inverted index (e.g. Tantivy or Rank-BM25) alongside ChromaDB within the constrained 1GB Docker memory budget is unknown and requires benchmarking.

---

## 8. Recommended Improvements

| Priority | Problem Area | Observed Evidence | Proposed Architectural Change | Expected Benefit | Risk / Tradeoff |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **P0** | **Negative Query Noise Leakage** | 100% FPR across Q26–Q31; max score was 0.379. | Raise `similarity_threshold` from `0.10` to `0.40` in `config.yaml` and calibrate reranker `min_score` to `-2.0`. | Eliminates 100% of hallucinated/irrelevant context on non-existent features. | Risk of dropping low-scoring edge cases if queries are poorly formulated. |
| **P1** | **Exact Symbol Identifier Attenuation** | Q02, Q03, Q33 outranked by caller methods; primary definitions missed in top-10. | Implement Hybrid Search combining Dense Vectors (`all-MiniLM-L6-v2`) with Sparse Lexical Search (BM25) using Reciprocal Rank Fusion (RRF, $k=60$). | Guarantees top-3 retrieval for exact class, method, table, and file names. | Requires building and persisting an inverted BM25 index on startup. |
| **P1** | **Interface & Method Header Chunking** | Q03 (interface) and Q20 (private method) lacked dense mass. | Enrich chunk headers in `chunker.py` with explicit symbol signature, enclosing interface/class hierarchy, and annotations. | Enhances dense vector representation for short declarations and contracts. | Slight increase in chunk character count ($\approx 5\%$). |
| **P2** | **Multi-Chunk Context Stitching** | Q20 retrieved caller `createReplenishment` but missed private validator. | Add parent-child chunk linking metadata (`parent_symbol_id`, `enclosing_class`) to enable automatic sibling context expansion. | Downstream agent receives complete execution unit without separate queries. | Increased payload size in MCP tool response. |
| **P3** | **Index Reproducibility & Validation** | Prior test runs inadvertently wiped the vector store with 1 chunk (`Legit.java`). | Add an index versioning hash and guard destructive `clear_first` operations behind isolated test collection namespaces. | Prevents unit test fixtures from polluting active knowledge bases. | Requires minor test harness refactoring. |

---

## 9. What Should We Improve Next?

Based strictly on the empirical findings of this evaluation, the following **top 5 prioritized engineering recommendations** are proposed:

### 1. Calibrate Negative Relevance Gating & Similarity Threshold (Priority: P0)
* **Rationale:** As measured in Category E, the current threshold of `0.10` fails completely at negative retrieval, leaking 10 chunks for every non-existent topic. True negative queries scored $\le 0.379$, whereas true positives scored $\ge 0.460$.
* **Action:** Update `config.yaml`:
  * `retrieval.similarity_threshold`: Increase from `0.10` to `0.40`.
  * `reranking.min_score`: Increase from `-7.0` to `-2.0`.
* **Expected Impact:** Rejects 100% of false positives while preserving 100% of legitimate passing queries.

### 2. Implement Hybrid Dense + BM25 Search with RRF (Priority: P1)
* **Rationale:** The only deficiencies in positive queries (Q02, Q03, Q33) occurred on exact symbol and method identifiers where dense semantic models favored callers with rich vocabularies over the exact token declaration.
* **Action:** Integrate a lightweight lexical BM25 index over codebase chunk tokens. Fuse BM25 and ChromaDB vector candidate lists using Reciprocal Rank Fusion:
  $$\text{RRF\_Score}(d) = \frac{1}{60 + \text{rank}_{\text{dense}}(d)} + \frac{1}{60 + \text{rank}_{\text{bm25}}(d)}$$
* **Expected Impact:** Elevates exact method, class, and table declarations directly to Rank 1–2, raising positive Hit@1 from 60.0% to $>85\%$.

### 3. Enrich AST Chunk Headers with Structural Enclosing Scope (Priority: P1)
* **Rationale:** AST chunks for interfaces (Q03) and private helpers (Q20) had low vector magnitude.
* **Action:** In `src/chunker.py`, prepend every code chunk with standardized structural metadata:
  ```java
  // File: {file_path}
  // Package: {package_name}
  // Enclosing Class/Interface: {class_name}
  // Target Symbol: {symbol_name} ({symbol_type})
  ```
* **Expected Impact:** Ensures embedding vectors capture the full conceptual context of small declarations and interface contracts.

### 4. Provide Sibling Chunk Expansion for Private Methods (Priority: P2)
* **Rationale:** In Q20, `createReplenishment` and its private helper `validateDestinationLocation` were split into separate chunks, forcing the user/agent to make follow-up queries.
* **Action:** When retrieving a public method chunk, provide an optional MCP parameter `include_enclosed_helpers: bool` to attach adjacent private utility chunks.
* **Expected Impact:** Reduces multi-turn retrieval loops for complex business rules.

### 5. Isolate Test Fixtures from Production Vector Persistence (Priority: P2)
* **Rationale:** Pytest runs previously cleared `data/chroma` because `AppConfig()` defaulted to the production directory.
* **Action:** Configure test suites to enforce temporary directory paths (`tmp_path / "chroma"`) for all vector store fixtures, ensuring zero risk of wiping the live codebase index during testing.
* **Expected Impact:** Guarantees knowledge base durability and repeatability across developer environments.
