package com.example.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orders.config.CouponPlatformProperties;
import com.example.orders.config.PaymentPlatformProperties;
import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PaymentResult;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.dto.RefundResult;
import com.example.orders.dto.ReviewOrderRequest;
import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.exception.BusinessException;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.RequestUser;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final RequestUser USER = new RequestUser(1L, "john_smith", "USER");
    private static final RequestUser ADMIN = new RequestUser(8L, "admin", "ADMIN");
    private static final BigDecimal PRICE = new BigDecimal("100.00");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private CouponPlatformClient couponPlatformClient;

    @Mock
    private PaymentPlatformClient paymentPlatformClient;

    @Mock
    private ExternalCallGuard externalCallGuard;

    @Mock
    private OrderInFlightLockManager orderInFlightLockManager;

    @Mock
    private CouponIssueRetryService couponIssueRetryService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                productClient,
                orderEventPublisher,
                couponPlatformClient,
                paymentPlatformClient,
                new CouponPlatformProperties(true, "http://coupon", "/issue", "/claim", "token", 1000, 1000),
                new PaymentPlatformProperties(true, 3000, 5000, 1000, 2000),
                externalCallGuard,
                orderInFlightLockManager,
                couponIssueRetryService
        );

        lenient().when(externalCallGuard.callSync(anyString(), anySupplier()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(externalCallGuard).callAsync(anyString(), any(Runnable.class), any());
        lenient().when(orderInFlightLockManager.withOrderLock(anyLong(), anySupplier()))
                .thenAnswer(invocation -> invocation.<Supplier<OrderResponse>>getArgument(1).get());
        lenient().when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createOrderReturnsExistingOrderForSameRequestNo() {
        OrderEntity existing = order(11L, USER.userId(), 1001L, 2, new BigDecimal("200.00"), OrderStatus.PAYING);
        existing.setRequestNo("REQ-1");
        when(orderRepository.findByUserIdAndRequestNo(USER.userId(), "REQ-1")).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.createOrder(USER, new CreateOrderRequest("REQ-1", 1001L, 2));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.requestNo()).isEqualTo("REQ-1");
        verify(productClient, never()).getProduct(anyLong());
        verify(productClient, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    void createOrderCreatesPayingOrderAndReservesStock() {
        when(orderRepository.findByUserIdAndRequestNo(USER.userId(), "REQ-2")).thenReturn(Optional.empty());
        when(productClient.getProduct(1001L)).thenReturn(productSnapshot(1001L, 5, 1, PRICE));
        when(productClient.reserveStock(1001L, 2)).thenReturn(productSnapshot(1001L, 3, 1, PRICE));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 21L);
            return entity;
        });

        OrderResponse response = orderService.createOrder(USER, new CreateOrderRequest("REQ-2", 1001L, 2));

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.status()).isEqualTo(OrderStatus.PAYING.getCode());
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.originAmount()).isEqualByComparingTo("200.00");
        verify(productClient).getProduct(1001L);
        verify(productClient).reserveStock(1001L, 2);
    }

    @Test
    void createOrderReturnsExistingOrderWhenInsertRacesAndReleasesReservedStock() {
        OrderEntity existing = order(31L, USER.userId(), 1001L, 1, new BigDecimal("100.00"), OrderStatus.PAYING);
        existing.setRequestNo("REQ-3");
        when(orderRepository.findByUserIdAndRequestNo(USER.userId(), "REQ-3"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(productClient.getProduct(1001L)).thenReturn(productSnapshot(1001L, 5, 1, PRICE));
        when(productClient.reserveStock(1001L, 1)).thenReturn(productSnapshot(1001L, 4, 1, PRICE));
        when(orderRepository.save(any(OrderEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        OrderResponse response = orderService.createOrder(USER, new CreateOrderRequest("REQ-3", 1001L, 1));

        assertThat(response.id()).isEqualTo(31L);
        verify(productClient).releaseStock(1001L, 1);
    }

    @Test
    void payOrderClaimsCouponProcessesPaymentAndIssuesCoupon() {
        OrderEntity payingOrder = order(41L, USER.userId(), 1001L, 1, new BigDecimal("100.00"), OrderStatus.PAYING);
        payingOrder.setRequestNo("REQ-4");
        payingOrder.setOrderNo("O-41");
        when(orderRepository.findByIdAndUserId(41L, USER.userId())).thenReturn(Optional.of(payingOrder));
        when(couponPlatformClient.claimBestCoupon(USER.userId(), new BigDecimal("100.00"), "O-41"))
                .thenReturn(new CouponClaimResult(USER.userId(), new BigDecimal("100.00"), true, 1,
                        new BigDecimal("0.10"), new BigDecimal("10.00"), new BigDecimal("90.00"), "ok"));
        Instant completedAt = Instant.parse("2026-06-16T12:00:00Z");
        when(paymentPlatformClient.pay("O-41", USER.userId(), new BigDecimal("90.00")))
                .thenReturn(new PaymentResult(true, "TX-1", new BigDecimal("90.00"), completedAt, "ok"));

        OrderResponse response = orderService.payOrder(USER, 41L);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID_PENDING_APPROVAL.getCode());
        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("90.00");
        assertThat(response.paymentTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 12, 0));
        verify(orderEventPublisher).publishCreated(any(OrderEntity.class), eq(USER));
        verify(couponPlatformClient).issueCoupon(USER.userId(), new BigDecimal("90.00"), "O-41");
    }

    @Test
    void cancelOrderCancelsPayingOrderAndReleasesStock() {
        OrderEntity payingOrder = order(51L, USER.userId(), 1001L, 1, new BigDecimal("100.00"), OrderStatus.PAYING);
        when(orderRepository.findByIdAndUserId(51L, USER.userId())).thenReturn(Optional.of(payingOrder));

        OrderResponse response = orderService.cancelOrder(USER, 51L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(response.cancelTime()).isNotNull();
        verify(productClient).releaseStock(1001L, 1);
        verify(orderEventPublisher).publishCancelled(any(OrderEntity.class), eq(USER));
    }

    @Test
    void approveMarksOrderAsShippingForAdmin() {
        OrderEntity paidOrder = order(61L, USER.userId(), 1001L, 1, new BigDecimal("100.00"), OrderStatus.PAID_PENDING_APPROVAL);
        when(orderRepository.findById(61L)).thenReturn(Optional.of(paidOrder));

        OrderResponse response = orderService.approve(ADMIN, 61L);

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPING.getCode());
        assertThat(response.shipTime()).isNotNull();
        assertThat(response.expectedDeliveryTime()).isNotNull();
        assertThat(response.expectedDeliveryTime().toLocalTime()).isEqualTo(LocalTime.of(9, 15));
        assertThat(response.expectedDeliveryTime().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        verify(orderEventPublisher).publishApproved(any(OrderEntity.class), eq(ADMIN));
    }

    @Test
    void approveRejectsNonAdminUser() {
        assertThatThrownBy(() -> orderService.approve(USER, 61L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Admin role required");
    }

    @Test
    void rejectRefundsAndReleasesStock() {
        OrderEntity paidOrder = order(71L, USER.userId(), 1001L, 2, new BigDecimal("180.00"), OrderStatus.PAID_PENDING_APPROVAL);
        paidOrder.setOrderNo("O-71");
        paidOrder.setFinalAmount(new BigDecimal("180.00"));
        when(orderRepository.findById(71L)).thenReturn(Optional.of(paidOrder));
        Instant refundedAt = Instant.parse("2026-06-16T13:00:00Z");
        when(paymentPlatformClient.refund("O-71", USER.userId(), new BigDecimal("180.00"), "risk"))
                .thenReturn(new RefundResult(true, "RF-1", new BigDecimal("180.00"), refundedAt, "ok"));

        OrderResponse response = orderService.reject(ADMIN, 71L, new ReviewOrderRequest("risk"));

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED.getCode());
        assertThat(response.rejectReason()).isEqualTo("risk");
        assertThat(response.refundTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 13, 0));
        verify(productClient).releaseStock(1001L, 2);
        verify(orderEventPublisher).publishRejected(any(OrderEntity.class), eq(ADMIN), eq("risk"));
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> anySupplier() {
        return (Supplier<T>) any(Supplier.class);
    }

    private static ProductSnapshot productSnapshot(Long productId, int stock, int status, BigDecimal price) {
        return new ProductSnapshot(productId, "Product", "P-" + productId, price, stock, status);
    }

    private static OrderEntity order(
            Long id,
            Long userId,
            Long productId,
            int quantity,
            BigDecimal totalAmount,
            OrderStatus status
    ) {
        OrderEntity entity = new OrderEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setOrderNo("O-" + id);
        entity.setRequestNo("REQ-" + id);
        entity.setUserId(userId);
        entity.setProductId(productId);
        entity.setQuantity(quantity);
        entity.setTotalAmount(totalAmount);
        entity.setOriginAmount(totalAmount);
        entity.setStatus(status.getCode());
        entity.setVersion(0);
        return entity;
    }
}
