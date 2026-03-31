package com.example.orders.service;

import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.event.OrderEventMessage;
import com.example.orders.security.RequestUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final OrderOutboxService orderOutboxService;

    public OrderEventPublisher(OrderOutboxService orderOutboxService) {
        this.orderOutboxService = orderOutboxService;
    }

    public void publishCreated(OrderEntity order, RequestUser operator) {
        publish("order.created", "ORDER_CREATED", order, operator, null);
    }

    public void publishCancelled(OrderEntity order, RequestUser operator) {
        publish("order.cancelled", "ORDER_CANCELLED", order, operator, null);
    }

    public void publishApproved(OrderEntity order, RequestUser operator) {
        publish("order.approved", "ORDER_APPROVED", order, operator, null);
    }

    public void publishRejected(OrderEntity order, RequestUser operator, String reason) {
        publish("order.rejected", "ORDER_REJECTED", order, operator, reason);
    }

    private void publish(
            String routingKey,
            String eventType,
            OrderEntity order,
            RequestUser operator,
            String reason
    ) {
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        OrderEventMessage payload = new OrderEventMessage(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                order.getId(),
                order.getOrderNo(),
                order.getRequestNo(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus(),
                status.name(),
                operator.userId(),
                operator.username(),
                operator.role(),
                reason
        );
        orderOutboxService.enqueue(routingKey, payload);
    }
}
