#!/usr/bin/env python3

import json
import os
import sys
import urllib.error
import urllib.request


BASE_URL = os.getenv("SMOKE_TEST_BASE_URL", "http://127.0.0.1:8000")


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


def main():
    print("Phase 2 Redis smoke test started.")

    user_session = login("john_smith", "User@123")
    user_token = user_session["access_token"]
    expect("User login", user_session["role"] == "USER", f"role={user_session['role']}")

    _, me_before_logout = request("GET", "/api/users/me", token=user_token)
    expect("Authenticated profile lookup", me_before_logout["data"]["username"] == "john_smith", "profile accessible")

    _, logout_response = request("POST", "/api/auth/logout", data={}, token=user_token)
    expect("Logout", logout_response["message"] == "signed out", f"message={logout_response['message']}")

    status_after_logout, revoked_response = request(
        "GET",
        "/api/users/me",
        token=user_token,
        expect_status=401,
    )
    expect(
        "Revoked token blocked",
        status_after_logout == 401 and revoked_response["detail"] == "Access token has been revoked",
        f"detail={revoked_response.get('detail')}",
    )

    second_session = login("john_smith", "User@123")
    second_token = second_session["access_token"]
    expect("Re-login after logout", bool(second_token), "fresh access token issued")

    _, me_after_relogin = request("GET", "/api/users/me", token=second_token)
    expect(
        "Profile lookup after re-login",
        me_after_relogin["data"]["username"] == "john_smith",
        "fresh token accepted",
    )

    _, products = request("GET", "/api/products?page=1&size=2", token=second_token)
    expect("Product listing after re-login", len(products["data"]["items"]) == 2, "catalog still available")

    print("Phase 2 Redis smoke test finished successfully.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
