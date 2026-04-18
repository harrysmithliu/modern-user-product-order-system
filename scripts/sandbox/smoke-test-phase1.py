#!/usr/bin/env python3

import json
import os
import sys
import time
import urllib.error
import urllib.request


BASE_URL = os.getenv("SMOKE_TEST_BASE_URL", "http://127.0.0.1:8000")


def request(method: str, path: str, data=None, token: str | None = None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(BASE_URL + path, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    try:
        with urllib.request.urlopen(req) as response:
            return response.getcode(), json.loads(response.read().decode())
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode()
        try:
            parsed = json.loads(payload)
        except json.JSONDecodeError:
            parsed = {"raw": payload}
        raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {parsed}") from exc


def expect(label: str, condition: bool, details: str):
    if not condition:
        raise RuntimeError(f"{label} failed: {details}")
    print(f"[OK] {label}: {details}")


def create_order(user_token: str, product_id: int, suffix: str):
    _, response = request(
        "POST",
        "/api/orders",
        {
            "request_no": f"SMOKE-{int(time.time() * 1000)}-{suffix}",
            "product_id": product_id,
            "quantity": 1,
        },
        user_token,
    )
    return response["data"]


def main():
    print("Phase 1 smoke test started.")

    _, user_login = request("POST", "/api/auth/login", {"username": "john_smith", "password": "User@123"})
    user_token = user_login["data"]["access_token"]
    expect("User login", user_login["data"]["role"] == "USER", f"role={user_login['data']['role']}")

    _, admin_login = request("POST", "/api/auth/login", {"username": "admin", "password": "Admin@123"})
    admin_token = admin_login["data"]["access_token"]
    expect("Admin login", admin_login["data"]["role"] == "ADMIN", f"role={admin_login['data']['role']}")

    _, products = request("GET", "/api/products?page=1&size=3")
    items = products["data"]["items"]
    expect("Product listing", len(items) > 0, f"items={len(items)}")
    product_id = items[0]["id"]

    cancel_order = create_order(user_token, product_id, "CANCEL")
    expect("Create order for cancel flow", cancel_order["status_label"] == "PENDING_APPROVAL", f"id={cancel_order['id']}")

    _, cancelled = request("POST", f"/api/orders/{cancel_order['id']}/cancel", token=user_token)
    expect("Cancel order", cancelled["data"]["status_label"] == "CANCELLED", f"id={cancel_order['id']}")

    approve_order = create_order(user_token, product_id, "APPROVE")
    expect("Create order for approve flow", approve_order["status_label"] == "PENDING_APPROVAL", f"id={approve_order['id']}")

    _, approved = request("POST", f"/api/admin/orders/{approve_order['id']}/approve", token=admin_token)
    expect("Approve order", approved["data"]["status_label"] == "APPROVED", f"id={approve_order['id']}")

    reject_order = create_order(user_token, product_id, "REJECT")
    expect("Create order for reject flow", reject_order["status_label"] == "PENDING_APPROVAL", f"id={reject_order['id']}")

    _, rejected = request(
        "POST",
        f"/api/admin/orders/{reject_order['id']}/reject",
        {"reject_reason": "Inventory risk needs review"},
        admin_token,
    )
    expect("Reject order", rejected["data"]["status_label"] == "REJECTED", f"id={reject_order['id']}")

    _, my_orders = request("GET", "/api/orders/my?page=1&size=5", token=user_token)
    latest_statuses = [item["status_label"] for item in my_orders["data"]["items"][:3]]
    expect(
        "Latest orders sorted desc",
        latest_statuses[:3] == ["REJECTED", "APPROVED", "CANCELLED"],
        f"latest={latest_statuses[:3]}",
    )

    _, pending_orders = request("GET", "/api/admin/orders?page=1&size=10&status=0", token=admin_token)
    pending_ids = {item["id"] for item in pending_orders["data"]["items"]}
    expect(
        "Pending queue excludes reviewed orders",
        approve_order["id"] not in pending_ids and reject_order["id"] not in pending_ids,
        f"pending_ids={sorted(pending_ids)}",
    )

    print("Phase 1 smoke test finished successfully.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
