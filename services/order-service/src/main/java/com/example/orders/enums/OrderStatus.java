package com.example.orders.enums;

public enum OrderStatus {
    PENDING_APPROVAL(0),
    APPROVED(1),
    REJECTED(2),
    CANCELLED(3);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + code);
    }
}
