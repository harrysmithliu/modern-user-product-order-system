package com.example.orders.dto;

import java.math.BigDecimal;

public record CouponClaimResult(
        Long userId,
        BigDecimal orderAmount,
        boolean claimed,
        Integer couponType,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String message
) {
}
