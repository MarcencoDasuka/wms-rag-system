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
