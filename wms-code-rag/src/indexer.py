"""Codebase crawler and indexer for WMS repository."""

import logging
from pathlib import Path
from typing import List, Tuple

from rich.console import Console

from src.chunker import CodeAwareChunker, CodeChunk
from src.config import AppConfig
from src.embedder import SentenceTransformerEmbedder
from src.vector_store import CodeVectorStore

logger = logging.getLogger("wms_indexer")
console = Console()


class CodebaseIndexer:
    """Traverses codebase directory, chunks files, generates embeddings, and saves to VectorStore."""

    def __init__(self, config: AppConfig):
        self.config = config
        self.chunker = CodeAwareChunker()
        self.embedder = SentenceTransformerEmbedder.get_instance(
            model_name=config.embeddings.default_model,
            device=config.embeddings.device,
        )
        self.store = CodeVectorStore(
            persist_dir=config.vector_db.persist_dir,
            collection_name=config.vector_db.collection_name,
            embedding_model=config.embeddings.default_model,
        )

    def scan_and_index(self, target_dir_override: str | None = None, clear_first: bool = False) -> Tuple[int, int]:
        """Scans the WMS codebase and indexes all matching files.
        
        Returns:
            Tuple of (total_files_scanned, total_chunks_indexed)
        """
        target_path_str = target_dir_override or self.config.codebase.target_dir
        target_path = Path(target_path_str)
        if not target_path.is_absolute():
            base_dir = Path(__file__).resolve().parent.parent
            target_path = (base_dir / target_path_str).resolve()

        if not target_path.exists():
            console.print(f"[red]Error: Target codebase path '{target_path}' does not exist![/red]")
            return 0, 0

        console.print(f"[cyan]Scanning WMS codebase at:[/cyan] {target_path}")

        if clear_first:
            console.print("[yellow]Clearing existing vector collection...[/yellow]")
            self.store.clear()

        import re
        import os

        secret_file_patterns = [
            re.compile(r"^\.env.*", re.IGNORECASE),
            re.compile(r".*secret.*", re.IGNORECASE),
            re.compile(r".*credential.*", re.IGNORECASE),
            re.compile(r".*id_rsa.*", re.IGNORECASE),
            re.compile(r".*\.(pem|key|pkcs12|p12|pfx|jks|keystore)$", re.IGNORECASE),
        ]
        sensitive_dirs = {"secrets", ".ssh", ".aws", ".gnupg", "certificates"}

        extensions = set(self.config.codebase.extensions)
        ignore_dirs = set(self.config.codebase.ignore_dirs) | sensitive_dirs

        matched_files: List[Path] = []
        for root, dirs, files in os.walk(str(target_path)):
            # In-place directory pruning: do not recurse into ignored directories (e.g. node_modules, target)
            dirs[:] = [d for d in dirs if d not in ignore_dirs and not d.startswith(".")]
            for file in files:
                # Exclude secret-bearing files by name/pattern
                if any(p.match(file) for p in secret_file_patterns):
                    continue
                ext = Path(file).suffix.lower()
                if ext in extensions:
                    matched_files.append(Path(root) / file)

        console.print(f"[green]Discovered {len(matched_files)} source files to index.[/green]")

        all_chunks: List[CodeChunk] = []
        for file_path in matched_files:
            try:
                rel_path = str(file_path.relative_to(target_path)).replace("\\", "/")
            except ValueError:
                rel_path = file_path.name
            chunks = self.chunker.chunk_file(file_path, rel_path)
            all_chunks.extend(chunks)

        console.print(f"[cyan]Generated {len(all_chunks)} semantic chunks. Generating embeddings...[/cyan]")

        if all_chunks:
            texts = [c.content for c in all_chunks]
            embeddings = self.embedder.embed_texts(texts, batch_size=32)
            self.store.add_chunks(all_chunks, embeddings)

        console.print(f"[bold green]Indexing complete! Collection size: {self.store.count()} chunks.[/bold green]")
        return len(matched_files), len(all_chunks)


if __name__ == "__main__":
    from src.config import load_config
    cfg = load_config()
    indexer = CodebaseIndexer(cfg)
    indexer.scan_and_index(clear_first=False)
