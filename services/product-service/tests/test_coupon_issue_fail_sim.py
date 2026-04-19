from app.api import routes


def test_coupon_issue_fail_sim_disabled(monkeypatch):
    monkeypatch.setattr(routes.settings, "coupon_issue_fail_sim_enabled", False)
    monkeypatch.setattr(routes.settings, "coupon_issue_fail_sim_ratio", 0.3)
    monkeypatch.setattr(routes.random, "random", lambda: 0.0)

    assert routes._should_simulate_coupon_issue_failure() is False


def test_coupon_issue_fail_sim_enabled_probability(monkeypatch):
    monkeypatch.setattr(routes.settings, "coupon_issue_fail_sim_enabled", True)
    monkeypatch.setattr(routes.settings, "coupon_issue_fail_sim_ratio", 0.3)
    monkeypatch.setattr(routes.random, "random", lambda: 0.2)
    assert routes._should_simulate_coupon_issue_failure() is True

    monkeypatch.setattr(routes.random, "random", lambda: 0.5)
    assert routes._should_simulate_coupon_issue_failure() is False
