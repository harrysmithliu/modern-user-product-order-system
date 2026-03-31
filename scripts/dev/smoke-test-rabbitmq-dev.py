#!/usr/bin/env python3

import json
import os
import sys
import time
import urllib.error
import urllib.request


PRODUCT_SERVICE_URL = os.getenv("DEV_PRODUCT_SERVICE_URL", "http://127.0.0.1:8002")
ORDER_SERVICE_URL = os.getenv("DEV_ORDER_SERVICE_URL", "http://127.0.0.1:8080")

USER_HEADERS = {
    "X-User-Id": "1",
    "X-Username": "john_smith",
    "X-User-Role": "USER",
}

ADMIN_HEADERS = {
    "X-User-Id": "8",
    "X-Username": "admin",
    "X-User-Role": "ADMIN",
}


def request(base_url: str, method: str, path: str, data=None, headers: dict[str, str] | None = None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base_url + path, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        req.add_header(key, value)

    try:
        with urllib.request.urlopen(req) as response:
            return response.getcode(), json.loads(response.read().decode())
    except urllib.error.HTTPError as exc:
        payload_text = exc.read().decode()
        try:
            payload = json.loads(payload_text)
        except json.JSONDecodeError:
            payload = {"raw": payload_text}
        raise RuntimeError(f"{method} {base_url}{path} failed with HTTP {exc.code}: {payload}") from exc


def expect(label: str, condition: bool, details: str):
    if not condition:
        raise RuntimeError(f"{label} failed: {details}")
    print(f"[OK] {label}: {details}")


def create_order(product_id: int, suffix: str) -> dict:
    _, response = request(
        ORDER_SERVICE_URL,
        "POST",
        "/orders",
        {
            "request_no": f"DEV-RMQ-{int(time.time() * 1000)}-{suffix}",
            "product_id": product_id,
            "quantity": 1,
        },
        USER_HEADERS,
    )
    return response["data"]


def main():
    print("Dev RabbitMQ smoke test started.")

    _, products = request(PRODUCT_SERVICE_URL, "GET", "/products?page=1&size=3")
    items = products["data"]["items"]
    expect("Product listing", len(items) > 0, f"items={len(items)}")
    product_id = items[0]["id"]

    cancelled_order = create_order(product_id, "CANCEL")
    expect(
        "Create order for cancel flow",
        cancelled_order["status_label"] == "PENDING_APPROVAL",
        f"id={cancelled_order['id']} order_no={cancelled_order['order_no']}",
    )
    _, cancelled = request(
        ORDER_SERVICE_URL,
        "POST",
        f"/orders/{cancelled_order['id']}/cancel",
        headers=USER_HEADERS,
    )
    expect(
        "Cancel order",
        cancelled["data"]["status_label"] == "CANCELLED",
        f"order_no={cancelled['data']['order_no']}",
    )

    approved_order = create_order(product_id, "APPROVE")
    expect(
        "Create order for approve flow",
        approved_order["status_label"] == "PENDING_APPROVAL",
        f"id={approved_order['id']} order_no={approved_order['order_no']}",
    )
    _, approved = request(
        ORDER_SERVICE_URL,
        "POST",
        f"/admin/orders/{approved_order['id']}/approve",
        data={},
        headers=ADMIN_HEADERS,
    )
    expect(
        "Approve order",
        approved["data"]["status_label"] == "APPROVED",
        f"order_no={approved['data']['order_no']}",
    )

    rejected_order = create_order(product_id, "REJECT")
    expect(
        "Create order for reject flow",
        rejected_order["status_label"] == "PENDING_APPROVAL",
        f"id={rejected_order['id']} order_no={rejected_order['order_no']}",
    )
    _, rejected = request(
        ORDER_SERVICE_URL,
        "POST",
        f"/admin/orders/{rejected_order['id']}/reject",
        data={"reject_reason": "Dev RabbitMQ smoke test"},
        headers=ADMIN_HEADERS,
    )
    expect(
        "Reject order",
        rejected["data"]["status_label"] == "REJECTED",
        f"order_no={rejected['data']['order_no']}",
    )

    print("")
    print("Expected notification-service log events:")
    print(f"- order.created  for {cancelled_order['order_no']}")
    print(f"- order.cancelled for {cancelled_order['order_no']}")
    print(f"- order.created  for {approved_order['order_no']}")
    print(f"- order.approved for {approved_order['order_no']}")
    print(f"- order.created  for {rejected_order['order_no']}")
    print(f"- order.rejected for {rejected_order['order_no']}")
    print("Dev RabbitMQ smoke test finished successfully.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
