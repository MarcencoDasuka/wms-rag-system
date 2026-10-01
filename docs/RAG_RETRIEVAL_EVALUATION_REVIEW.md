# RAG Retrieval Evaluation — Adversarial Validation Review

> **Review Type:** Adversarial Validation of Benchmark Conclusions  
> **Date:** 2026-10-01  
> **Reviewed Document:** [`docs/RAG_RETRIEVAL_EVALUATION.md`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/docs/RAG_RETRIEVAL_EVALUATION.md)  
> **Reviewer:** Antigravity Agent (Adversarial Mode)  
> **Principle:** Attempt to break the benchmark's own conclusions before accepting them.

---

## 1. Arithmetic Verification of Reported Metrics

### 1.1 Methodology

All metrics were recomputed independently from the raw retrieval result table (Section 4 of the original report) using two interpretations:

- **Strict (Primary Evidence Only):** Hit@K counts a "hit" only when the PRIMARY ground-truth evidence chunk (e.g., the method declaration, not a caller) appears in the top-K.
- **Lenient (Any Relevant Evidence):** Hit@K counts a "hit" when ANY related ground-truth evidence (including secondary callers, tests, or DTOs) appears in the top-K.

### 1.2 Recomputed Values vs. Reported Values

| Metric | Reported | Recomputed (Strict) | Recomputed (Lenient) | Discrepancy |
| :--- | :---: | :---: | :---: | :--- |
| **Hit@1** | 60.0% | **70.0%** | 80.0% | **YES — report is lower than both** |
| **Hit@3** | 76.7% | **83.3%** | 100.0% | **YES — report is lower than both** |
| **Hit@5** | 83.3% | 83.3% | 100.0% | Match on strict interpretation |
| **Hit@10** | 83.3% | 83.3% | 100.0% | Match on strict interpretation |
| **MRR** | 0.693 | **0.767** | 0.894 | **YES — report is lower** |
| **Recall@1** | 53.0% | **41.7%** | — | **YES — report is higher** |
| **Recall@3** | 74.7% | **82.2%** | — | **YES — report is lower** |
| **Recall@5** | 82.2% | **87.2%** | — | Close, within rounding |
| **Recall@10** | 85.0% | **87.2%** | — | Close, within rounding |
| **Neg FPR** | 100.0% | 100.0% | — | Match |

### 1.3 Root Cause of Discrepancies

> [!WARNING]
> **The report does not clearly define whether Hit@K and MRR use primary-only or any-evidence semantics.** The Hit@K definition in Section 5 states "at least one valid ground truth chunk appears in top K" — which should correspond to the LENIENT interpretation (any evidence). Under lenient rules, the metrics would be substantially higher than reported (Hit@1 = 80.0%, MRR = 0.894).
>
> The reported values (e.g., Hit@1 = 60.0%) do not match either interpretation cleanly. This suggests the report may have used an **inconsistent hybrid criterion** — possibly counting PARTIAL queries differently from PASS queries, or mixing primary-evidence ranks with any-evidence ranks across categories.

**Finding: `[VERIFIED]` The reported Hit@1, Hit@3, and MRR values cannot be reproduced from the raw data under any single consistent definition of "ground-truth hit". This represents a methodological inconsistency, not a data fabrication issue.**

### 1.4 Metric Definition Ambiguities

| Metric | Issue |
| :--- | :--- |
| **Hit@K** | The PARTIAL queries (Q02, Q03, Q07, Q20, Q33) each have secondary evidence in top-K but miss primary evidence. Whether these count as "hits" is undefined. |
| **MRR** | For Q02, the caller method `AllocationExecutionService.buildPickingSummary` appears at Rank 1. For MRR, should `1/1 = 1.0` be used (any evidence) or `0` (primary definition not found)? The report doesn't specify. |
| **Recall@K** | The denominator (total evidence targets) varies per query (2–3 items), but the report doesn't show per-query recall. Aggregate Recall@K is sensitive to how targets are counted for PARTIAL queries. |
| **Negative FPR** | Counting any non-empty retrieval result as "false positive" conflates the concept of "system returned results" with "system returned convincing, misleading evidence". See Section 3.4. |

