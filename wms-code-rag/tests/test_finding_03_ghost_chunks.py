"""Regression test for Finding 3: Ghost Chunks Elimination & Reindex Reconciliation."""

from pathlib import Path
from src.chunker import CodeAwareChunker
from src.config import AppConfig, VectorDBConfig
from src.indexer import CodebaseIndexer


def test_chunk_ids_are_invariant_to_line_number_shifts(tmp_path: Path):
    """Verify that shifting code lines does not change chunk IDs (preventing ghost duplicates)."""
    chunker = CodeAwareChunker()

    original_java = """package com.isd.wms.service;

public class InventoryService {
    public void allocateStock(Long itemId, int qty) {
        System.out.println("allocate");
    }

    public void releaseStock(Long itemId) {
        System.out.println("release");
    }
}
"""
    file_path = tmp_path / "InventoryService.java"
    file_path.write_text(original_java, encoding="utf-8")
    chunks_1 = chunker.chunk_file(file_path, "InventoryService.java")
    ids_1 = {c.symbol_name: c.id for c in chunks_1}

    # Add 15 blank lines and a header comment shifting method start lines
    shifted_java = """// Copyright 2026 WMS
// Additional copyright header lines
//
//


package com.isd.wms.service;










public class InventoryService {
    public void allocateStock(Long itemId, int qty) {
        System.out.println("allocate");
    }

    public void releaseStock(Long itemId) {
        System.out.println("release");
    }
}
"""
    file_path.write_text(shifted_java, encoding="utf-8")
    chunks_2 = chunker.chunk_file(file_path, "InventoryService.java")
    ids_2 = {c.symbol_name: c.id for c in chunks_2}

    # Chunk IDs must remain identical despite line shifts!
    assert ids_1["InventoryService.allocateStock"] == ids_2["InventoryService.allocateStock"]
    assert ids_1["InventoryService.releaseStock"] == ids_2["InventoryService.releaseStock"]
    assert ids_1["InventoryService"] == ids_2["InventoryService"]


def test_reconciliation_prunes_deleted_file_chunks(tmp_path: Path):
    """Verify that re-indexing prunes chunks belonging to deleted files without needing full clear."""
    db_dir = tmp_path / "chroma_test"
    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_reconciliation",
        )
    )

    src_dir = tmp_path / "src"
    src_dir.mkdir()

    file_a = src_dir / "FileA.java"
    file_a.write_text("public class FileA { public void actionA() {} }", encoding="utf-8")

    file_b = src_dir / "FileB.java"
    file_b.write_text("public class FileB { public void actionB() {} }", encoding="utf-8")

    indexer = CodebaseIndexer(config)
    # 1. First index with both files
    _, total_chunks_1 = indexer.scan_and_index(target_dir_override=str(src_dir), clear_first=True)
    assert total_chunks_1 > 0
    assert indexer.store.count() == total_chunks_1

    # 2. Delete FileB from source tree
    file_b.unlink()

    # 3. Incremental reindex (clear_first=False)
    _, total_chunks_2 = indexer.scan_and_index(target_dir_override=str(src_dir), clear_first=False)

    # 4. Invariant: collection count MUST match total_chunks_2 (FileB chunks must be pruned!)
    assert indexer.store.count() == total_chunks_2
    all_docs = [c.file_name for c, _ in indexer.store.search(indexer.embedder.embed_query("action"), top_k=10)]
    assert "FileB.java" not in all_docs
