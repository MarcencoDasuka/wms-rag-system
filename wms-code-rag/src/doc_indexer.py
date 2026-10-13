"""Indexer for the Ospysin documentation set (isolated Chroma collection)."""

import json
import logging
import subprocess
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional, Tuple

from rich.console import Console

from src.config import AppConfig
from src.doc_chunker import MarkdownSpecChunker
from src.chunker import CodeChunk
from src.embedder import SentenceTransformerEmbedder
from src.vector_store import CodeVectorStore

logger = logging.getLogger("ospysin_indexer")
console = Console(stderr=True)

INDEX_META_FILENAME = ".ospysin_index_meta.json"
GIT_TIMEOUT_SECONDS = 5


def resolve_git_revision(docs_dir: Path) -> str:
    """Best-effort source revision for ``docs_dir``: ``<sha>`` or ``<sha>-dirty``; ``unknown`` on failure.

    Invoked without a shell and with a hard timeout; a missing git binary or non-repo directory
    degrades to ``unknown`` instead of failing indexing.
    """
    def _run(args: List[str]) -> Optional[str]:
        try:
            res = subprocess.run(
                ["git", "-C", str(docs_dir), *args],
                capture_output=True,
                text=True,
                timeout=GIT_TIMEOUT_SECONDS,
                check=True,
            )
            return res.stdout.strip()
        except (OSError, subprocess.SubprocessError):
            return None

    sha = _run(["rev-parse", "HEAD"])
    if not sha:
        return "unknown"
    dirty = _run(["status", "--porcelain", "--", "."])
    return f"{sha}-dirty" if dirty else sha


class OspysinDocsIndexer:
    """Crawls the Ospysin docs directory, chunks Markdown specs, embeds and stores them."""

    def __init__(
        self,
        config: AppConfig,
        store: Optional[CodeVectorStore] = None,
        embedder: Optional[SentenceTransformerEmbedder] = None,
    ):
        self.config = config
        self.chunker = MarkdownSpecChunker(
            max_chars=config.ospysin.chunk_max_chars,
            overlap_chars=config.ospysin.chunk_overlap_chars,
        )
        self.embedder = embedder or SentenceTransformerEmbedder.get_instance(
            model_name=config.embeddings.default_model,
            device=config.embeddings.device,
        )
        self.store = store or CodeVectorStore(
            persist_dir=config.vector_db.persist_dir,
            collection_name=config.ospysin.collection_name,
            embedding_model=config.embeddings.default_model,
        )
        self._reindex_lock = threading.Lock()

    # ------------------------------------------------------------------ helpers

    def resolve_docs_dir(self, override: Optional[str] = None) -> Path:
        raw = override or self.config.ospysin.docs_dir
        path = Path(raw)
        if not path.is_absolute():
            path = (Path(__file__).resolve().parent.parent / raw).resolve()
        return path

    @property
    def meta_path(self) -> Path:
        return self.store.persist_dir / INDEX_META_FILENAME

    def read_index_meta(self) -> dict:
        try:
            return json.loads(self.meta_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return {}

    def _collect_files(self, docs_dir: Path) -> List[Path]:
        extensions = {e.lower() for e in self.config.ospysin.extensions}
        resolved_root = docs_dir.resolve()
        files: List[Path] = []
        for candidate in sorted(docs_dir.rglob("*")):
            if candidate.suffix.lower() not in extensions:
                continue
            if candidate.is_symlink() or not candidate.is_file():
                continue
            try:
                if not candidate.resolve().is_relative_to(resolved_root):
                    continue
            except (OSError, ValueError, RuntimeError):
                continue
            if any(part.startswith(".") for part in candidate.relative_to(docs_dir).parts):
                continue
            files.append(candidate)
        return files

    # ------------------------------------------------------------------ indexing

    def scan_and_index(
        self, docs_dir_override: Optional[str] = None, clear_first: bool = False
    ) -> Tuple[int, int]:
        """Index the docs directory; returns ``(files_scanned, chunks_indexed)``.

        Raises ``RuntimeError`` if another reindex is running, the directory is missing, or it
        contains no matching files (so a misconfigured path can never prune a healthy index).
        """
        if not self._reindex_lock.acquire(blocking=False):
            raise RuntimeError("Ospysin reindexing is already in progress. Concurrent reindexing is prohibited.")
        try:
            docs_dir = self.resolve_docs_dir(docs_dir_override)
            if not docs_dir.is_dir():
                raise RuntimeError(f"Ospysin docs directory '{docs_dir}' does not exist.")

            files = self._collect_files(docs_dir)
            if not files:
                raise RuntimeError(f"No documentation files found under '{docs_dir}'; index left unchanged.")

            revision = resolve_git_revision(docs_dir)
            console.print(f"[cyan]Indexing {len(files)} Ospysin docs from:[/cyan] {docs_dir} (revision {revision})")

            all_chunks: List[CodeChunk] = []
            for file_path in files:
                rel_path = file_path.relative_to(docs_dir).as_posix()
                for chunk in self.chunker.chunk_file(file_path, rel_path):
                    chunk.metadata["source_commit"] = revision
                    all_chunks.append(chunk)

            if not all_chunks:
                raise RuntimeError("Chunker produced no chunks; index left unchanged.")

            embeddings = self.embedder.embed_texts([c.content for c in all_chunks], batch_size=32)

            if clear_first:
                console.print("[yellow]Clearing Ospysin docs collection...[/yellow]")
                self.store.clear()

            self.store.add_chunks(all_chunks, embeddings)

            pruned = self.store.prune_stale_chunks({c.id for c in all_chunks})
            if pruned:
                console.print(f"[yellow]Pruned {pruned} obsolete Ospysin chunks.[/yellow]")

            self._write_meta(revision, len(files), len(all_chunks))
            console.print(f"[bold green]Ospysin index ready: {self.store.count()} chunks.[/bold green]")
            return len(files), len(all_chunks)
        finally:
            self._reindex_lock.release()

    def _write_meta(self, revision: str, files: int, chunks: int) -> None:
        meta = {
            "source_commit": revision,
            "docs_dir": str(self.resolve_docs_dir()),
            "files": files,
            "chunks": chunks,
            "embedding_model": self.config.embeddings.default_model,
            "chunk_max_chars": self.config.ospysin.chunk_max_chars,
            "chunk_overlap_chars": self.config.ospysin.chunk_overlap_chars,
            "indexed_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            self.meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")
        except OSError as exc:
            logger.warning("Could not write Ospysin index metadata: %s", exc)
