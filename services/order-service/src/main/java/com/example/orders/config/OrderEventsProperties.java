package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order-events")
public record OrderEventsProperties(String exchange, int outboxBatchSize) {
}
