"""ChromaDB vector store for WMS Code Chunks."""

import os
from pathlib import Path
from typing import Any, List, Optional, Tuple
import uuid

import chromadb
from chromadb.config import Settings

from src.chunker import CodeChunk


class CodeVectorStore:
    """Manages persistent ChromaDB vector storage for code chunks."""

    def __init__(
        self,
        persist_dir: str = "data/chroma",
        collection_name: str = "wms_codebase_knowledge",
        embedding_model: Optional[str] = None,
    ):
        self.persist_dir = Path(persist_dir)
        self.persist_dir.mkdir(parents=True, exist_ok=True)
        self.collection_name = collection_name
        self.embedding_model = embedding_model

        self.client = chromadb.PersistentClient(
            path=str(self.persist_dir),
            settings=Settings(anonymized_telemetry=False, is_persistent=True),
        )
        self._symbol_cache: Optional[dict[str, Any]] = None
        self._symbol_cache_count: Optional[int] = None
        self._symbol_cache_rev: Optional[str] = None
        self._rev_file = self.persist_dir / ".index_rev"

    def _assert_safe_mutation(self) -> None:
        """Prevent accidental destruction or mutation of default production index during test execution."""
        if os.environ.get("PYTEST_CURRENT_TEST"):
            try:
                default_path = Path("data/chroma").resolve()
                if self.persist_dir.resolve() == default_path:
                    raise RuntimeError(
                        f"Blocked destructive vector store mutation on production index '{self.persist_dir}' "
                        f"during test execution ({os.environ.get('PYTEST_CURRENT_TEST')}). "
                        "Tests that mutate vector store state MUST configure an isolated temporary persist_dir."
                    )
            except (OSError, RuntimeError) as e:
                if isinstance(e, RuntimeError):
                    raise

    def _bump_revision(self) -> str:
        """Updates persistent revision marker and invalidates in-memory cache."""
        new_rev = uuid.uuid4().hex
        try:
            self._rev_file.write_text(new_rev, encoding="utf-8")
        except Exception:
            pass
        self._symbol_cache = None
        self._symbol_cache_count = None
        self._symbol_cache_rev = None
        return new_rev

    def _get_current_revision(self) -> str:
        """Determines current storage revision combining persistent marker, sqlite mtime, and count."""
        rev_marker = ""
        try:
            if self._rev_file.exists():
                rev_marker = self._rev_file.read_text(encoding="utf-8").strip()
        except Exception:
            pass

        sqlite_path = self.persist_dir / "chroma.sqlite3"
        sqlite_mtime = 0
        try:
            if sqlite_path.exists():
                sqlite_mtime = sqlite_path.stat().st_mtime_ns
        except Exception:
            pass

        return f"{self.count()}:{rev_marker}:{sqlite_mtime}"

    def invalidate_symbol_cache(self) -> None:
        """Explicitly invalidate the in-memory symbol cache."""
        self._symbol_cache = None
        self._symbol_cache_count = None
        self._symbol_cache_rev = None


    @property
    def collection(self):
        """Dynamically resolve current ChromaDB collection to avoid stale handles across re-indexing."""
        try:
            return self.client.get_collection(name=self.collection_name)
        except Exception:
            init_metadata = {"hnsw:space": "cosine"}
            if self.embedding_model:
                init_metadata["embedding_model"] = self.embedding_model
            return self.client.get_or_create_collection(
                name=self.collection_name,
                metadata=init_metadata,
            )

    def count(self) -> int:
        return self.collection.count()

    def add_chunks(self, chunks: List[CodeChunk], embeddings: List[List[float]]) -> None:
        """Batch upsert code chunks into ChromaDB."""
        if not chunks:
            return

        self._assert_safe_mutation()
        self._symbol_cache = None
        self._symbol_cache_count = None
        self._symbol_cache_rev = None

        ids = [c.id for c in chunks]
        documents = [c.content for c in chunks]
        metadatas = [
            {
                "file_path": c.file_path,
                "file_name": c.file_name,
                "language": c.language,
                "chunk_type": c.chunk_type,
                "symbol_name": c.symbol_name,
                "start_line": c.start_line,
                "end_line": c.end_line,
                **{k: str(v) for k, v in c.metadata.items()}
            }
            for c in chunks
        ]

        # Chroma has a max batch limit of 5461 items
        batch_size = 500
        for i in range(0, len(ids), batch_size):
            end_idx = i + batch_size
            self.collection.upsert(
                ids=ids[i:end_idx],
                embeddings=embeddings[i:end_idx],
                documents=documents[i:end_idx],
                metadatas=metadatas[i:end_idx],
            )
        self._bump_revision()

    def search(
        self,
        query_embedding: List[float],
        top_k: int = 10,
        where_filter: Optional[dict[str, Any]] = None,
    ) -> List[Tuple[CodeChunk, float]]:
        """Perform cosine distance similarity search and return chunks with similarity scores."""
        if self.collection.count() == 0:
            return []

        search_k = min(top_k, self.collection.count())
        kwargs: dict[str, Any] = {
            "query_embeddings": [query_embedding],
            "n_results": search_k,
            "include": ["documents", "metadatas", "distances"],
        }
        if where_filter:
            kwargs["where"] = where_filter

        results = self.collection.query(**kwargs)

        chunks_with_scores: List[Tuple[CodeChunk, float]] = []
        if not results or not results.get("ids") or not results["ids"][0]:
            return chunks_with_scores

        ids = results["ids"][0]
        docs = results["documents"][0] if results.get("documents") else []
        metas = results["metadatas"][0] if results.get("metadatas") else []
        distances = results["distances"][0] if results.get("distances") else []

        for cid, doc, meta, dist in zip(ids, docs, metas, distances):
            meta_dict = dict(meta) if meta else {}
            # Cosine distance to similarity: similarity = 1.0 - distance
            similarity = max(0.0, 1.0 - float(dist))

            chunk = CodeChunk(
                id=cid,
                file_path=meta_dict.get("file_path", "unknown"),
                file_name=meta_dict.get("file_name", "unknown"),
                language=meta_dict.get("language", "text"),
                chunk_type=meta_dict.get("chunk_type", "general"),
                symbol_name=meta_dict.get("symbol_name", ""),
                content=doc or "",
                start_line=int(meta_dict.get("start_line", 1)),
                end_line=int(meta_dict.get("end_line", 1)),
                metadata=meta_dict,
            )
            chunks_with_scores.append((chunk, similarity))

        return chunks_with_scores

    def delete_chunks_by_ids(self, ids: List[str]) -> None:
        """Delete specific chunks by ID."""
        if not ids:
            return
        self._assert_safe_mutation()
        self._symbol_cache = None
        self._symbol_cache_count = None
        self._symbol_cache_rev = None
        batch_size = 500
        for i in range(0, len(ids), batch_size):
            self.collection.delete(ids=ids[i:i + batch_size])
        self._bump_revision()

    def get_all_ids(self) -> List[str]:
        """Fetch all indexed chunk IDs from collection."""
        res = self.collection.get(include=[])
        return res.get("ids", []) if res else []

    def prune_stale_chunks(self, active_ids: set[str]) -> int:
        """Removes orphaned chunks from collection that no longer exist in the source tree."""
        all_ids = set(self.get_all_ids())
        stale_ids = list(all_ids - active_ids)
        if stale_ids:
            self.delete_chunks_by_ids(stale_ids)
        return len(stale_ids)

    def clear(self) -> None:
        """Clear all indexed data from the collection."""
        self._assert_safe_mutation()
        self._symbol_cache = None
        self._symbol_cache_count = None
        self._symbol_cache_rev = None
        try:
            self.client.delete_collection(name=self.collection_name)
        except Exception:
            pass
        init_metadata = {"hnsw:space": "cosine"}
        if self.embedding_model:
            init_metadata["embedding_model"] = self.embedding_model
        self.client.get_or_create_collection(
            name=self.collection_name,
            metadata=init_metadata,
        )
        self._bump_revision()

    def _rebuild_symbol_cache(self, current_rev: Optional[str] = None) -> None:
        """Rebuilds deterministic in-memory lookup index from persistent collection metadata."""
        if current_rev is None:
            current_rev = self._get_current_revision()
        cache: dict[str, dict[str, List[CodeChunk]]] = {
            "exact": {},
            "qualified": {},
            "unqualified": {},
            "case_insensitive": {},
        }
        current_count = self.collection.count()
        if current_count == 0:
            self._symbol_cache = cache
            self._symbol_cache_count = 0
            self._symbol_cache_rev = current_rev
            return

        data = self.collection.get(include=["metadatas", "documents"])
        ids = data.get("ids") or []
        metas = data.get("metadatas") or []
        docs = data.get("documents") or []

        for cid, meta, doc in zip(ids, metas, docs):
            m = dict(meta) if meta else {}
            chunk = CodeChunk(
                id=cid,
                file_path=m.get("file_path", "unknown"),
                file_name=m.get("file_name", "unknown"),
                language=m.get("language", "text"),
                chunk_type=m.get("chunk_type", "general"),
                symbol_name=m.get("symbol_name", ""),
                content=doc or "",
                start_line=int(m.get("start_line", 1)),
                end_line=int(m.get("end_line", 1)),
                metadata=m,
            )

            sym = chunk.symbol_name.strip()
            pkg = m.get("package", "").strip()
            cls = m.get("class", "").strip()
            method = m.get("method", "").strip()
            inner = m.get("inner_name", "").strip()
            fname = m.get("file_name", "").strip()

            def add_to_bucket(bucket_name: str, key_name: str, item: CodeChunk):
                if not key_name:
                    return
                bucket = cache[bucket_name]
                lst = bucket.setdefault(key_name, [])
                if not any(x.id == item.id for x in lst):
                    lst.append(item)

            if sym:
                add_to_bucket("exact", sym, chunk)
                add_to_bucket("case_insensitive", sym.lower(), chunk)

            # Package-qualified symbols (e.g. com.isd.wms.repository.StockRepository)
            if pkg and cls:
                pkg_cls = f"{pkg}.{cls}"
                add_to_bucket("qualified", pkg_cls, chunk)
                add_to_bucket("case_insensitive", pkg_cls.lower(), chunk)
                if method:
                    pkg_method = f"{pkg}.{cls}.{method}"
                    add_to_bucket("qualified", pkg_method, chunk)
                    add_to_bucket("case_insensitive", pkg_method.lower(), chunk)

            # Class-level standalone symbol
            if cls:
                if chunk.chunk_type == "class_summary":
                    add_to_bucket("exact", cls, chunk)
                    add_to_bucket("case_insensitive", cls.lower(), chunk)

            # Inner declaration (e.g. inner record / DTO)
            if inner:
                add_to_bucket("exact", inner, chunk)
                add_to_bucket("case_insensitive", inner.lower(), chunk)

            # Unqualified method name (e.g. findAvailableStocksByProductIdAndZone)
            if method:
                add_to_bucket("unqualified", method, chunk)
                add_to_bucket("case_insensitive", method.lower(), chunk)

            # File name (e.g. StockRepository.java, V25__rename_processes_to_allocations.sql)
            if fname:
                add_to_bucket("exact", fname, chunk)
                add_to_bucket("case_insensitive", fname.lower(), chunk)

        self._symbol_cache = cache
        self._symbol_cache_count = current_count
        self._symbol_cache_rev = current_rev

    def find_symbol_declarations(self, symbol_name: str) -> List[CodeChunk]:
        """Deterministic exact lookup for symbol declarations in indexed metadata."""
        if not symbol_name or not symbol_name.strip():
            return []

        target = symbol_name.strip()
        target_lower = target.lower()

        current_rev = self._get_current_revision()
        if self._symbol_cache is None or self._symbol_cache_rev != current_rev:
            self._rebuild_symbol_cache(current_rev)


        results: List[CodeChunk] = []
        if target in self._symbol_cache["exact"]:
            results = list(self._symbol_cache["exact"][target])
        elif target in self._symbol_cache["qualified"]:
            results = list(self._symbol_cache["qualified"][target])
        elif target in self._symbol_cache["unqualified"]:
            results = list(self._symbol_cache["unqualified"][target])
        elif target_lower in self._symbol_cache["case_insensitive"]:
            results = list(self._symbol_cache["case_insensitive"][target_lower])

        if not results:
            return []

        # Deterministic ranking:
        # 1. Exact symbol_name match or exact inner_name / class match
        # 2. class_summary
        # 3. file_path, start_line
        def sort_key(c: CodeChunk):
            is_exact = 0 if (
                c.symbol_name == target
                or c.metadata.get("class") == target
                or c.metadata.get("inner_name") == target
                or c.metadata.get("method") == target
            ) else 1
            is_summary = 0 if c.chunk_type == "class_summary" else 1
            return (is_exact, is_summary, c.file_path, c.start_line)

        results.sort(key=sort_key)
        return results

