package com.example.orders.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orders.entity.OrderCouponIssueTaskEntity;
import com.example.orders.entity.OrderEntity;
import com.example.orders.entity.OrderOutboxEntity;
import com.example.orders.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class OrderPersistenceIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @Autowired
    private OrderCouponIssueTaskRepository orderCouponIssueTaskRepository;

    @BeforeEach
    void setUp() {
        orderCouponIssueTaskRepository.deleteAll();
        orderOutboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void orderRepositoryFindsOrderByUserAndRequestNo() {
        OrderEntity order = new OrderEntity();
        order.setOrderNo("ORDER-REQ-1");
        order.setRequestNo("REQ-1");
        order.setUserId(100L);
        order.setProductId(200L);
        order.setQuantity(2);
        order.setTotalAmount(new BigDecimal("88.00"));
        order.setOriginAmount(new BigDecimal("88.00"));
        order.setStatus(OrderStatus.PAYING.getCode());
        order.setVersion(0);
        orderRepository.save(order);

        assertThat(orderRepository.findByUserIdAndRequestNo(100L, "REQ-1"))
                .isPresent()
                .get()
                .extracting(OrderEntity::getOrderNo)
                .isEqualTo("ORDER-REQ-1");
    }

    @Test
    void orderRepositoryFindsDueShippingOrders() {
        OrderEntity due = shippingOrder("ORDER-DUE-1", LocalDateTime.now().minusMinutes(10));
        OrderEntity future = shippingOrder("ORDER-FUTURE-1", LocalDateTime.now().plusHours(2));
        orderRepository.save(due);
        orderRepository.save(future);

        List<OrderEntity> dueOrders = orderRepository.findByStatusAndExpectedDeliveryTimeLessThanEqual(
                OrderStatus.SHIPPING.getCode(),
                LocalDateTime.now(),
                PageRequest.of(0, 10)
        );

        assertThat(dueOrders)
                .extracting(OrderEntity::getOrderNo)
                .containsExactly("ORDER-DUE-1");
    }

    @Test
    void outboxRepositoryReturnsPendingRowsInIdOrder() {
        OrderOutboxEntity published = outbox("message-1", "order.created", "PUBLISHED");
        OrderOutboxEntity firstPending = outbox("message-2", "order.created", "PENDING");
        OrderOutboxEntity secondPending = outbox("message-3", "order.cancelled", "PENDING");
        orderOutboxRepository.save(published);
        orderOutboxRepository.save(firstPending);
        orderOutboxRepository.save(secondPending);

        List<OrderOutboxEntity> pending = orderOutboxRepository.findTop50ByStatusOrderByIdAsc("PENDING");

        assertThat(pending)
                .extracting(OrderOutboxEntity::getMessageId)
                .containsExactly("message-2", "message-3");
    }

    @Test
    void couponIssueTaskRepositoryReturnsDuePendingRowsInIdOrder() {
        OrderCouponIssueTaskEntity future = couponTask("ORDER-TASK-1", "PENDING", LocalDateTime.now().plusMinutes(5));
        OrderCouponIssueTaskEntity dueFirst = couponTask("ORDER-TASK-2", "PENDING", LocalDateTime.now().minusMinutes(5));
        OrderCouponIssueTaskEntity failed = couponTask("ORDER-TASK-3", "FAILED", LocalDateTime.now().minusMinutes(10));
        OrderCouponIssueTaskEntity dueSecond = couponTask("ORDER-TASK-4", "PENDING", LocalDateTime.now().minusMinutes(1));
        orderCouponIssueTaskRepository.save(future);
        orderCouponIssueTaskRepository.save(dueFirst);
        orderCouponIssueTaskRepository.save(failed);
        orderCouponIssueTaskRepository.save(dueSecond);

        List<OrderCouponIssueTaskEntity> tasks =
                orderCouponIssueTaskRepository.findTop50ByStatusAndNextRetryTimeLessThanEqualOrderByIdAsc(
                        "PENDING",
                        LocalDateTime.now()
                );

        assertThat(tasks)
                .extracting(OrderCouponIssueTaskEntity::getOrderNo)
                .containsExactly("ORDER-TASK-2", "ORDER-TASK-4");
    }

    private static OrderEntity shippingOrder(String orderNo, LocalDateTime expectedDeliveryTime) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setRequestNo("REQ-" + orderNo);
        entity.setUserId(1L);
        entity.setProductId(10L);
        entity.setQuantity(1);
        entity.setTotalAmount(new BigDecimal("50.00"));
        entity.setOriginAmount(new BigDecimal("50.00"));
        entity.setStatus(OrderStatus.SHIPPING.getCode());
        entity.setShipTime(LocalDateTime.now().minusHours(1));
        entity.setExpectedDeliveryTime(expectedDeliveryTime);
        entity.setVersion(0);
        return entity;
    }

    private static OrderOutboxEntity outbox(String messageId, String routingKey, String status) {
        OrderOutboxEntity entity = new OrderOutboxEntity();
        entity.setMessageId(messageId);
        entity.setRoutingKey(routingKey);
        entity.setPayloadJson("{\"message_id\":\"" + messageId + "\"}");
        entity.setStatus(status);
        entity.setAttemptCount(0);
        return entity;
    }

    private static OrderCouponIssueTaskEntity couponTask(String orderNo, String status, LocalDateTime nextRetryTime) {
        OrderCouponIssueTaskEntity entity = new OrderCouponIssueTaskEntity();
        entity.setOrderId((long) orderNo.hashCode());
        entity.setOrderNo(orderNo);
        entity.setUserId(200L);
        entity.setOrderAmount(new BigDecimal("35.00"));
        entity.setStatus(status);
        entity.setRetryCount(0);
        entity.setNextRetryTime(nextRetryTime);
        return entity;
    }
}
