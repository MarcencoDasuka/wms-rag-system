import pytest
from src.mcp_server import is_session_active, SessionAutoHealMiddleware


class MockPublicSessionManager:
    def __init__(self, active_ids):
        self.active_ids = set(active_ids)

    def has_session(self, session_id: str) -> bool:
        return session_id in self.active_ids


class MockPrivateSessionManager:
    def __init__(self, active_ids):
        self._server_instances = {sid: True for sid in active_ids}


class MockEmptySessionManager:
    """Simulates a future/different session manager without private _server_instances."""
    pass


def test_is_session_active_prefers_public_api_and_gracefully_handles_unknown():
    # 1. Public API works
    pub_sm = MockPublicSessionManager(["sess-1", "sess-2"])
    assert is_session_active(pub_sm, "sess-1") is True
    assert is_session_active(pub_sm, "sess-missing") is False

    # 2. Defensive fallback works
    priv_sm = MockPrivateSessionManager(["sess-abc"])
    assert is_session_active(priv_sm, "sess-abc") is True
    assert is_session_active(priv_sm, "sess-other") is False

    # 3. Manager with _sessions or sessions attribute
    class MockSessionsListManager:
        def __init__(self, sessions):
            self.sessions = sessions
    sess_list_sm = MockSessionsListManager(["sess-xyz"])
    assert is_session_active(sess_list_sm, "sess-xyz") is True
    assert is_session_active(sess_list_sm, "sess-unknown") is False

    # 4. Empty/whitespace session_id returns False safely
    assert is_session_active(pub_sm, "") is False
    assert is_session_active(pub_sm, "   ") is False

    # 5. Future/different manager with no recognized attributes does not raise AttributeError
    empty_sm = MockEmptySessionManager()
    assert is_session_active(empty_sm, "any-id") is True

    # 6. None session manager does not crash
    assert is_session_active(None, "any-id") is True


@pytest.mark.anyio
async def test_session_auto_heal_middleware_removes_inactive_session():
    async def dummy_app(scope, receive, send):
        headers = dict(scope.get("headers", []))
        return headers

    pub_sm = MockPublicSessionManager(["active-session-123"])
    middleware = SessionAutoHealMiddleware(dummy_app, session_manager=pub_sm)

    # Case A: Active session ID header is preserved
    scope_active = {
        "type": "http",
        "headers": [(b"mcp-session-id", b"active-session-123"), (b"host", b"localhost")]
    }
    await middleware(scope_active, None, None)
    header_keys_a = [k for k, _ in scope_active["headers"]]
    assert b"mcp-session-id" in header_keys_a

    # Case B: Inactive session ID header is stripped to allow fresh session
    scope_inactive = {
        "type": "http",
        "headers": [(b"mcp-session-id", b"stale-expired-456"), (b"host", b"localhost")]
    }
    await middleware(scope_inactive, None, None)
    header_keys_b = [k for k, _ in scope_inactive["headers"]]
    assert b"mcp-session-id" not in header_keys_b

    # Case C: Middleware with None session manager does not throw AttributeError
    middleware_none = SessionAutoHealMiddleware(dummy_app, session_manager=None)
    scope_none = {
        "type": "http",
        "headers": [(b"mcp-session-id", b"some-session"), (b"host", b"localhost")]
    }
    await middleware_none(scope_none, None, None)
