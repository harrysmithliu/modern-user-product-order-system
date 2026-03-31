from app.main import build_log_record


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
