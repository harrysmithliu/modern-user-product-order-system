package com.example.orders.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String orderNo,
        String requestNo,
        Long userId,
        Long productId,
        int quantity,
        BigDecimal totalAmount,
        BigDecimal originAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        int status,
        String statusLabel,
        String rejectReason,
        LocalDateTime paymentTime,
        LocalDateTime shipTime,
        LocalDateTime expectedDeliveryTime,
        LocalDateTime completeTime,
        LocalDateTime refundTime,
        LocalDateTime approveTime,
        LocalDateTime cancelTime,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
