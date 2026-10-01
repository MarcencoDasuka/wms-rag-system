from pathlib import Path
from src.chunker import CodeChunk
from src.config import AppConfig, RetrievalConfig, VectorDBConfig
from src.indexer import CodebaseIndexer
from src.reranker import CodeCrossEncoderReranker
from src.retriever import CodeRetriever


def test_negative_retrieval_returns_empty_when_below_threshold(tmp_path: Path):
    """Verify that queries below similarity threshold return empty results instead of falling back to noise."""
    db_dir = tmp_path / "chroma_thresh"
    src_dir = tmp_path / "src"
    src_dir.mkdir()

    # Index legitimate warehouse code
    (src_dir / "InventoryService.java").write_text(
        "public class InventoryService { public void allocateStock(Long id, int qty) {} }",
        encoding="utf-8"
    )

    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_threshold",
        ),
        retrieval=RetrievalConfig(
            default_top_k=5,
            # Set strict similarity threshold to falsify fallback behavior
            similarity_threshold=0.85,
        ),
    )

    indexer = CodebaseIndexer(config)
    indexer.scan_and_index(target_dir_override=str(src_dir), clear_first=True)

    retriever = CodeRetriever(config)

    # 1. Unrelated query that cannot meet 0.85 threshold
    unrelated_query = "recipe for homemade blueberry pancakes and french toast"
    results = retriever.retrieve(unrelated_query)

    # Invariant: Must return empty list, NOT arbitrary fallback chunks
    assert results == []

    formatted = retriever.format_for_agent(results)
    assert "no matching chunks passed the relevance threshold" in formatted

    # 2. Normal relevant query with reasonable threshold
    config_normal = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_threshold",
        ),
        retrieval=RetrievalConfig(
            default_top_k=5,
            similarity_threshold=0.10,
        ),
    )
    retriever_normal = CodeRetriever(config_normal)
    relevant_results = retriever_normal.retrieve("allocate stock inventory quantity")
    assert len(relevant_results) > 0
    assert any("InventoryService" in chunk.file_name for chunk, _ in relevant_results)


def test_reranker_fallback_applies_similarity_threshold_when_min_score_negative():
    """Verify that fallback reranker applies calibrated similarity threshold when min_score is negative logit."""
    reranker = CodeCrossEncoderReranker(model_name="nonexistent-dummy-model")
    reranker._available = False  # Force fallback mode

    dummy_chunk = CodeChunk(
        id="dummy_01",
        file_path="src/Dummy.java",
        file_name="Dummy.java",
        language="java",
        chunk_type="method",
        symbol_name="dummy",
        content="public void dummy() {}",
        start_line=1,
        end_line=2,
        metadata={}
    )

    # Candidate with low similarity (0.12) and zero keyword overlap with unrelated query
    low_sim_candidate = (dummy_chunk, 0.12)

    # With default min_score = -7.0 (logit domain), fallback should calibrate to 0.25 and filter it out
    filtered = reranker.rerank(
        query="unrelated query about astrophysics and stellar orbits",
        candidates=[low_sim_candidate],
        min_score=-7.0,
    )
    assert len(filtered) == 0, f"Expected low similarity candidate to be filtered, got: {filtered}"

    # With high similarity (0.45), candidate should pass through
    high_sim_candidate = (dummy_chunk, 0.45)
    passed = reranker.rerank(
        query="unrelated query about astrophysics and stellar orbits",
        candidates=[high_sim_candidate],
        min_score=-7.0,
    )
    assert len(passed) == 1
    assert passed[0][0].id == "dummy_01"
