package com.example.orders.service;

import com.example.orders.entity.OrderOutboxEntity;
import com.example.orders.event.OrderEventMessage;
import com.example.orders.repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOutboxService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(OrderOutboxRepository orderOutboxRepository, ObjectMapper objectMapper) {
        this.orderOutboxRepository = orderOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String routingKey, OrderEventMessage payload) {
        OrderOutboxEntity entity = new OrderOutboxEntity();
        entity.setMessageId(payload.messageId());
        entity.setRoutingKey(routingKey);
        entity.setPayloadJson(writePayload(payload));
        entity.setStatus(STATUS_PENDING);
        entity.setAttemptCount(0);
        orderOutboxRepository.save(entity);
    }

    private String writePayload(OrderEventMessage payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize order event payload", ex);
        }
    }
}
