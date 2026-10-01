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
    from src.config import AppConfig
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

    config = AppConfig()
    indexer = CodebaseIndexer(config)
    files_scanned, chunks_indexed = indexer.scan_and_index(target_dir_override=str(codebase_dir), clear_first=True)

    # Only Legit.java should be indexed; external_link.properties must be skipped!
    assert files_scanned == 1
    indexed_files = [c.file_name for c, _ in indexer.store.search(indexer.embedder.embed_query("HOST_DATA_LEAK"), top_k=5)]
    assert "external_link.properties" not in indexed_files
