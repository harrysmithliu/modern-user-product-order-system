package com.example.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResult(
        boolean success,
        String refundId,
        BigDecimal amount,
        Instant completedAt,
        String message
) {
}
