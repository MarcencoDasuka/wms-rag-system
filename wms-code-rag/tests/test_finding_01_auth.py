"""Regression test for Finding 1: Unauthenticated Remote MCP Access & Destructive Tool Authorization."""

import os
import pytest
from starlette.testclient import TestClient
from mcp.server.fastmcp import FastMCP
from src.config import AppConfig, ServerConfig
from src.mcp_server import create_http_app, reindex_wms_codebase


def test_auth_middleware_blocks_unauthenticated_mcp_access():
    """Verify that remote MCP endpoints reject unauthenticated access when auth_token is set."""
    server = FastMCP("test-auth-server")
    app_config = AppConfig(server=ServerConfig(auth_token="secret-token-123"))
    app = create_http_app(server, app_config)
    with TestClient(app) as client:
        # 1. Healthcheck is open for monitoring probes
        health_resp = client.get("/health")
        assert health_resp.status_code == 200
        assert health_resp.json()["status"] == "healthy"

        # 2. Unauthenticated request to /sse or / is rejected with 401
        resp_no_auth = client.get("/sse")
        assert resp_no_auth.status_code == 401
        assert "error" in resp_no_auth.json()

        # 3. Invalid token is rejected with 401
        resp_invalid_token = client.get("/sse", headers={"Authorization": "Bearer wrong-token"})
        assert resp_invalid_token.status_code == 401

        # 4. Valid Bearer token passes auth middleware
        resp_valid = client.get("/sse", headers={"Authorization": "Bearer secret-token-123"})
        assert resp_valid.status_code != 401

        # 5. Valid X-API-Key header passes auth middleware
        resp_api_key = client.get("/sse", headers={"X-API-Key": "secret-token-123"})
        assert resp_api_key.status_code != 401

        # 6. Query parameter token is REJECTED with 401 to prevent log/URL exposure (CWE-598)
        resp_query_token = client.get("/sse?token=secret-token-123")
        assert resp_query_token.status_code == 401


def test_reindex_wms_codebase_requires_confirmation_and_auth(monkeypatch):
    """Verify that destructive reindex_wms_codebase requires explicit confirmation and auth."""
    # 1. Without confirmation
    result = reindex_wms_codebase(confirm=False)
    assert "Warning" in result
    assert "destructive operation" in result

    # 2. When server has auth_token configured
    monkeypatch.setenv("MCP_AUTH_TOKEN", "secure-admin-token")
    from src import mcp_server
    mcp_server.config.server.auth_token = "secure-admin-token"

    # Unauthenticated / wrong token call
    result_unauth = reindex_wms_codebase(confirm=True, auth_token="wrong-token")
    assert "Unauthorized" in result_unauth

    # Empty token call
    result_empty = reindex_wms_codebase(confirm=True, auth_token="")
    assert "Unauthorized" in result_empty

    # Clean up
    mcp_server.config.server.auth_token = None
