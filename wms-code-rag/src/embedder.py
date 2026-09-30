"""Embeddings module supporting both SentenceTransformers and ChromaDB native ONNX (lightweight)."""

import hashlib
import os
import threading
from abc import ABC, abstractmethod
from collections import OrderedDict
from typing import List, Optional

os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")


class BaseEmbedder(ABC):
    @property
    @abstractmethod
    def model_name(self) -> str:
        pass

    @property
    @abstractmethod
    def dimension(self) -> int:
        pass

    @abstractmethod
    def embed_texts(self, texts: List[str], batch_size: int = 32) -> List[List[float]]:
        pass

    @abstractmethod
    def embed_query(self, query: str) -> List[float]:
        pass


class SentenceTransformerEmbedder(BaseEmbedder):
    """Embedder with automatic fallback between SentenceTransformers and ChromaDB ONNX."""

    _instances: dict[str, "SentenceTransformerEmbedder"] = {}
    _lock = threading.Lock()

    def __init__(
        self,
        model_name: str = "sentence-transformers/all-MiniLM-L6-v2",
        device: Optional[str] = None,
        max_cache_size: int = 10_000,
    ):
        self._model_name = model_name
        self._device = device
        self._model = None
        self._dim: Optional[int] = 384
        self._max_cache_size = max_cache_size
        self._cache: OrderedDict[str, List[float]] = OrderedDict()
        self._cache_lock = threading.Lock()
        self._load_lock = threading.Lock()
        self._use_onnx = False

    @classmethod
    def get_instance(cls, model_name: str = "sentence-transformers/all-MiniLM-L6-v2", device: Optional[str] = None) -> "SentenceTransformerEmbedder":
        key = f"{model_name}_{device}"
        if key not in cls._instances:
            with cls._lock:
                if key not in cls._instances:
                    cls._instances[key] = cls(model_name=model_name, device=device)
        return cls._instances[key]

    def _load_model(self):
        if self._model is not None:
            return

        with self._load_lock:
            if self._model is not None:
                return

            try:
                from sentence_transformers import SentenceTransformer
                self._model = SentenceTransformer(self._model_name, device=self._device or "cpu")
                self._dim = self._model.get_sentence_embedding_dimension()
                self._use_onnx = False
            except Exception:
                # Fallback to ChromaDB's native ONNX all-MiniLM-L6-v2 embedding function
                from chromadb.utils import embedding_functions
                self._model = embedding_functions.DefaultEmbeddingFunction()
                self._dim = 384
                self._use_onnx = True

    @property
    def model_name(self) -> str:
        return self._model_name

    @property
    def dimension(self) -> int:
        return self._dim or 384

    def _get_cache_key(self, text: str) -> str:
        return hashlib.md5(text.encode("utf-8")).hexdigest()

    def embed_texts(self, texts: List[str], batch_size: int = 32) -> List[List[float]]:
        if not texts:
            return []
        self._load_model()

        results: List[Optional[List[float]]] = [None] * len(texts)
        missing_indices: List[int] = []
        missing_texts: List[str] = []

        with self._cache_lock:
            for i, text in enumerate(texts):
                key = self._get_cache_key(text)
                if key in self._cache:
                    self._cache.move_to_end(key)
                    results[i] = self._cache[key]
                else:
                    missing_indices.append(i)
                    missing_texts.append(text)

        if missing_texts:
            if self._use_onnx:
                raw_embeddings = self._model(missing_texts)
            else:
                raw_embeddings = self._model.encode(
                    missing_texts,
                    batch_size=batch_size,
                    show_progress_bar=False,
                    normalize_embeddings=True,
                )

            with self._cache_lock:
                for idx, text, emb in zip(missing_indices, missing_texts, raw_embeddings):
                    emb_list = emb.tolist() if hasattr(emb, "tolist") else list(emb)
                    results[idx] = emb_list
                    key = self._get_cache_key(text)
                    self._cache[key] = emb_list
                    if len(self._cache) > self._max_cache_size:
                        self._cache.popitem(last=False)

        return [r for r in results if r is not None]

    def embed_query(self, query: str) -> List[float]:
        embs = self.embed_texts([query])
        return embs[0]
