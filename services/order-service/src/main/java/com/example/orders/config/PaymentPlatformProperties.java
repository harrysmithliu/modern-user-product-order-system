package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-platform")
public record PaymentPlatformProperties(
        boolean enabled,
        int payDelayMs,
        int refundDelayMs,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
