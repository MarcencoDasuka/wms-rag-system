import io
import sys
from pathlib import Path
from src.indexer import console, CodebaseIndexer
from src.config import AppConfig, VectorDBConfig, CodebaseConfig


def test_console_and_logging_direct_to_stderr(capsys):
    """Verify that Rich Console and logger output goes to stderr, keeping stdout clean for MCP stdio protocol."""
    # Invariant 1: indexer console must be configured with stderr=True
    assert console.file == sys.stderr or getattr(console, "_stderr", False) is True or console.file.name == "<stderr>"

    # Print a message via indexer console
    console.print("[green]Testing stdio channel cleanliness[/green]")

    captured = capsys.readouterr()
    # Invariant 2: stdout must be completely empty!
    assert captured.out == "", f"stdout was polluted with: {captured.out!r}"
    # Invariant 3: stderr must receive the human-readable console message
    assert "Testing stdio channel cleanliness" in captured.err


def test_indexing_operation_does_not_pollute_stdout(tmp_path: Path, capsys):
    """Verify that a full scan_and_index run outputs only to stderr and never to stdout."""
    db_dir = tmp_path / "chroma_clean"
    src_dir = tmp_path / "src"
    src_dir.mkdir()
    (src_dir / "Sample.java").write_text("public class Sample { public void hello() {} }", encoding="utf-8")

    cfg = AppConfig(
        vector_db=VectorDBConfig(persist_dir=str(db_dir), collection_name="test_clean_stdout"),
        codebase=CodebaseConfig(target_dir=str(src_dir)),
    )

    indexer = CodebaseIndexer(cfg)
    # Clear capsys before scan
    _ = capsys.readouterr()

    # Run indexing
    files_scanned, chunks_indexed = indexer.scan_and_index(target_dir_override=str(src_dir), clear_first=True)

    captured = capsys.readouterr()
    # Invariant: stdout must remain 100% clean for JSON-RPC communication
    assert captured.out == "", f"Indexing leaked data to stdout: {captured.out}"
    # Diagnostic output must be present on stderr
    assert "Discovered 1 source files to index." in captured.err or "Scanning WMS codebase at" in captured.err
