package com.example.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResult(
        boolean success,
        String transactionId,
        BigDecimal amount,
        Instant completedAt,
        String message
) {
}
