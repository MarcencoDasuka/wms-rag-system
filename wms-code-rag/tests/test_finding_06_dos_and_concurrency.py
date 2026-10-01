"""Regression test for Finding 6: Query/Resource/Concurrency DoS & Reindex Lock Protection."""

import threading
from pathlib import Path
from src.config import AppConfig, VectorDBConfig
from src.indexer import CodebaseIndexer
from src.mcp_server import clamp_top_n, search_wms_code, validate_query


def test_query_limits_and_top_n_clamping():
    """Verify that oversized/empty query payloads and extreme top_n are bounded."""
    # 1. Empty query rejected
    assert validate_query("", "query") is not None
    assert "cannot be empty" in validate_query("   ", "query")

    # 2. Oversized payload rejected
    oversized = "A" * 1500
    err = validate_query(oversized, "query")
    assert err is not None
    assert "exceeds maximum allowed length" in err

    # 3. search_wms_code propagates validation errors
    assert "cannot be empty" in search_wms_code("   ")
    assert "exceeds maximum allowed length" in search_wms_code("X" * 1001)

    # 4. clamp_top_n enforces hard boundaries [1, 20]
    assert clamp_top_n(-10) == 1
    assert clamp_top_n(0) == 1
    assert clamp_top_n(5) == 5
    assert clamp_top_n(100) == 20


def test_concurrent_reindexing_is_prevented_without_sleep(tmp_path: Path):
    """Verify that concurrent scan_and_index attempts are strictly blocked using synchronizers."""
    db_dir = tmp_path / "chroma_concurrency"
    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_concurrency",
        ),
    )
    indexer = CodebaseIndexer(config)

    start_event = threading.Event()
    unblock_event = threading.Event()
    results = {}

    def slow_task():
        # Acquire lock and simulate an in-progress scan
        assert indexer._reindex_lock.acquire(blocking=False)
        try:
            start_event.set()
            unblock_event.wait()
        finally:
            indexer._reindex_lock.release()

    # Start thread 1 which holds the reindex lock
    t1 = threading.Thread(target=slow_task)
    t1.start()

    # Wait until thread 1 has firmly acquired the lock
    start_event.wait()

    # Thread 2 attempts to run scan_and_index concurrently
    try:
        indexer.scan_and_index(target_dir_override=str(tmp_path))
        results["t2_success"] = True
    except RuntimeError as e:
        results["t2_error"] = str(e)
    finally:
        # Allow thread 1 to finish cleanly
        unblock_event.set()
        t1.join()

    # Invariant: concurrent attempt must be rejected with RuntimeError
    assert "t2_error" in results
    assert "Reindexing is already in progress" in results["t2_error"]
    assert not indexer._reindex_lock.locked()
