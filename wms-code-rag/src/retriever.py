"""Retriever pipeline with vector search and cross-encoder reranking."""

from typing import Any, List, Optional, Tuple

from src.chunker import CodeChunk
from src.config import AppConfig
from src.embedder import SentenceTransformerEmbedder
from src.reranker import CodeCrossEncoderReranker
from src.vector_store import CodeVectorStore


class CodeRetriever:
    """Orchestrates query embedding, vector retrieval, and cross-encoder reranking."""

    def __init__(self, config: AppConfig):
        self.config = config
        self.embedder = SentenceTransformerEmbedder.get_instance(
            model_name=config.embeddings.default_model,
            device=config.embeddings.device,
        )
        self.store = CodeVectorStore(
            persist_dir=config.vector_db.persist_dir,
            collection_name=config.vector_db.collection_name,
            embedding_model=config.embeddings.default_model,
        )
        self.reranker = None
        if config.reranking.enabled:
            self.reranker = CodeCrossEncoderReranker.get_instance(
                model_name=config.reranking.model_name,
                device=config.embeddings.device,
            )

    def retrieve(
        self,
        query: str,
        top_k: Optional[int] = None,
        top_n: Optional[int] = None,
        where_filter: Optional[dict[str, Any]] = None,
    ) -> List[Tuple[CodeChunk, float]]:
        """Executes full search and rerank pipeline."""
        k = top_k or self.config.retrieval.default_top_k
        n = top_n or self.config.reranking.top_n

        # 1. Query vectorization
        query_vector = self.embedder.embed_query(query)

        # 2. Vector search (Top-K)
        candidates = self.store.search(
            query_embedding=query_vector,
            top_k=k,
            where_filter=where_filter,
        )

        if not candidates:
            return []

        # 3. Filter by similarity threshold
        filtered = [
            (chunk, score)
            for chunk, score in candidates
            if score >= self.config.retrieval.similarity_threshold
        ]

        if not filtered:
            filtered = candidates[:k]

        # 4. Rerank using Cross-Encoder (if enabled)
        if self.reranker:
            return self.reranker.rerank(
                query=query,
                candidates=filtered,
                top_n=n,
                min_score=self.config.reranking.min_score,
            )

        return filtered[:n]

    def format_for_agent(self, results: List[Tuple[CodeChunk, float]]) -> str:
        """Formats retrieved chunks into clean markdown for agent context."""
        if not results:
            return "No relevant code or documentation found in WMS codebase."

        output = [f"### Found {len(results)} relevant code / documentation chunks:\n"]
        for i, (chunk, score) in enumerate(results, 1):
            output.append(
                f"#### [{i}] `{chunk.file_path}` (Lines {chunk.start_line}-{chunk.end_line}) | Symbol: `{chunk.symbol_name}` | Score: {score:.3f}\n"
                f"```{chunk.language}\n"
                f"{chunk.content}\n"
                f"```\n"
            )

        return "\n".join(output)
