from app.main import build_audit_record, build_log_record, normalize_timestamp, persist_audit_record


def test_build_log_record_accepts_snake_case_payload():
    record = build_log_record(
        {
            "event_type": "ORDER_CREATED",
            "order_no": "ORD-1001",
            "status": "PENDING_APPROVAL",
            "user_id": 1,
            "operator_username": "john_smith",
            "occurred_at": "2026-03-30T10:00:00Z",
        },
        "order.created",
    )

    assert record == {
        "consumer": "notification-service",
        "routing_key": "order.created",
        "event_type": "ORDER_CREATED",
        "order_no": "ORD-1001",
        "status": "PENDING_APPROVAL",
        "user_id": 1,
        "operator_username": "john_smith",
        "occurred_at": "2026-03-30T10:00:00Z",
    }


def test_build_log_record_accepts_camel_case_payload():
    record = build_log_record(
        {
            "eventType": "ORDER_APPROVED",
            "orderNo": "ORD-1002",
            "status": "APPROVED",
            "userId": 8,
            "operatorUsername": "admin",
            "occurredAt": "2026-03-30T10:10:00Z",
        },
        "order.approved",
    )

    assert record == {
        "consumer": "notification-service",
        "routing_key": "order.approved",
        "event_type": "ORDER_APPROVED",
        "order_no": "ORD-1002",
        "status": "APPROVED",
        "user_id": 8,
        "operator_username": "admin",
        "occurred_at": "2026-03-30T10:10:00Z",
    }


def test_normalize_timestamp_converts_epoch_seconds_to_iso_utc():
    assert normalize_timestamp(1774964218.685016) == "2026-03-31T13:36:58.685016+00:00"


def test_build_audit_record_accepts_camel_case_payload():
    document = build_audit_record(
        {
            "messageId": "msg-1002",
            "eventType": "ORDER_APPROVED",
            "orderId": 7,
            "orderNo": "ORD-1002",
            "requestNo": "REQ-1002",
            "userId": 8,
            "productId": 17,
            "quantity": 2,
            "totalAmount": "598.00",
            "statusCode": 1,
            "status": "APPROVED",
            "operatorId": 8,
            "operatorUsername": "admin",
            "operatorRole": "ADMIN",
            "reason": None,
            "occurredAt": "2026-03-30T10:10:00Z",
        },
        "order.approved",
        consumed_at="2026-03-30T10:11:00Z",
    )

    assert document["message_id"] == "msg-1002"
    assert document["routing_key"] == "order.approved"
    assert document["event_type"] == "ORDER_APPROVED"
    assert document["order_id"] == 7
    assert document["order_no"] == "ORD-1002"
    assert document["request_no"] == "REQ-1002"
    assert document["user_id"] == 8
    assert document["product_id"] == 17
    assert document["quantity"] == 2
    assert document["total_amount"] == "598.00"
    assert document["status_code"] == 1
    assert document["status"] == "APPROVED"
    assert document["operator_id"] == 8
    assert document["operator_username"] == "admin"
    assert document["operator_role"] == "ADMIN"
    assert document["occurred_at"] == "2026-03-30T10:10:00Z"
    assert document["consumed_at"] == "2026-03-30T10:11:00Z"


def test_build_audit_record_normalizes_epoch_occurred_at():
    document = build_audit_record(
        {
            "messageId": "msg-3001",
            "eventType": "ORDER_REJECTED",
            "orderId": 16,
            "orderNo": "ORD-3001",
            "occurredAt": 1774964218.685016,
        },
        "order.rejected",
        consumed_at="2026-03-31T13:36:58.688780+00:00",
    )

    assert document["occurred_at"] == "2026-03-31T13:36:58.685016+00:00"


def test_persist_audit_record_upserts_by_message_id():
    calls: list[tuple[dict, dict, bool]] = []

    class FakeCollection:
        def update_one(self, selector, update, upsert=False):
            calls.append((selector, update, upsert))

    saved = persist_audit_record(
        FakeCollection(),
        {
            "message_id": "msg-2001",
            "event_type": "ORDER_REJECTED",
        },
    )

    assert saved is True
    assert calls == [
        (
            {"message_id": "msg-2001"},
            {"$setOnInsert": {"message_id": "msg-2001", "event_type": "ORDER_REJECTED"}},
            True,
        )
    ]
