package com.example.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.PaymentResult;
import com.example.orders.entity.OrderEntity;
import com.example.orders.entity.OrderOutboxEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.repository.OrderOutboxRepository;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.RequestUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderWorkflowPersistenceIT {

    private static final RequestUser USER = new RequestUser(101L, "user-a", "USER");
    private static final RequestUser ADMIN = new RequestUser(900L, "admin", "ADMIN");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CouponPlatformClient couponPlatformClient;

    @MockBean
    private PaymentPlatformClient paymentPlatformClient;

    @MockBean
    private ExternalCallGuard externalCallGuard;

    @SpyBean
    private OrderOutboxService orderOutboxService;

    @BeforeEach
    void setUp() {
        orderOutboxRepository.deleteAll();
        orderRepository.deleteAll();
        reset(orderOutboxService);

        when(externalCallGuard.callSync(anyString(), anySupplier()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(externalCallGuard).callAsync(anyString(), any(Runnable.class), any());
    }

    @Test
    void payOrderPersistsOrderUpdateAndOutboxRow() {
        OrderEntity order = payingOrder("ORDER-PAY-1");
        order = orderRepository.save(order);

        when(couponPlatformClient.claimBestCoupon(USER.userId(), new BigDecimal("120.00"), "ORDER-PAY-1"))
                .thenReturn(new CouponClaimResult(
                        USER.userId(),
                        new BigDecimal("120.00"),
                        true,
                        77,
                        new BigDecimal("0.10"),
                        new BigDecimal("12.00"),
                        new BigDecimal("108.00"),
                        "claimed"
                ));
        when(paymentPlatformClient.pay("ORDER-PAY-1", USER.userId(), new BigDecimal("108.00")))
                .thenReturn(new PaymentResult(
                        true,
                        "PAY-1",
                        new BigDecimal("108.00"),
                        Instant.parse("2026-06-16T16:00:00Z"),
                        "paid"
                ));

        orderService.payOrder(USER, order.getId());

        OrderEntity saved = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID_PENDING_APPROVAL.getCode());
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("12.00");
        assertThat(saved.getFinalAmount()).isEqualByComparingTo("108.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("108.00");
        assertThat(saved.getPaymentTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 16, 0));

        assertThat(orderOutboxRepository.findAll())
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getRoutingKey()).isEqualTo("order.created");
                    assertThat(outbox.getStatus()).isEqualTo(OrderOutboxService.STATUS_PENDING);
                    assertThat(outbox.getPayloadJson()).contains("\"event_type\":\"ORDER_CREATED\"");
                    assertThat(outbox.getPayloadJson()).contains("\"order_no\":\"ORDER-PAY-1\"");
                });
    }

    @Test
    void approveRollsBackOrderUpdateWhenOutboxWriteFails() {
        OrderEntity order = paidPendingApprovalOrder("ORDER-APPROVE-1");
        order = orderRepository.save(order);
        Long orderId = order.getId();
        doThrow(new IllegalStateException("outbox down"))
                .when(orderOutboxService)
                .enqueue(eq("order.approved"), any());

        assertThatThrownBy(() -> orderService.approve(ADMIN, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox down");

        OrderEntity reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID_PENDING_APPROVAL.getCode());
        assertThat(reloaded.getApproveTime()).isNull();
        assertThat(reloaded.getShipTime()).isNull();
        assertThat(reloaded.getExpectedDeliveryTime()).isNull();
        assertThat(orderOutboxRepository.findAll()).isEmpty();
    }

    private static OrderEntity payingOrder(String orderNo) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setRequestNo("REQ-" + orderNo);
        entity.setUserId(USER.userId());
        entity.setProductId(501L);
        entity.setQuantity(1);
        entity.setTotalAmount(new BigDecimal("120.00"));
        entity.setOriginAmount(new BigDecimal("120.00"));
        entity.setStatus(OrderStatus.PAYING.getCode());
        entity.setVersion(0);
        return entity;
    }

    private static OrderEntity paidPendingApprovalOrder(String orderNo) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setRequestNo("REQ-" + orderNo);
        entity.setUserId(USER.userId());
        entity.setProductId(502L);
        entity.setQuantity(1);
        entity.setTotalAmount(new BigDecimal("80.00"));
        entity.setOriginAmount(new BigDecimal("80.00"));
        entity.setFinalAmount(new BigDecimal("80.00"));
        entity.setDiscountAmount(BigDecimal.ZERO);
        entity.setStatus(OrderStatus.PAID_PENDING_APPROVAL.getCode());
        entity.setVersion(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> anySupplier() {
        return (Supplier<T>) any(Supplier.class);
    }
}
