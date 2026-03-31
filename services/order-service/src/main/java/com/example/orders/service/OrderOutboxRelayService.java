package com.example.orders.service;

import com.example.orders.config.OrderEventsProperties;
import com.example.orders.entity.OrderOutboxEntity;
import com.example.orders.event.OrderEventMessage;
import com.example.orders.repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelayService.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderEventsProperties properties;
    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;

    public OrderOutboxRelayService(
            RabbitTemplate rabbitTemplate,
            OrderEventsProperties properties,
            OrderOutboxRepository orderOutboxRepository,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.orderOutboxRepository = orderOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.order-events.outbox-relay-delay-ms:3000}")
    @Transactional
    public void relayPendingEvents() {
        List<OrderOutboxEntity> pendingEvents = orderOutboxRepository.findTop50ByStatusOrderByIdAsc(OrderOutboxService.STATUS_PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        int maxBatchSize = Math.max(properties.outboxBatchSize(), 1);
        pendingEvents.stream()
                .limit(maxBatchSize)
                .forEach(this::publish);
    }

    private void publish(OrderOutboxEntity outboxEntity) {
        try {
            OrderEventMessage payload = objectMapper.readValue(outboxEntity.getPayloadJson(), OrderEventMessage.class);
            rabbitTemplate.convertAndSend(properties.exchange(), outboxEntity.getRoutingKey(), payload);
            outboxEntity.setStatus(OrderOutboxService.STATUS_PUBLISHED);
            outboxEntity.setPublishedAt(LocalDateTime.now());
            outboxEntity.setLastError(null);
            outboxEntity.setAttemptCount(outboxEntity.getAttemptCount() + 1);
            log.info(
                    "outbox event published message_id={} routing_key={} outbox_id={}",
                    outboxEntity.getMessageId(),
                    outboxEntity.getRoutingKey(),
                    outboxEntity.getId()
            );
        } catch (Exception ex) {
            outboxEntity.setAttemptCount(outboxEntity.getAttemptCount() + 1);
            outboxEntity.setLastError(shortenError(ex));
            log.warn(
                    "outbox event publish failed message_id={} routing_key={} outbox_id={} error={}",
                    outboxEntity.getMessageId(),
                    outboxEntity.getRoutingKey(),
                    outboxEntity.getId(),
                    shortenError(ex)
            );
        }
    }

    private String shortenError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        if (message.length() > 500) {
            return message.substring(0, 500);
        }
        return message;
    }
}
