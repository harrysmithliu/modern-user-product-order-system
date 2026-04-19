from decimal import Decimal

from app.services import product_service


def _mock_rate_limit(monkeypatch):
    monkeypatch.setattr(product_service, "increment_rate_limit", lambda _key, _window: 1)


def test_issue_coupon_thresholds(monkeypatch):
    _mock_rate_limit(monkeypatch)
    monkeypatch.setattr(product_service, "issue_coupon_balance", lambda _user_id, _coupon_type: 1)

    below = product_service.issue_coupon_for_order(None, 3, Decimal("199.99"))
    low = product_service.issue_coupon_for_order(None, 3, Decimal("200"))
    mid = product_service.issue_coupon_for_order(None, 3, Decimal("500"))
    high = product_service.issue_coupon_for_order(None, 3, Decimal("800"))

    assert below.issued is False
    assert below.coupon_type is None

    assert low.issued is True
    assert low.coupon_type == 10
    assert low.discount_rate == Decimal("0.10")

    assert mid.issued is True
    assert mid.coupon_type == 20
    assert mid.discount_rate == Decimal("0.20")

    assert high.issued is True
    assert high.coupon_type == 30
    assert high.discount_rate == Decimal("0.30")


def test_claim_best_prefers_highest_coupon(monkeypatch):
    _mock_rate_limit(monkeypatch)
    captured: dict[str, object] = {}

    def _claim(user_id: int, coupon_types: list[int]) -> int:
        captured["user_id"] = user_id
        captured["coupon_types"] = coupon_types
        return 30

    monkeypatch.setattr(product_service, "claim_best_coupon_balance", _claim)

    result = product_service.claim_best_coupon_for_order(None, 9, Decimal("950"))

    assert captured["user_id"] == 9
    assert captured["coupon_types"] == [30, 20, 10]
    assert result.claimed is True
    assert result.coupon_type == 30
    assert result.discount_rate == Decimal("0.30")
    assert result.discount_amount == Decimal("285.00")
    assert result.final_amount == Decimal("665.00")


def test_claim_best_returns_no_coupon_when_balance_empty(monkeypatch):
    _mock_rate_limit(monkeypatch)
    captured: dict[str, object] = {}

    def _claim(user_id: int, coupon_types: list[int]) -> int:
        captured["user_id"] = user_id
        captured["coupon_types"] = coupon_types
        return 0

    monkeypatch.setattr(product_service, "claim_best_coupon_balance", _claim)

    result = product_service.claim_best_coupon_for_order(None, 11, Decimal("680"))

    assert captured["user_id"] == 11
    assert captured["coupon_types"] == [20, 10]
    assert result.claimed is False
    assert result.coupon_type is None
    assert result.discount_amount == Decimal("0")
    assert result.final_amount == Decimal("680.00")
    assert result.message == "No eligible coupon available"
