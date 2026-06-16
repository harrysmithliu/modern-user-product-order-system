package com.example.orders.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.PaymentResult;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.dto.RefundResult;
import com.example.orders.entity.OrderEntity;
import com.example.orders.entity.OrderOutboxEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.repository.OrderCouponIssueTaskRepository;
import com.example.orders.repository.OrderOutboxRepository;
import com.example.orders.repository.OrderRepository;
import com.example.orders.service.CouponPlatformClient;
import com.example.orders.service.ExternalCallGuard;
import com.example.orders.service.PaymentPlatformClient;
import com.example.orders.service.ProductClient;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @Autowired
    private OrderCouponIssueTaskRepository orderCouponIssueTaskRepository;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CouponPlatformClient couponPlatformClient;

    @MockBean
    private PaymentPlatformClient paymentPlatformClient;

    @MockBean
    private ExternalCallGuard externalCallGuard;

    @BeforeEach
    void setUp() {
        orderCouponIssueTaskRepository.deleteAll();
        orderOutboxRepository.deleteAll();
        orderRepository.deleteAll();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        when(externalCallGuard.callSync(anyString(), anySupplier()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(externalCallGuard).callAsync(anyString(), any(Runnable.class), any());
    }

    @Test
    void createOrderReturnsPayingOrder() {
        when(productClient.getProduct(1001L)).thenReturn(productSnapshot(1001L, 10, new BigDecimal("99.00")));
        when(productClient.reserveStock(1001L, 2)).thenReturn(productSnapshot(1001L, 8, new BigDecimal("99.00")));

        given()
                .contentType(ContentType.JSON)
                .header("X-User-Id", "101")
                .header("X-Username", "alice")
                .header("X-User-Role", "USER")
                .body("""
                        {
                          "request_no": "REQ-API-1",
                          "product_id": 1001,
                          "quantity": 2
                        }
                        """)
        .when()
                .post("/orders")
        .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("message", equalTo("success"))
                .body("data.request_no", equalTo("REQ-API-1"))
                .body("data.user_id", equalTo(101))
                .body("data.product_id", equalTo(1001))
                .body("data.quantity", equalTo(2))
                .body("data.status", equalTo(OrderStatus.PAYING.getCode()))
                .body("data.status_label", equalTo("PAYING"))
                .body("data.total_amount", equalTo(198.00F))
                .body("data.origin_amount", equalTo(198.00F))
                .body("data.id", notNullValue());
    }

    @Test
    void payOrderReturnsPaidPendingApproval() {
        OrderEntity order = orderRepository.save(payingOrder("ORDER-API-PAY-1", 101L));
        when(couponPlatformClient.claimBestCoupon(101L, new BigDecimal("120.00"), "ORDER-API-PAY-1"))
                .thenReturn(new CouponClaimResult(
                        101L,
                        new BigDecimal("120.00"),
                        true,
                        1,
                        new BigDecimal("0.10"),
                        new BigDecimal("12.00"),
                        new BigDecimal("108.00"),
                        "claimed"
                ));
        when(paymentPlatformClient.pay("ORDER-API-PAY-1", 101L, new BigDecimal("108.00")))
                .thenReturn(new PaymentResult(
                        true,
                        "PAY-API-1",
                        new BigDecimal("108.00"),
                        Instant.parse("2026-06-16T18:00:00Z"),
                        "paid"
                ));

        given()
                .header("X-User-Id", "101")
                .header("X-Username", "alice")
                .header("X-User-Role", "USER")
        .when()
                .post("/orders/{orderId}/pay", order.getId())
        .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", equalTo(order.getId().intValue()))
                .body("data.status", equalTo(OrderStatus.PAID_PENDING_APPROVAL.getCode()))
                .body("data.status_label", equalTo("PAID_PENDING_APPROVAL"))
                .body("data.discount_amount", equalTo(12.00F))
                .body("data.final_amount", equalTo(108.00F))
                .body("data.payment_time", equalTo("2026-06-16T18:00:00"));

        assertOutboxRoutingKeys("order.created");
    }

    @Test
    void cancelOrderReturnsCancelledOrder() {
        OrderEntity order = orderRepository.save(payingOrder("ORDER-API-CANCEL-1", 101L));

        given()
                .header("X-User-Id", "101")
                .header("X-Username", "alice")
                .header("X-User-Role", "USER")
        .when()
                .post("/orders/{orderId}/cancel", order.getId())
        .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo(OrderStatus.CANCELLED.getCode()))
                .body("data.status_label", equalTo("CANCELLED"))
                .body("data.cancel_time", notNullValue());

        assertOutboxRoutingKeys("order.cancelled");
    }

    @Test
    void approveOrderReturnsShippingOrder() {
        OrderEntity order = orderRepository.save(paidPendingApprovalOrder("ORDER-API-APPROVE-1", 101L));

        given()
                .header("X-User-Id", "900")
                .header("X-Username", "admin")
                .header("X-User-Role", "ADMIN")
        .when()
                .post("/admin/orders/{orderId}/approve", order.getId())
        .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo(OrderStatus.SHIPPING.getCode()))
                .body("data.status_label", equalTo("SHIPPING"))
                .body("data.ship_time", notNullValue())
                .body("data.expected_delivery_time", notNullValue());

        assertOutboxRoutingKeys("order.approved");
    }

    @Test
    void rejectOrderReturnsRejectedOrder() {
        OrderEntity order = orderRepository.save(paidPendingApprovalOrder("ORDER-API-REJECT-1", 101L));
        when(paymentPlatformClient.refund("ORDER-API-REJECT-1", 101L, new BigDecimal("80.00"), "risk"))
                .thenReturn(new RefundResult(
                        true,
                        "REFUND-API-1",
                        new BigDecimal("80.00"),
                        Instant.parse("2026-06-16T19:00:00Z"),
                        "refunded"
                ));

        given()
                .contentType(ContentType.JSON)
                .header("X-User-Id", "900")
                .header("X-Username", "admin")
                .header("X-User-Role", "ADMIN")
                .body("""
                        {
                          "reject_reason": "risk"
                        }
                        """)
        .when()
                .post("/admin/orders/{orderId}/reject", order.getId())
        .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo(OrderStatus.REJECTED.getCode()))
                .body("data.status_label", equalTo("REJECTED"))
                .body("data.reject_reason", equalTo("risk"))
                .body("data.refund_time", equalTo("2026-06-16T19:00:00"));

        assertOutboxRoutingKeys("order.rejected");
    }

    @Test
    void missingRequestUserHeadersReturnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "request_no": "REQ-API-UNAUTH",
                          "product_id": 1001,
                          "quantity": 1
                        }
                        """)
        .when()
                .post("/orders")
        .then()
                .statusCode(401)
                .body("code", equalTo(401))
                .body("message", equalTo("Missing request user context"));
    }

    private void assertOutboxRoutingKeys(String... expectedRoutingKeys) {
        List<OrderOutboxEntity> outboxRows = orderOutboxRepository.findAll();
        org.assertj.core.api.Assertions.assertThat(outboxRows)
                .extracting(OrderOutboxEntity::getRoutingKey)
                .containsExactly(expectedRoutingKeys);
    }

    private static ProductSnapshot productSnapshot(Long productId, int stock, BigDecimal price) {
        return new ProductSnapshot(productId, "Product-" + productId, "P-" + productId, price, stock, 1);
    }

    private static OrderEntity payingOrder(String orderNo, Long userId) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setRequestNo("REQ-" + orderNo);
        entity.setUserId(userId);
        entity.setProductId(501L);
        entity.setQuantity(1);
        entity.setTotalAmount(new BigDecimal("120.00"));
        entity.setOriginAmount(new BigDecimal("120.00"));
        entity.setStatus(OrderStatus.PAYING.getCode());
        entity.setVersion(0);
        return entity;
    }

    private static OrderEntity paidPendingApprovalOrder(String orderNo, Long userId) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setRequestNo("REQ-" + orderNo);
        entity.setUserId(userId);
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