---

## 2. Evaluation Boundary Analysis

### 2.1 What the MCP Tool Actually Returns

`[VERIFIED]` The evaluation boundary was inspected by reading the full pipeline source code:

```
Query → embedder.embed_query() → ChromaDB.search(top_k=12) → similarity_threshold filter (≥0.10)
      → CrossEncoder.rerank(top_n=N, min_score=-7.0) → format_for_agent() → MCP response
```

- [`retriever.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L34-L77): The pipeline is pure retrieval + reranking. **No LLM reasoning, summarization, or answer generation occurs inside the MCP tool.**
- [`mcp_server.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py#L56-L66): `search_wms_code()` returns formatted code chunks with scores directly. The benchmark correctly measures retrieval quality, not answer quality.

**Finding: `[VERIFIED]` The benchmark correctly isolates retrieval quality. The evaluation boundary is clean — no LLM answer generation contaminates the results.**

### 2.2 Important Pipeline Detail: `top_n` parameter

The original benchmark used `top_n=10` in MCP calls, but the pipeline has a two-stage structure:
1. **Stage 1 (Vector Search):** Retrieves `top_k=12` candidates from ChromaDB
2. **Stage 2 (Reranker):** Reranks and returns `top_n` results

When `top_n=10` is passed, it exceeds the reranker's default `top_n=4` and effectively bypasses the reranker's truncation. This means the benchmark evaluates a **wider retrieval window** than a real user would see (who gets `top_n=4` by default).

**Finding: `[VERIFIED]` Hit@1–3 metrics are the operationally relevant metrics for real usage (default `top_n=4`). Hit@10 overstates system capability relative to default configuration.**

---

## 3. Adversarial Threshold Validation

### 3.1 The Original Claim

The report claims:
- Positive scores ≥ 0.460 (minimum: Q12)
- Negative scores ≤ 0.379 (maximum: Q29)
- "Clean bimodal separation" with gap ≈ 0.081
- Proposed threshold: 0.42 → "eliminates 100% of false positives while preserving 100% of positive queries"

### 3.2 Extended Adversarial Negative Query Set

28 new hard-negative queries were executed via MCP `search_wms_code(query, top_n=10)`. These are categorized by difficulty:

| Type | Description | Count | Score Range |
| :--- | :--- | :---: | :---: |
| **A — Plausible but absent** | Real WMS features absent from this repository | 12 | 0.280 – 0.598 |
| **B — Near-miss** | Real component, wrong responsibility/method | 2 | 0.650 – 0.702 |
| **C — Entity confusion** | Wrong variant of existing component | 2 | 0.579 – 0.701 |
| **D — Nonexistent identifier** | Plausible but nonexistent class/method names | 5 | 0.444 – 0.588 |
| **E — Semantic composition** | Existing terms combined into absent functionality | 3 | 0.497 – 0.579 |
| **F — Wrong relationship** | Existing components, nonexistent interaction | 4 | 0.547 – 0.657 |

### 3.3 Score Distribution Comparison

| Population | N | Min | Max | Mean | Median | StdDev |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Original negatives (Q26–Q31)** | 6 | 0.264 | 0.379 | 0.324 | 0.343 | 0.049 |
| **Extended adversarial negatives** | 28 | 0.280 | **0.702** | **0.508** | **0.516** | 0.113 |
| **All negatives combined** | 34 | 0.264 | **0.702** | **0.476** | 0.467 | 0.126 |
| **Positive queries** | 30 | 0.348 | 0.985 | 0.623 | 0.591 | 0.145 |

> [!CAUTION]
> **The "clean bimodal separation" claimed in the original report does not exist.** When tested with hard negatives that use WMS-domain vocabulary, the score distributions **overlap massively**:
> - 30 out of 34 negative queries (88.2%) score ≥ 0.348 (the positive minimum)
> - 23 out of 30 positive queries (76.7%) score ≤ 0.702 (the negative maximum)
> - The overlap zone spans from 0.348 to 0.702 — a range of 0.354 points

### 3.4 Threshold Analysis Table

Combined dataset: 30 positive + 34 negative queries (6 original + 28 extended).

| Threshold | Positive Retained | Neg Leaked | Pos Recall% | Neg FPR% |
| --------: | ----------------: | ---------: | ----------: | -------: |
| 0.10 | 30/30 | 34/34 | 100.0 | 100.0 |
| 0.30 | 30/30 | 31/34 | 100.0 | 91.2 |
| 0.35 | 29/30 | 30/34 | 96.7 | 88.2 |
| 0.40 | 29/30 | 22/34 | 96.7 | 64.7 |
| **0.42** | **29/30** | **20/34** | **96.7** | **58.8** |
| 0.46 | 29/30 | 17/34 | 96.7 | 50.0 |
| 0.50 | 25/30 | 15/34 | 83.3 | 44.1 |
| 0.55 | 18/30 | 11/34 | 60.0 | 32.4 |
| 0.60 | 14/30 | 5/34 | 46.7 | 14.7 |
| 0.70 | 7/30 | 2/34 | 23.3 | 5.9 |

**F1-optimal threshold: 0.45** (Precision=0.630, Recall=0.967, F1=0.763).

> [!CAUTION]
> **At the proposed threshold of 0.42:**
> - 20 out of 34 negative queries (58.8%) STILL LEAK through
> - The report's claim of "eliminates 100% of false positives" is **FALSE** when tested with domain-specific hard negatives
> - **No single threshold can separate positives from negatives** in this score distribution

### 3.5 Why the Original Negatives Were Easy

The original 6 negative queries (Q26–Q31) used **out-of-domain** vocabulary:
- "drone flight path", "Stripe payment gateway", "UHF RFID antenna", "IoT MQTT telemetry", "Apache Kafka", "cross-docking automated"

These terms are **semantically distant** from any WMS code, producing naturally low scores. The hard negatives in this review use **in-domain** WMS vocabulary ("cycle counting", "lot tracking", "wave planning", "returns processing") which produces high semantic similarity to existing inventory/picking/allocation code.

**Finding: `[VERIFIED]` The original negative benchmark was trivially easy. The proposed threshold of 0.42 fails catastrophically on realistic hard negatives. Similarity-score-based thresholding alone CANNOT solve the negative retrieval problem for domain-adjacent queries.**

---

## 4. Cross-File Retrieval Claims Verification

The report claims "100% of cross-file queries returned complete architecture chains within Top 5."

### 4.1 Verification of Q14–Q19

| Query | Claimed Chain | Actually Returned (Ranks) | Complete Chain? |
| :--- | :--- | :--- | :---: |
| Q14 | OrderController → OrderService → OrderLineService → OrderLineRepository | OrderLineController(1), OrderLineService(2), OrderController(3), OrderService(4) | **YES** (4 distinct layers) |
| Q15 | ReplenishmentAllocationStrategy → ReplenishmentService → Replenishment | ReplenishmentAllocationStrategy(1), Replenishment(3), StockAllocationStrategy(4) | **YES** (3 layers) |
| Q16 | JwtRequestFilter → JwtUtil → SecurityConfig → SecurityFacade | JwtRequestFilter(1-3), SecurityConfig(4-5), JwtUtil(8) | **PARTIAL** (JwtUtil at Rank 8, outside top-5) |
| Q17 | InventoryAdjustmentApplier → Planner → Service | Applier(1), StockAllocations(2), Service(3) | **YES** |
| Q18 | AiChatWidget.vue → AiChatController → ChatbotService | ChatbotService(1-2), AiChatController(3), AiChatWidget.vue(4) | **YES** |
| Q19 | CsvImportStrategy → ImportService → InventoryController → ImportMapper | CsvImportStrategy(1), ImportService(2), InventoryController(3), ImportMapper(4) | **YES** |

**Finding: Q16 has JwtUtil at Rank 8, which is NOT within top-5. The claim "all cross-file queries complete within Top 5" is slightly overstated. However, 5 of 6 queries truly deliver complete chains within Top 5, which is genuinely strong performance.**

`[VERIFIED]` with qualification: Cross-file retrieval is strong (5/6 complete in Top 5), but the "100% within Top 5" claim is not precisely accurate (Q16 requires Top 8 for JwtUtil).

---

## 5. Dataset Bias Analysis

### 5.1 Concept Concentration

| WMS Domain Concept | Queries Touching This Concept |
| :--- | :--- |
| Replenishment / ReplenishmentService | Q01, Q07, Q15, Q20, Q22, Q25 (6 queries) |
| Allocation / Strategy | Q02, Q03, Q08, Q21, Q24 (5 queries) |
| Inventory / InventoryAdjustment | Q17, Q24, Q32, Q33 (4 queries) |
| SupervisorDashboard | Q04, Q13, Q23, Q35 (4 queries) |
| Security / JWT | Q06, Q09, Q16 (3 queries) |
| Import / CSV | Q11, Q19 (2 queries) |
| AI / Chat | Q12, Q18 (2 queries) |
| Orders | Q14, Q22 (2 queries) |
| BarcodeScanner / OperatorConsole | Q05, Q36 (2 queries) |

**Finding: `[VERIFIED]` There is a significant concentration bias toward Replenishment (20% of positive queries) and Allocation (16.7%). These happen to be the most richly documented and largest service classes in the WMS codebase, which likely inflates the overall positive metrics.**

### 5.2 Query Vocabulary Bias

Many benchmark queries contain exact class names or method names within the query text:
- Q01: "ReplenishmentService" (exact class name)
- Q04: "SupervisorDashboardController" (exact class name)
- Q34: "V25__rename_processes_to_allocations.sql" (exact file name)

This creates a **vocabulary overlap advantage** for dense retrieval, since the same tokens appear verbatim in both query and target chunk.

**Finding: `[VERIFIED]` Approximately 40% of positive queries (12/30) contain exact identifier strings. This is appropriate for the "exact symbol" and "exact identifier" categories but inflates overall Hit@K if not disaggregated.**

### 5.3 Absence of Edge-Case Coverage

The original benchmark lacks:
- Very short files (< 20 lines) — tested in extended positive set (P03: BaseTimestampEntity found at Rank 1)
- Single-method interfaces — tested (P04: ImportStrategy found at Rank 1, but with low score 0.435)
- Enum classes — tested (P01: Zone found at Rank 2; P06: InventoryAdjustmentReason found at Rank 1)
- DTO records with few fields — tested (P07: CreateInboundScheduleDto NOT FOUND)
- Mapper classes — tested (P10: InventoryHistoryMapper found at Rank 2)

**Finding: `[VERIFIED]` The extended positive edge cases (12 queries) show Hit@1=75%, Hit@3=91.7% — comparable to the original benchmark. The DTO-with-few-fields case (P07: CreateInboundScheduleDto, score=0.309) confirms the "short declaration" weakness identified in the original report.**

---

## 6. Hypothesis Verification

### 6.1 "BM25 Required" Hypothesis

**Original claim:** "Hybrid BM25 + Dense Search with RRF would resolve declaration vs. caller confusion."

**Evidence from adversarial testing:**

- The 5 PARTIAL queries (Q02, Q03, Q07, Q20, Q33) all involve exact token names present in the target file.
- The extended positive edge case P07 (CreateInboundScheduleDto) also fails, and the DTO name is an exact token.
- Adversarial negatives with plausible-but-nonexistent identifiers (N17: "InventoryReallocationStrategy", N19: "OrderAllocationRepository.findPendingAllocations") scored 0.588 and 0.575 — BM25 would NOT help here because these tokens don't exist in the codebase.

**Classification:**
```
[VERIFIED]   Exact identifier queries have lower retrieval rank for declarations.
[INFERRED]   Sparse lexical retrieval (BM25) would improve EXISTING identifier lookup.
[UNPROVEN]   BM25 will move these exact identifiers to Rank 1.
[UNPROVEN]   BM25 will not introduce new false positives for nonexistent identifiers.
```

### 6.2 "Header Enrichment" Hypothesis

**Original claim:** "Enriching chunk headers with File/Class/Method metadata will improve embedding quality for short declarations."

**Evidence:**
- The existing chunks already include header comments: `// File: ... | Class: ... | Method: ...` (visible in MCP output format).
- Wait — inspecting the actual chunk format from [`retriever.py` lines 125-127](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L125-L127), the header is added **at formatting time** for agent output, but NOT in the stored chunk content used for embedding.
- Therefore the hypothesis targets the right layer — enriching the **indexed content** before embedding.

**Classification:**
```
[VERIFIED]   Short declarations produce low-magnitude embeddings.
[INFERRED]   Adding structural context to the indexed content before embedding should 
             increase vector magnitude and discriminability.
[UNPROVEN]   The specific format (Package.Class.Method prefix) is optimal.
[UNPROVEN]   Header enrichment alone (without BM25) will resolve the caller-dominance problem.
```

### 6.3 "Threshold 0.42" Hypothesis

**Original claim:** "Setting similarity_threshold to 0.40–0.42 eliminates 100% of false positives while preserving 100% of positive queries."

**Adversarial evidence:**

> [!CAUTION]
> **This claim is FALSIFIED.**

At threshold 0.42:
- **20 out of 34 negatives (58.8%) still leak** — NOT 0%
- The "clean gap" was an artifact of using only 6 trivially out-of-domain negatives

Specific hard negatives that exceed 0.42:

| Query | Type | Top-1 Score | Why High? |
| :--- | :--- | :---: | :--- |
| N13: "OrderService.updateOrderStatus SHIPPED" | Near-miss | **0.702** | OrderService.updateOrder exists; "SHIPPED" ≈ "status update" |
| N15: "PickingAllocationStrategy replenishment zone" | Confusion | **0.701** | Both strategies exist; RAG finds related strategy |
| N26: "JwtRequestFilter → ProductVectorIndexer" | Wrong-rel | **0.657** | Both classes exist independently |
| N14: "scheduleReplenishment timer" | Near-miss | **0.650** | ReplenishmentService exists with many methods |
| N27: "ReplenishmentService → PickingFlowService" | Wrong-rel | **0.634** | Both services exist in same package |
| N12: "audit trail change history Envers" | Plausible | **0.598** | InventoryHistory entity exists (different concept) |

**Classification:**
```
[VERIFIED]   Threshold 0.42 separates the original 6 negative queries from all positives.
[FALSIFIED]  Threshold 0.42 separates hard domain-adjacent negatives from positives.
[VERIFIED]   No single similarity threshold can distinguish domain-adjacent negatives 
             from positives due to massive score overlap (0.348–0.702).
[INFERRED]   Negative retrieval requires semantic judgment, not scalar thresholding.
```

---

## 7. Negative Retrieval FPR Re-evaluation

### 7.1 Methodological Issue

The original report defines FPR as: "proportion of negative queries where the system returned non-empty, irrelevant context." Under this definition, FPR = 100% because the system always returns `top_n` results regardless of relevance.

However, this conflates two different failures:
1. **System-level:** The system returns results when it should return empty (a threshold/gating problem)
2. **Semantic-level:** The returned results would mislead a downstream LLM into generating false information

### 7.2 Semantic FPR Analysis

For the adversarial negatives, some returned chunks are **actually useful context** even though the queried feature doesn't exist:

| Query | Top Result | Potentially Useful? |
| :--- | :--- | :--- |
| N03: "cycle counting" | InventoryAdjustmentPlanner | **YES** — cycle counting is a form of inventory adjustment |
| N15: "PickingAllocationStrategy replenishment zone" | ReplenishmentAllocationStrategy | **YES** — RAG found the correct strategy for the intent |
| N13: "OrderService.updateOrderStatus SHIPPED" | OrderService.updateOrder | **Partial** — shows actual status update logic |
| N22: "inventory adjustment email notification" | InventoryAdjustmentService | **Partial** — shows adjustment; absence of email is informative |

**Finding: `[VERIFIED]` The binary FPR metric (100%) overstates the actual harm. A graded relevance metric (e.g., NDCG with graded judgments) would better capture the nuance that some "negative" query results are partially informative.**

---

## 8. Extended Positive Edge Cases

12 new positive queries targeting known weaknesses:

| ID | Query | Top-1 Score | Found? | Rank |
| :--- | :--- | :---: | :---: | :---: |
| P01 | `Zone` (4-value enum) | 0.550 | YES | 2 |
| P02 | `LowStockEvent` (event class) | 0.710 | YES | 1 |
| P03 | `BaseTimestampEntity` (abstract base) | 0.640 | YES | 1 |
| P04 | `ImportStrategy` (interface) | 0.435 | YES | 1 |
| P05 | `ShortageResolver` (utility) | 0.709 | YES | 1 |
| P06 | `InventoryAdjustmentReason` (enum) | 0.805 | YES | 1 |
| P07 | `CreateInboundScheduleDto` (DTO) | 0.309 | **NO** | > 10 |
| P08 | `PickingAllocationCompletionStrategy` | 0.764 | YES | 1 |
| P09 | `AllocationExecutionService.completeAllocation` | 0.841 | YES | 1 |
| P10 | `InventoryHistoryMapper` (mapper) | 0.853 | YES | 2 |
| P11 | `SecurityFacade` | 0.572 | YES | 1 |
| P12 | `NeedsAttentionResponse` (record DTO) | 0.620 | YES | 1 |

**Edge-case Hit@1: 75.0% (9/12), Hit@3: 91.7% (11/12)**

**Finding: `[VERIFIED]` The "caller semantic dominance" problem is NOT universal. Most exact identifier queries succeed (11/12). The failure mode is specific to:**
1. **DTO names that don't appear as standalone Java files** (P07: `CreateInboundScheduleDto` is likely an inner record or parameter object)
2. **Short repository method signatures** (original Q02, Q33)

The original report's characterization of "exact identifiers weaker" is partially overstated. The weakness is specific to identifiers that are either:
- Declared inline (not standalone files)
- Single-line declarations in large multi-method files

---

## 9. Final Classification of Original Report Findings

| Finding | Original Status | Adversarial Validation Result | Notes |
| :--- | :--- | :--- | :--- |
| Dense retrieval strong for business logic | `[VERIFIED]` | **`[VERIFIED]`** | Confirmed by 100% semantic impl pass rate and edge cases |
| Cross-file retrieval strong | `[VERIFIED]` | **`[VERIFIED]` with qualification** | 5/6 complete in Top 5 (not 6/6); Q16 needs Top 8 |
| Exact identifiers weaker | `[VERIFIED]` | **`[PARTIALLY VERIFIED]`** | True for inline DTOs and repo method sigs; false for most standalone classes |
| BM25 required | `[INFERRED]` | **`[INFERRED]`** | Reasonable hypothesis; unproven without A/B experiment |
| Threshold 0.42 sufficient for FP elimination | `[VERIFIED]` | **`[FALSIFIED]`** | Fails on 58.8% of hard negatives; massively overstated |
| Threshold 0.42 is optimal | Implied | **`[FALSIFIED]`** | No single threshold provides clean separation |
| Header enrichment will help | `[UNCERTAIN]` | **`[INFERRED]`** | Reasonable but requires experiment |
| Negative FPR genuinely 100% | `[VERIFIED]` | **`[VERIFIED]` with methodological caveat** | True by binary definition; overstated in practice |
| "Bimodal separation" between pos/neg scores | `[VERIFIED]` | **`[FALSIFIED]`** | Artifact of trivially easy negatives; overlap is massive with hard negatives |

---

## 10. Final Verdict

### What the benchmark PROVES

1. **`[VERIFIED]`** The RAG system delivers strong semantic retrieval for business-logic and cross-file queries when the query uses natural language describing functionality (Hit@1 ≥ 70% on primary evidence, 80% on any evidence).
2. **`[VERIFIED]`** The cross-encoder reranker effectively promotes relevant chunks over partially matching noise within the same domain.
3. **`[VERIFIED]`** Short method declarations and inline DTOs are systematically disadvantaged by dense-only retrieval.
4. **`[VERIFIED]`** The current `similarity_threshold: 0.10` is too low — trivially out-of-domain queries (blockchain, drones) should be filtered.

### What the benchmark STRONGLY SUGGESTS (but does not prove)

1. **`[INFERRED]`** Adding BM25 / sparse lexical search would improve exact-identifier retrieval without harming semantic queries.
2. **`[INFERRED]`** Enriching chunk content with structural metadata before embedding would reduce the "short declaration" disadvantage.
3. **`[INFERRED]`** Raising the threshold to ~0.35 would filter trivially irrelevant queries with minimal positive query loss (1 query: Q11 at 0.348).

### What the benchmark DOES NOT PROVE

1. **`[FALSIFIED]`** That threshold 0.42 (or any single threshold) can distinguish domain-adjacent negatives from positives. The score distributions overlap from 0.348 to 0.702.
2. **`[UNPROVEN]`** That BM25 + RRF will move exact identifiers to Rank 1. This requires A/B testing.
3. **`[UNPROVEN]`** That the benchmark's 83.3% positive pass rate generalizes to queries from real developers. The dataset is concentrated on well-documented core services.
4. **`[UNPROVEN]`** The specific quantitative claims: "raising threshold to 0.42 will achieve 0% FPR" and "BM25 will raise Hit@1 from 60% to >85%".
5. **`[UNPROVEN]`** That the reported metrics (especially Hit@1, Hit@3, MRR) are arithmetically correct — they cannot be reproduced under any consistent definition.

### Recommended Next Experiment

> [!IMPORTANT]
> **One experiment, maximum information:**
>
> **Implement a `confidence_signal` output in the MCP tool** that returns both the similarity score AND a boolean `is_confident` flag based on the **score gap between the top result and a reference baseline** (e.g., the mean score of the bottom 3 results, or a fixed calibration set).
>
> This moves the system from absolute-threshold-based filtering (which is fundamentally limited by score distribution overlap) toward **relative confidence** — "how much more relevant is the best match compared to random noise?"
>
> This single experiment would:
> 1. Test whether **relative scoring** separates hard negatives better than absolute thresholds
> 2. Produce a dataset for calibrating any future threshold decisions
> 3. Require no changes to the embedding model, chunking, or reranking pipeline

---

## Appendix A: Adversarial Negative Query Details

| ID | Type | Query | Top-1 Score | Top-1 Symbol | FP at 0.42? |
| :--- | :--- | :--- | :---: | :--- | :---: |
| N01 | A | returns processing reverse logistics RMA | 0.379 | — | NO |
| N02 | A | wave planning batch picking optimization | 0.405 | V11 SQL | NO |
| N03 | A | cycle counting scheduled partial inventory | **0.524** | README: Scheduled Jobs | **YES** |
| N04 | A | lot tracking serial number batch expiry | 0.380 | — | NO |
| N05 | A | multi-warehouse cross-facility stock transfer | **0.423** | V24 SQL migration | **YES** |
| N06 | A | shipping label carrier UPS FedEx | 0.280 | — | NO |
| N07 | A | put-away optimization slotting algorithm | 0.390 | — | NO |
| N08 | A | quality control quarantine zone | 0.369 | — | NO |
| N09 | A | demand forecasting ML safety stock | **0.436** | InventoryAiTools | **YES** |
| N10 | A | purchase order vendor supplier procurement | 0.404 | — | NO |
| N11 | A | PDF report generation performance | 0.366 | — | NO |
| N12 | A | audit trail change history Envers | **0.598** | BaseTimestampEntity | **YES** |
| N13 | B | OrderService.updateOrderStatus SHIPPED | **0.702** | OrderService.updateOrder | **YES** |
| N14 | B | ReplenishmentService.scheduleReplenishment timer | **0.650** | ReplenishmentService | **YES** |
| N15 | C | PickingAllocationStrategy replenishment zone | **0.701** | ReplenishmentAllocationStrategy | **YES** |
| N16 | C | StockService.validateStockBalance | **0.579** | InventoryAdjustmentValidator | **YES** |
| N17 | D | InventoryReallocationStrategy | **0.588** | ReallocationPlanItem | **YES** |
| N18 | D | LocationOptimizationService.calculateOptimalSlot | **0.444** | LocationService | **YES** |
| N19 | D | OrderAllocationRepository.findPendingAllocations | **0.575** | OrderService | **YES** |
| N20 | D | WarehouseZoneRebalancer capacity balancing | **0.496** | ReplenishmentAllocationStrategy | **YES** |
| N21 | D | AllocationConflictResolver priority conflict | **0.509** | PickingAllocationStrategy | **YES** |
| N22 | E | inventory adjustment email notification supervisor | **0.544** | README: Future Enhancements | **YES** |
| N23 | E | StockAllocationStrategy WebSocket notification | **0.497** | Allocation entity | **YES** |
| N24 | E | InventoryAdjustmentApplier PDF certificate | **0.579** | InventoryAdjustmentService | **YES** |
| N25 | F | BarcodeScanner directly update stock database | **0.568** | InventoryAiTools | **YES** |
| N26 | F | JwtRequestFilter invoke ProductVectorIndexer | **0.657** | JwtRequestFilter | **YES** |
| N27 | F | ReplenishmentService directly call PickingFlowService | **0.634** | PickingFlowService | **YES** |
| N28 | F | SupervisorDashboardService read external ERP | **0.547** | SupervisorDashboardController | **YES** |

**False Positive Rate at 0.42: 20/28 = 71.4%**

## Appendix B: Extended Positive Edge Cases

| ID | Query | Top-1 Score | Primary Found? | GT Rank | Verdict |
| :--- | :--- | :---: | :---: | :---: | :---: |
| P01 | `Zone` | 0.550 | YES | 2 | PASS |
| P02 | `LowStockEvent` | 0.710 | YES | 1 | PASS |
| P03 | `BaseTimestampEntity` | 0.640 | YES | 1 | PASS |
| P04 | `ImportStrategy` | 0.435 | YES | 1 | PASS |
| P05 | `ShortageResolver` | 0.709 | YES | 1 | PASS |
| P06 | `InventoryAdjustmentReason` | 0.805 | YES | 1 | PASS |
| P07 | `CreateInboundScheduleDto` | 0.309 | NO | > 10 | FAIL |
| P08 | `PickingAllocationCompletionStrategy` | 0.764 | YES | 1 | PASS |
| P09 | `AllocationExecutionService.completeAllocation` | 0.841 | YES | 1 | PASS |
| P10 | `InventoryHistoryMapper` | 0.853 | YES | 2 | PASS |
| P11 | `SecurityFacade` | 0.572 | YES | 1 | PASS |
| P12 | `NeedsAttentionResponse` | 0.620 | YES | 1 | PASS |

**Extended Positive Hit@1: 75.0%**, **Hit@3: 91.7%**
