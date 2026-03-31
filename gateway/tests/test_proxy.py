import pytest
from fastapi import HTTPException

from app.core.proxy import apply_rate_limit
from app.core.security import CurrentUser


def build_request(path: str, method: str = "GET", client_host: str = "127.0.0.1"):
    return type(
        "RequestStub",
        (),
        {
            "method": method,
            "url": type("UrlStub", (), {"path": path})(),
            "client": type("ClientStub", (), {"host": client_host})(),
        },
    )()


def test_apply_rate_limit_blocks_login_after_threshold(monkeypatch):
    from app.core import proxy

    monkeypatch.setattr(proxy.settings, "login_rate_limit_max_requests", 2)
    monkeypatch.setattr(proxy, "increment_rate_limit", lambda key, window: 3)

    with pytest.raises(HTTPException) as exc:
        apply_rate_limit(build_request("/api/auth/login", method="POST"), None)

    assert exc.value.status_code == 429
    assert "Too many login attempts" in exc.value.detail


def test_apply_rate_limit_uses_user_scope_for_order_creation(monkeypatch):
    from app.core import proxy

    captured: dict[str, object] = {}

    def fake_increment(key: str, window: int):
        captured["key"] = key
        captured["window"] = window
        return 1

    monkeypatch.setattr(proxy, "increment_rate_limit", fake_increment)
    monkeypatch.setattr(proxy.settings, "order_create_rate_limit_window_seconds", 60)

    user = CurrentUser(user_id=42, username="john_smith", role="USER")
    apply_rate_limit(build_request("/api/orders", method="POST"), user)

    assert captured == {
        "key": "gateway:ratelimit:order-create:user:42",
        "window": 60,
    }
