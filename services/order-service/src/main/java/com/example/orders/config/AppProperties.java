package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.product-service")
public record AppProperties(String baseUrl) {
}
