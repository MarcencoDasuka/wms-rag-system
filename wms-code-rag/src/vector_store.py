"""ChromaDB vector store for WMS Code Chunks."""

from pathlib import Path
from typing import Any, List, Optional, Tuple

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

    def clear(self) -> None:
        """Clear all indexed data from the collection."""
        self.client.delete_collection(name=self.collection_name)
        init_metadata = {"hnsw:space": "cosine"}
        if self.embedding_model:
            init_metadata["embedding_model"] = self.embedding_model
        self.client.get_or_create_collection(
            name=self.collection_name,
            metadata=init_metadata,
        )
