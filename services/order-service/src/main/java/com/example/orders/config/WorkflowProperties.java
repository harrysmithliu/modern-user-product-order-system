package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.workflow")
public record WorkflowProperties(
        int executorCorePoolSize,
        int executorMaxPoolSize,
        int executorQueueCapacity,
        int externalSemaphorePermits,
        int semaphoreAcquireTimeoutMs,
        int couponIssueRetryFixedDelayMs,
        int couponIssueRetryDelaySeconds,
        int couponIssueMaxRetries
) {
}
