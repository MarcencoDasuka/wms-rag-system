"""Cross-Encoder reranking for code chunks with keyword-boost fallback."""

import os
import re
import threading
from typing import List, Optional, Tuple

from src.chunker import CodeChunk

os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")


class CodeCrossEncoderReranker:
    """Thread-safe Cross-Encoder reranker with graceful fallback."""

    _instances: dict[str, "CodeCrossEncoderReranker"] = {}
    _lock = threading.Lock()

    def __init__(
        self,
        model_name: str = "cross-encoder/ms-marco-MiniLM-L-6-v2",
        device: Optional[str] = None,
    ):
        self._model_name = model_name
        self._device = device
        self._model = None
        self._available = True
        self._load_lock = threading.Lock()
        self._predict_lock = threading.Lock()

    @classmethod
    def get_instance(
        cls,
        model_name: str = "cross-encoder/ms-marco-MiniLM-L-6-v2",
        device: Optional[str] = None,
    ) -> "CodeCrossEncoderReranker":
        key = f"{model_name}_{device}"
        if key not in cls._instances:
            with cls._lock:
                if key not in cls._instances:
                    cls._instances[key] = cls(model_name=model_name, device=device)
        return cls._instances[key]

    def _load_model(self):
        if self._model is None and self._available:
            with self._load_lock:
                if self._model is None and self._available:
                    try:
                        from sentence_transformers import CrossEncoder
                        self._model = CrossEncoder(self._model_name, device=self._device or "cpu")
                    except Exception:
                        self._available = False

    def rerank(
        self,
        query: str,
        candidates: List[Tuple[CodeChunk, float]],
        top_n: int = 4,
        min_score: Optional[float] = None,
    ) -> List[Tuple[CodeChunk, float]]:
        """Reranks candidate chunks using deep cross-attention or keyword-similarity boost."""
        if not candidates:
            return []

        self._load_model()
        if self._model is not None and self._available:
            try:
                pairs = [[query, chunk.content] for chunk, _ in candidates]
                with self._predict_lock:
                    scores = self._model.predict(pairs, show_progress_bar=False)

                scored = []
                for (chunk, _), score in zip(candidates, scores):
                    float_score = float(score)
                    if min_score is not None and float_score < min_score:
                        continue
                    scored.append((chunk, float_score))

                scored.sort(key=lambda x: x[1], reverse=True)
                return scored[:top_n]
            except Exception:
                pass

        # Intelligent Fallback: Vector similarity score boosted by exact term matches
        query_terms = set(re.findall(r"\w+", query.lower()))
        boosted = []
        for chunk, sim in candidates:
            content_lower = chunk.content.lower()
            term_matches = sum(1 for term in query_terms if term in content_lower)
            boost = (term_matches / max(1, len(query_terms))) * 0.15
            boosted.append((chunk, sim + boost))

        boosted.sort(key=lambda x: x[1], reverse=True)
        return boosted[:top_n]
