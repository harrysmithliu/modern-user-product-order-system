package com.example.orders.dto;

import java.math.BigDecimal;

public record CouponIssueResult(
        Long userId,
        BigDecimal orderAmount,
        boolean issued,
        Integer couponType,
        BigDecimal discountRate,
        Integer balanceAfterIssue,
        String message
) {
}
