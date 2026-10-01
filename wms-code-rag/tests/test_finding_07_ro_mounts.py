import yaml
from pathlib import Path


def test_docker_compose_mounts_are_read_only():
    compose_path = Path(__file__).resolve().parent.parent / "docker-compose.yml"
    assert compose_path.exists(), f"docker-compose.yml not found at {compose_path}"

    with open(compose_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    services = data.get("services", {})
    assert "wms-code-rag" in services, "wms-code-rag service missing from compose"

    volumes = services["wms-code-rag"].get("volumes", [])
    assert volumes, "volumes missing from wms-code-rag service"

    # Verify that any source mounts are marked :ro (read-only)
    source_mounts_checked = 0
    for vol in volumes:
        if isinstance(vol, str):
            parts = vol.split(":")
            host_path = parts[0]
            container_path = parts[1] if len(parts) > 1 else ""
            mode = parts[2] if len(parts) > 2 else ""

            if "/src" in container_path or "/workspace/wms" in container_path or "src" in host_path:
                assert mode == "ro", f"Source volume mount '{vol}' must be read-only (:ro) to prevent host source tampering"
                source_mounts_checked += 1

    assert source_mounts_checked >= 2, f"Expected at least 2 source mounts (:ro), found {source_mounts_checked}"


def test_indexer_skips_symlinks_escaping_codebase_root(tmp_path: Path):
    """Verify that indexer rejects symlinks resolving outside the codebase target directory."""
    from src.config import AppConfig, VectorDBConfig
    from src.indexer import CodebaseIndexer

    external_dir = tmp_path / "external_host"
    external_dir.mkdir()
    external_secret = external_dir / "host_sensitive.properties"
    external_secret.write_text("secret.info=HOST_DATA_LEAK\n", encoding="utf-8")

    codebase_dir = tmp_path / "wms_codebase"
    codebase_dir.mkdir()
    (codebase_dir / "Legit.java").write_text("public class Legit {}", encoding="utf-8")

    # Create symlink pointing outside codebase
    symlink_file = codebase_dir / "external_link.properties"
    try:
        symlink_file.symlink_to(external_secret)
    except (OSError, NotImplementedError):
        # Skip if host environment does not allow symlink creation without admin privileges
        return

    db_dir = tmp_path / "chroma_ro_mounts"
    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_ro_mounts",
        ),
    )
    indexer = CodebaseIndexer(config)
    files_scanned, chunks_indexed = indexer.scan_and_index(target_dir_override=str(codebase_dir), clear_first=True)

    # Only Legit.java should be indexed; external_link.properties must be skipped!
    assert files_scanned == 1
    indexed_files = [c.file_name for c, _ in indexer.store.search(indexer.embedder.embed_query("HOST_DATA_LEAK"), top_k=5)]
    assert "external_link.properties" not in indexed_files


def test_clear_first_operates_only_on_isolated_temporary_directory(tmp_path: Path):
    """Verify that clear_first=True operates strictly on isolated temporary directory and never touches production data/chroma."""
    from src.config import AppConfig, VectorDBConfig
    from src.indexer import CodebaseIndexer
    from src.vector_store import CodeVectorStore

    # Inspect real data/chroma state prior to running test
    prod_store = CodeVectorStore("data/chroma")
    prod_count_before = prod_store.count()

    # Create isolated test environment
    src_dir = tmp_path / "src"
    src_dir.mkdir()
    (src_dir / "TestSample.java").write_text("public class TestSample { void run() {} }", encoding="utf-8")

    db_dir = tmp_path / "chroma_isolated"
    config = AppConfig(
        vector_db=VectorDBConfig(
            persist_dir=str(db_dir),
            collection_name="test_clear_first_isolation",
        ),
    )

    indexer = CodebaseIndexer(config)
    # Perform indexing with clear_first=True on isolated test store
    files_scanned, chunks_indexed = indexer.scan_and_index(target_dir_override=str(src_dir), clear_first=True)

    # Invariants:
    # 1. Temporary directory was populated
    assert files_scanned == 1
    assert chunks_indexed > 0
    assert indexer.store.count() == chunks_indexed
    assert indexer.store.persist_dir.resolve() == db_dir.resolve()

    # 2. Production store was NEVER modified
    prod_store_after = CodeVectorStore("data/chroma")
    assert prod_store_after.count() == prod_count_before, (
        f"Production store chunk count changed from {prod_count_before} to {prod_store_after.count()}!"
    )


def test_production_chroma_mutation_guard_blocks_accidental_destruction():
    """Verify that attempting to mutate production data/chroma during test execution raises RuntimeError."""
    import pytest
    from src.chunker import CodeChunk
    from src.vector_store import CodeVectorStore

    store = CodeVectorStore("data/chroma")

    # 1. clear() on production store must fail loudly during pytest
    with pytest.raises(RuntimeError, match="Blocked destructive vector store mutation on production index"):
        store.clear()

    # 2. add_chunks() on production store must fail loudly during pytest
    dummy_chunk = CodeChunk(
        id="canary_01",
        file_path="Canary.java",
        file_name="Canary.java",
        language="java",
        chunk_type="class_summary",
        symbol_name="Canary",
        content="public class Canary {}",
        start_line=1,
        end_line=1,
    )
    with pytest.raises(RuntimeError, match="Blocked destructive vector store mutation on production index"):
        store.add_chunks([dummy_chunk], [[0.0] * 384])

    # 3. delete_chunks_by_ids() on production store must fail loudly during pytest
    with pytest.raises(RuntimeError, match="Blocked destructive vector store mutation on production index"):
        store.delete_chunks_by_ids(["nonexistent_id"])
