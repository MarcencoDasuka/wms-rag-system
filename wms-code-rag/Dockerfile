# High-performance, lightweight production container for WMS Codebase RAG MCP Server
FROM python:3.11-slim-bookworm AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DEBIAN_FRONTEND=noninteractive

WORKDIR /app

# Install minimal system utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies (FastMCP, ChromaDB ONNX Engine, Pydantic, Uvicorn)
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Create non-root system user for security
RUN groupadd -g 1000 appuser && \
    useradd -u 1000 -g appuser -m -s /bin/bash appuser

# Copy application files
COPY config.yaml .
COPY src/ ./src/

# Create data directory with proper ownership
RUN mkdir -p /app/data/chroma && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8000/sse || exit 1

ENTRYPOINT ["python", "src/mcp_server.py"]
CMD ["--transport", "sse", "--host", "0.0.0.0", "--port", "8000", "--auto-index"]
