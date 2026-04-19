package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.coupon-platform")
public record CouponPlatformProperties(
        boolean enabled,
        String baseUrl,
        String issuePathTemplate,
        String claimBestPathTemplate,
        String internalToken,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
