#!/usr/bin/env python3

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request


BASE_URL = os.getenv("SMOKE_TEST_BASE_URL", "http://127.0.0.1:8000")
COMPOSE_FILE = os.getenv("SANDBOX_COMPOSE_FILE", "infra/docker/docker-compose.sandbox.yml")
MONGO_SERVICE = os.getenv("SANDBOX_MONGO_COMPOSE_SERVICE", "mongodb")
MONGO_URI = os.getenv("SANDBOX_MONGO_URI", "mongodb://admin:admin123@mongodb:27017/admin?authSource=admin")
MONGO_DATABASE = os.getenv("SANDBOX_MONGO_DATABASE", "upo_audit")
MONGO_COLLECTION = os.getenv("SANDBOX_MONGO_COLLECTION", "order_event_timeline")
POLL_TIMEOUT_SECONDS = int(os.getenv("SMOKE_TEST_MONGO_TIMEOUT_SECONDS", "30"))
POLL_INTERVAL_SECONDS = float(os.getenv("SMOKE_TEST_MONGO_POLL_INTERVAL_SECONDS", "2"))


def request(method: str, path: str, data=None, token: str | None = None, expect_status: int = 200):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(BASE_URL + path, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    try:
        with urllib.request.urlopen(req) as response:
            payload = json.loads(response.read().decode())
            if response.getcode() != expect_status:
                raise RuntimeError(
                    f"{method} {path} returned HTTP {response.getcode()}, expected {expect_status}: {payload}"
                )
            return response.getcode(), payload
    except urllib.error.HTTPError as exc:
        payload_text = exc.read().decode()
        try:
            payload = json.loads(payload_text)
        except json.JSONDecodeError:
            payload = {"raw": payload_text}
        if exc.code != expect_status:
            raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {payload}") from exc
        return exc.code, payload


def expect(label: str, condition: bool, details: str):
    if not condition:
        raise RuntimeError(f"{label} failed: {details}")
    print(f"[OK] {label}: {details}")


def login(username: str, password: str) -> dict:
    _, response = request("POST", "/api/auth/login", {"username": username, "password": password})
    return response["data"]


def create_order(user_token: str, product_id: int) -> dict:
    _, response = request(
        "POST",
        "/api/orders",
        {
            "request_no": f"SMOKE-MONGO-{int(time.time() * 1000)}",
            "product_id": product_id,
            "quantity": 1,
        },
        user_token,
    )
    return response["data"]


def mongo_eval(expression: str) -> str:
    cmd = [
        "docker",
        "compose",
        "-f",
        COMPOSE_FILE,
        "exec",
        "-T",
        MONGO_SERVICE,
        "mongosh",
        MONGO_URI,
        "--quiet",
        "--eval",
        expression,
    ]
    result = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            "MongoDB query failed: "
            f"exit={result.returncode}, stdout={result.stdout.strip()!r}, stderr={result.stderr.strip()!r}"
        )
    return result.stdout.strip()


def mongo_find_audit(order_no: str) -> dict | None:
    order_no_js = json.dumps(order_no)
    database_js = json.dumps(MONGO_DATABASE)
    collection_js = json.dumps(MONGO_COLLECTION)
    expression = f"""
const collection = db.getSiblingDB({database_js}).getCollection({collection_js});
const doc = collection.findOne({{"routing_key": "order.created", "order_no": {order_no_js}}});
if (doc) {{
  print(JSON.stringify(doc));
}}
"""
    output = mongo_eval(expression)
    if not output:
        return None
    return json.loads(output)


def wait_for_audit_document(order_no: str) -> dict:
    deadline = time.time() + POLL_TIMEOUT_SECONDS
    last_error: str | None = None

    while time.time() < deadline:
        try:
            document = mongo_find_audit(order_no)
            if document:
                return document
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
        time.sleep(POLL_INTERVAL_SECONDS)

    suffix = f" last_error={last_error}" if last_error else ""
    raise RuntimeError(
        f"Timed out waiting for MongoDB audit record for order_no={order_no}{suffix}"
    )


def main():
    print("MongoDB audit smoke test started.")

    user_session = login("john_smith", "User@123")
    user_token = user_session["access_token"]
    expect("User login", user_session["role"] == "USER", f"role={user_session['role']}")

    _, products = request("GET", "/api/products?page=1&size=1", token=user_token)
    items = products["data"]["items"]
    expect("Product listing", len(items) > 0, f"items={len(items)}")
    product_id = items[0]["id"]

    created_order = create_order(user_token, product_id)
    expect(
        "Create order",
        created_order["status_label"] == "PENDING_APPROVAL",
        f"order_no={created_order['order_no']}",
    )

    audit_document = wait_for_audit_document(created_order["order_no"])
    expect("Mongo consumer", audit_document.get("consumer") == "notification-service", f"consumer={audit_document.get('consumer')}")
    expect("Mongo routing key", audit_document.get("routing_key") == "order.created", f"routing_key={audit_document.get('routing_key')}")
    expect("Mongo event type", audit_document.get("event_type") == "ORDER_CREATED", f"event_type={audit_document.get('event_type')}")
    expect("Mongo order number", audit_document.get("order_no") == created_order["order_no"], f"order_no={audit_document.get('order_no')}")
    expect("Mongo status code", audit_document.get("status_code") == 0, f"status_code={audit_document.get('status_code')}")
    expect("Mongo status", audit_document.get("status") == "PENDING_APPROVAL", f"status={audit_document.get('status')}")

    payload = audit_document.get("payload") or {}
    expect("Mongo payload orderNo", payload.get("orderNo") == created_order["order_no"], f"orderNo={payload.get('orderNo')}")
    expect("Mongo payload eventType", payload.get("eventType") == "ORDER_CREATED", f"eventType={payload.get('eventType')}")

    print("MongoDB audit smoke test finished successfully.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
