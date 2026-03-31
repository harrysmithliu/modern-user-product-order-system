package com.example.orders.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventMessage(
        String messageId,
        String eventType,
        Instant occurredAt,
        Long orderId,
        String orderNo,
        String requestNo,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalAmount,
        Integer statusCode,
        String status,
        Long operatorId,
        String operatorUsername,
        String operatorRole,
        String reason
) {
}
