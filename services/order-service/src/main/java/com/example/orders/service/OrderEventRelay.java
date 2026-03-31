package com.example.orders.service;

import com.example.orders.config.OrderEventsProperties;
import com.example.orders.event.OrderDomainEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventRelay {

    private final RabbitTemplate rabbitTemplate;
    private final OrderEventsProperties properties;

    public OrderEventRelay(RabbitTemplate rabbitTemplate, OrderEventsProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderDomainEvent event) {
        rabbitTemplate.convertAndSend(properties.exchange(), event.routingKey(), event.payload());
    }
}
