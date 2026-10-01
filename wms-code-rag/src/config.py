"""Configuration management for WMS Codebase RAG MCP Server."""

import os
from pathlib import Path
from typing import Any, List, Optional

import yaml
from pydantic import BaseModel, Field


class CodebaseConfig(BaseModel):
    target_dir: str = "../inbound-storage-dispatch"
    extensions: List[str] = [
        ".java", ".vue", ".js", ".sql", ".properties", ".yaml", ".yml", ".md"
    ]
    ignore_dirs: List[str] = [
        "target", "node_modules", ".git", ".idea", ".vscode", "dist", "build", ".system_generated"
    ]


class EmbeddingsConfig(BaseModel):
    default_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    device: str = "cpu"


class RerankingConfig(BaseModel):
    enabled: bool = True
    model_name: str = "cross-encoder/ms-marco-MiniLM-L-6-v2"
    top_n: int = 4
    min_score: float = -7.0


class VectorDBConfig(BaseModel):
    persist_dir: str = "data/chroma"
    collection_name: str = "wms_codebase_knowledge"


class RetrievalConfig(BaseModel):
    default_top_k: int = 12
    similarity_threshold: float = 0.10


class ServerConfig(BaseModel):
    host: str = "127.0.0.1"
    port: int = 8000
    auth_token: Optional[str] = None


class AppConfig(BaseModel):
    codebase: CodebaseConfig = Field(default_factory=CodebaseConfig)
    embeddings: EmbeddingsConfig = Field(default_factory=EmbeddingsConfig)
    reranking: RerankingConfig = Field(default_factory=RerankingConfig)
    vector_db: VectorDBConfig = Field(default_factory=VectorDBConfig)
    retrieval: RetrievalConfig = Field(default_factory=RetrievalConfig)
    server: ServerConfig = Field(default_factory=ServerConfig)


def load_config(config_path: str = "config.yaml") -> AppConfig:
    """Load configuration from YAML file and apply environment variable overrides."""
    base_dir = Path(__file__).resolve().parent.parent
    path = Path(config_path)
    if not path.is_absolute():
        path = base_dir / config_path

    data: dict[str, Any] = {}
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}

    config = AppConfig(**data)

    # Environment overrides
    if env_target := os.environ.get("WMS_CODEBASE_PATH"):
        config.codebase.target_dir = env_target
    if env_chroma := os.environ.get("CHROMA_PERSIST_DIR"):
        config.vector_db.persist_dir = env_chroma
    if env_host := os.environ.get("MCP_HOST"):
        config.server.host = env_host
    if env_port := os.environ.get("MCP_PORT"):
        config.server.port = int(env_port)
    if env_auth := os.environ.get("MCP_AUTH_TOKEN"):
        config.server.auth_token = env_auth

    return config
