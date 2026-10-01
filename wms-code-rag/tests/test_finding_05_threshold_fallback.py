"""Regression test for Finding 5: Negative Retrieval & Threshold Fallback Elimination."""

from pathlib import Path
from src.config import AppConfig, RetrievalConfig, VectorDBConfig
from src.indexer import CodebaseIndexer
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
