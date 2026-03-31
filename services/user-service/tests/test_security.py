from datetime import UTC, datetime, timedelta

import jwt
import pytest
from fastapi import HTTPException

from app.core import security


def test_create_access_token_contains_jti_and_identity_claims():
    token = security.create_access_token(user_id=7, username="john_smith", role="USER")
    claims = jwt.decode(token, security.settings.jwt_secret, algorithms=[security.settings.jwt_algorithm])

    assert claims["user_id"] == 7
    assert claims["username"] == "john_smith"
    assert claims["role"] == "USER"
    assert isinstance(claims["jti"], str)
    assert claims["jti"]


def test_get_current_claims_rejects_blacklisted_token(monkeypatch):
    valid_token = security.create_access_token(user_id=1, username="john_smith", role="USER")
    monkeypatch.setattr(security, "is_token_blacklisted", lambda token: token == valid_token)

    with pytest.raises(HTTPException) as exc:
        security.get_current_claims(f"Bearer {valid_token}")

    assert exc.value.status_code == 401
    assert exc.value.detail == "Access token has been revoked"


def test_revoke_token_uses_integer_expiry(monkeypatch):
    captured: dict[str, object] = {}

    def fake_blacklist_token(token: str, exp_timestamp: int):
        captured["token"] = token
        captured["exp_timestamp"] = exp_timestamp

    expires_at = datetime.now(UTC) + timedelta(minutes=5)
    monkeypatch.setattr(security, "blacklist_token", fake_blacklist_token)

    security.revoke_token("sample-token", {"exp": expires_at})

    assert captured["token"] == "sample-token"
    assert captured["exp_timestamp"] == int(expires_at.timestamp())
