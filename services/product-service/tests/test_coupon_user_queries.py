from decimal import Decimal

import pytest
from fastapi import HTTPException

from app.services import product_service


def _mock_rate_limit(monkeypatch):
    monkeypatch.setattr(product_service, "increment_rate_limit", lambda _key, _window: 1)


def test_issue_coupon_records_order_event(monkeypatch):
    _mock_rate_limit(monkeypatch)
    monkeypatch.setattr(product_service, "issue_coupon_balance", lambda _user_id, _coupon_type: 2)
    captured: dict[str, object] = {}

    def _record(user_id: int, order_no: str, payload: dict[str, object], ttl_seconds: int) -> bool:
        captured["user_id"] = user_id
        captured["order_no"] = order_no
        captured["payload"] = payload
        captured["ttl_seconds"] = ttl_seconds
        return True

    monkeypatch.setattr(product_service, "set_coupon_issue_record", _record)
    monkeypatch.setattr(product_service.settings, "coupon_order_record_ttl_seconds", 123)

    result = product_service.issue_coupon_for_order(None, 7, Decimal("520"), "ORD-001")

    assert result.issued is True
    assert result.coupon_type == 20
    assert captured["user_id"] == 7
    assert captured["order_no"] == "ORD-001"
    assert captured["ttl_seconds"] == 123
    assert captured["payload"]["coupon_type"] == 20


def test_claim_coupon_records_order_event(monkeypatch):
    _mock_rate_limit(monkeypatch)
    monkeypatch.setattr(product_service, "claim_best_coupon_balance", lambda _user_id, _types: 10)
    captured: dict[str, object] = {}

    def _record(user_id: int, order_no: str, payload: dict[str, object], ttl_seconds: int) -> bool:
        captured["user_id"] = user_id
        captured["order_no"] = order_no
        captured["payload"] = payload
        captured["ttl_seconds"] = ttl_seconds
        return True

    monkeypatch.setattr(product_service, "set_coupon_claim_record", _record)
    monkeypatch.setattr(product_service.settings, "coupon_order_record_ttl_seconds", 123)

    result = product_service.claim_best_coupon_for_order(None, 7, Decimal("680"), "ORD-002")

    assert result.claimed is True
    assert result.coupon_type == 10
    assert captured["user_id"] == 7
    assert captured["order_no"] == "ORD-002"
    assert captured["ttl_seconds"] == 123
    assert captured["payload"]["coupon_type"] == 10


def test_get_user_coupon_balances_returns_all_coupon_types(monkeypatch):
    monkeypatch.setattr(product_service, "get_coupon_balance_snapshot", lambda _user_id: {10: 2, 30: 1})

    result = product_service.get_user_coupon_balances(None, 3)

    assert result.user_id == 3
    assert [item.coupon_type for item in result.items] == [10, 20, 30]
    assert [item.quantity for item in result.items] == [2, 0, 1]


def test_get_user_coupon_issue_by_order_not_found(monkeypatch):
    monkeypatch.setattr(product_service, "get_coupon_issue_record", lambda _user_id, _order_no: None)

    with pytest.raises(HTTPException) as exc:
        product_service.get_user_coupon_issue_by_order(None, 3, "ORD-NOT-FOUND")

    assert exc.value.status_code == 404

