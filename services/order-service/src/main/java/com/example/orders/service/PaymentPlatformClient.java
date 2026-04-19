package com.example.orders.service;

import com.example.orders.config.PaymentPlatformProperties;
import com.example.orders.dto.PaymentResult;
import com.example.orders.dto.RefundResult;
import com.example.orders.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentPlatformClient {

    private final PaymentPlatformProperties properties;

    public PaymentPlatformClient(PaymentPlatformProperties properties) {
        this.properties = properties;
    }

    public PaymentResult pay(String orderNo, Long userId, BigDecimal amount) {
        ensureEnabled();
        simulateDelay(properties.payDelayMs(), "pay");
        return new PaymentResult(
                true,
                "pay_" + UUID.randomUUID(),
                amount,
                Instant.now(),
                "Payment success for order_no=" + orderNo + ", user_id=" + userId
        );
    }

    public RefundResult refund(String orderNo, Long userId, BigDecimal amount, String reason) {
        ensureEnabled();
        simulateDelay(properties.refundDelayMs(), "refund");
        return new RefundResult(
                true,
                "refund_" + UUID.randomUUID(),
                amount,
                Instant.now(),
                "Refund success for order_no=" + orderNo + ", user_id=" + userId + ", reason=" + reason
        );
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Payment-platform integration is disabled");
        }
    }

    private void simulateDelay(int delayMs, String operation) {
        try {
            Thread.sleep(Math.max(delayMs, 0));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment-platform " + operation + " interrupted");
        }
    }
}
