package com.example.orders.service;

import com.example.orders.config.CouponPlatformProperties;
import com.example.orders.config.PaymentPlatformProperties;
import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PageResponse;
import com.example.orders.dto.PaymentResult;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.dto.RefundResult;
import com.example.orders.dto.ReviewOrderRequest;
import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.exception.BusinessException;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.RequestUser;
import com.example.orders.util.OrderNoGenerator;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final LocalTime BUSINESS_COMPLETE_TIME = LocalTime.of(9, 15);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventPublisher orderEventPublisher;
    private final CouponPlatformClient couponPlatformClient;
    private final PaymentPlatformClient paymentPlatformClient;
    private final CouponPlatformProperties couponPlatformProperties;
    private final PaymentPlatformProperties paymentPlatformProperties;
    private final ExternalCallGuard externalCallGuard;
    private final OrderInFlightLockManager orderInFlightLockManager;
    private final CouponIssueRetryService couponIssueRetryService;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            OrderEventPublisher orderEventPublisher,
            CouponPlatformClient couponPlatformClient,
            PaymentPlatformClient paymentPlatformClient,
            CouponPlatformProperties couponPlatformProperties,
            PaymentPlatformProperties paymentPlatformProperties,
            ExternalCallGuard externalCallGuard,
            OrderInFlightLockManager orderInFlightLockManager,
            CouponIssueRetryService couponIssueRetryService
    ) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderEventPublisher = orderEventPublisher;
        this.couponPlatformClient = couponPlatformClient;
        this.paymentPlatformClient = paymentPlatformClient;
        this.couponPlatformProperties = couponPlatformProperties;
        this.paymentPlatformProperties = paymentPlatformProperties;
        this.externalCallGuard = externalCallGuard;
        this.orderInFlightLockManager = orderInFlightLockManager;
        this.couponIssueRetryService = couponIssueRetryService;
    }

    @Transactional
    public OrderResponse createOrder(RequestUser requestUser, CreateOrderRequest request) {
        OrderEntity existing = orderRepository.findByUserIdAndRequestNo(requestUser.userId(), request.requestNo())
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        ProductSnapshot product = productClient.getProduct(request.productId());
        if (product.status() == null || product.status() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Product is off sale");
        }
        if (product.stock() == null || product.stock() < request.quantity()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Insufficient stock");
        }

        ProductSnapshot reserved = productClient.reserveStock(request.productId(), request.quantity());
        String orderNo = OrderNoGenerator.next();
        BigDecimal originAmount = reserved.price().multiply(BigDecimal.valueOf(request.quantity()));

        try {
            OrderEntity entity = new OrderEntity();
            entity.setOrderNo(orderNo);
            entity.setRequestNo(request.requestNo());
            entity.setUserId(requestUser.userId());
            entity.setProductId(request.productId());
            entity.setQuantity(request.quantity());
            entity.setTotalAmount(originAmount);
            entity.setOriginAmount(originAmount);
            entity.setDiscountAmount(null);
            entity.setFinalAmount(null);
            entity.setStatus(OrderStatus.PAYING.getCode());
            entity.setVersion(0);

            return toResponse(orderRepository.save(entity));
        } catch (RuntimeException ex) {
            productClient.releaseStock(request.productId(), request.quantity());
            throw ex;
        }
    }

    @Transactional
    public OrderResponse payOrder(RequestUser requestUser, Long orderId) {
        return orderInFlightLockManager.withOrderLock(orderId, () -> doPayOrder(requestUser, orderId));
    }

    @Transactional
    public OrderResponse cancelOrder(RequestUser requestUser, Long orderId) {
        OrderEntity entity = orderRepository.findByIdAndUserId(orderId, requestUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!isPendingApprovalStatus(entity.getStatus()) && !isPayingStatus(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only paying or pending approval orders can be cancelled");
        }

        entity.setStatus(OrderStatus.CANCELLED.getCode());
        entity.setCancelTime(LocalDateTime.now());
        OrderEntity saved = orderRepository.save(entity);
        productClient.releaseStock(saved.getProductId(), saved.getQuantity());
        orderEventPublisher.publishCancelled(saved, requestUser);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMyOrders(RequestUser requestUser, int page, int size) {
        Pageable pageable = buildPageRequest(page, size);
        Page<OrderEntity> orderPage = orderRepository.findByUserId(requestUser.userId(), pageable);
        return toPageResponse(orderPage, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAllOrders(RequestUser requestUser, int page, int size, Integer status) {
        ensureAdmin(requestUser);
        Pageable pageable = buildPageRequest(page, size);
        Page<OrderEntity> orderPage = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return toPageResponse(orderPage, page, size);
    }

    @Transactional
    public OrderResponse approve(RequestUser requestUser, Long orderId) {
        ensureAdmin(requestUser);
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!isPendingApprovalStatus(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }

        LocalDateTime shipTime = LocalDateTime.now();
        entity.setStatus(OrderStatus.SHIPPING.getCode());
        entity.setApproveTime(shipTime);
        entity.setShipTime(shipTime);
        entity.setExpectedDeliveryTime(nextWorkdayAtFixedTime(shipTime));
        OrderEntity saved = orderRepository.save(entity);
        orderEventPublisher.publishApproved(saved, requestUser);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse reject(RequestUser requestUser, Long orderId, ReviewOrderRequest request) {
        ensureAdmin(requestUser);
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!isPendingApprovalStatus(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }

        entity.setStatus(OrderStatus.REFUNDING.getCode());
        entity.setRejectReason(request.rejectReason());
        OrderEntity refunding = orderRepository.save(entity);

        BigDecimal refundAmount = resolveRefundAmount(refunding);
        RefundResult refundResult = refundOrder(
                refunding.getOrderNo(),
                refunding.getUserId(),
                refundAmount,
                request.rejectReason()
        );

        refunding.setStatus(OrderStatus.REJECTED.getCode());
        refunding.setRefundTime(toLocalDateTime(refundResult.completedAt()));
        OrderEntity saved = orderRepository.save(refunding);
        productClient.releaseStock(saved.getProductId(), saved.getQuantity());
        orderEventPublisher.publishRejected(saved, requestUser, request.rejectReason());
        return toResponse(saved);
    }

    private void ensureAdmin(RequestUser requestUser) {
        if (!requestUser.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private boolean isPendingApprovalStatus(Integer status) {
        return status != null
                && (OrderStatus.PENDING_APPROVAL.getCode() == status
                || OrderStatus.PAID_PENDING_APPROVAL.getCode() == status);
    }

    private boolean isPaidOrPendingApprovalStatus(Integer status) {
        return isPendingApprovalStatus(status);
    }

    private boolean isPayingStatus(Integer status) {
        return status != null && OrderStatus.PAYING.getCode() == status;
    }

    private LocalDateTime nextWorkdayAtFixedTime(LocalDateTime baseTime) {
        LocalDate deliveryDate = baseTime.toLocalDate().plusDays(1);
        while (deliveryDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || deliveryDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            deliveryDate = deliveryDate.plusDays(1);
        }
        return LocalDateTime.of(deliveryDate, BUSINESS_COMPLETE_TIME);
    }

    private DiscountBreakdown claimBestCoupon(Long userId, BigDecimal originAmount, String orderNo) {
        if (!couponPlatformProperties.enabled()) {
            return new DiscountBreakdown(originAmount, ZERO);
        }
        CouponClaimResult claim = externalCallGuard.callSync(
                "coupon-claim",
                () -> couponPlatformClient.claimBestCoupon(userId, originAmount, orderNo)
        );
        BigDecimal discountAmount = claim.discountAmount() == null ? ZERO : claim.discountAmount();
        BigDecimal finalAmount = claim.finalAmount() == null ? originAmount : claim.finalAmount();
        return new DiscountBreakdown(finalAmount, discountAmount);
    }

    private PaymentResult requestPayment(String orderNo, Long userId, BigDecimal finalAmount) {
        if (!paymentPlatformProperties.enabled()) {
            return new PaymentResult(true, "pay_skipped_" + orderNo, finalAmount, Instant.now(), "Payment skipped");
        }
        return externalCallGuard.callSync(
                "payment-pay",
                () -> paymentPlatformClient.pay(orderNo, userId, finalAmount)
        );
    }

    private RefundResult refundOrder(String orderNo, Long userId, BigDecimal refundAmount, String reason) {
        if (!paymentPlatformProperties.enabled()) {
            return new RefundResult(true, "refund_skipped_" + orderNo, refundAmount, Instant.now(), "Refund skipped");
        }
        return externalCallGuard.callSync(
                "payment-refund",
                () -> paymentPlatformClient.refund(orderNo, userId, refundAmount, reason)
        );
    }

    private void issueCouponAfterOrder(Long orderId, Long userId, BigDecimal orderAmount, String orderNo) {
        if (!couponPlatformProperties.enabled()) {
            return;
        }
        externalCallGuard.callAsync(
                "coupon-issue",
                () -> {
                    couponPlatformClient.issueCoupon(userId, orderAmount, orderNo);
                },
                ex -> {
                    log.warn("coupon issue failed user_id={} amount={} order_no={} error={}", userId, orderAmount, orderNo, ex.getMessage());
                    couponIssueRetryService.scheduleRetry(orderId, orderNo, userId, orderAmount, ex.getMessage());
                }
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? LocalDateTime.now() : LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private OrderEntity ensurePayableAmountPrepared(OrderEntity entity, Long userId) {
        if (entity.getFinalAmount() != null && entity.getDiscountAmount() != null && entity.getOriginAmount() != null) {
            return entity;
        }
        BigDecimal originAmount = resolveOriginAmount(entity);
        DiscountBreakdown discount = claimBestCoupon(userId, originAmount, entity.getOrderNo());
        entity.setOriginAmount(originAmount);
        entity.setDiscountAmount(discount.discountAmount());
        entity.setFinalAmount(discount.finalAmount());
        entity.setTotalAmount(discount.finalAmount());
        return orderRepository.save(entity);
    }

    private BigDecimal resolvePayableAmount(OrderEntity entity) {
        if (entity.getFinalAmount() != null) {
            return entity.getFinalAmount();
        }
        return resolveOriginAmount(entity);
    }

    private BigDecimal resolveOriginAmount(OrderEntity entity) {
        if (entity.getOriginAmount() != null) {
            return entity.getOriginAmount();
        }
        if (entity.getTotalAmount() != null) {
            return entity.getTotalAmount();
        }
        return ZERO;
    }

    private BigDecimal resolveRefundAmount(OrderEntity entity) {
        if (entity.getFinalAmount() != null) {
            return entity.getFinalAmount();
        }
        if (entity.getTotalAmount() != null) {
            return entity.getTotalAmount();
        }
        return ZERO;
    }

    private record DiscountBreakdown(BigDecimal finalAmount, BigDecimal discountAmount) {
    }

    private OrderResponse doPayOrder(RequestUser requestUser, Long orderId) {
        OrderEntity entity = orderRepository.findByIdAndUserId(orderId, requestUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (isPaidOrPendingApprovalStatus(entity.getStatus())) {
            return toResponse(entity);
        }
        if (!isPayingStatus(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Order is not waiting for payment");
        }

        entity = ensurePayableAmountPrepared(entity, requestUser.userId());
        BigDecimal finalAmount = resolvePayableAmount(entity);
        PaymentResult paymentResult = requestPayment(entity.getOrderNo(), requestUser.userId(), finalAmount);

        entity.setStatus(OrderStatus.PAID_PENDING_APPROVAL.getCode());
        entity.setPaymentTime(toLocalDateTime(paymentResult.completedAt()));
        entity.setTotalAmount(finalAmount);
        OrderEntity saved = orderRepository.save(entity);
        orderEventPublisher.publishCreated(saved, requestUser);
        issueCouponAfterOrder(saved.getId(), requestUser.userId(), finalAmount, saved.getOrderNo());
        return toResponse(saved);
    }

    private Pageable buildPageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                size,
                Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"))
        );
    }

    private PageResponse<OrderResponse> toPageResponse(Page<OrderEntity> orderPage, int page, int size) {
        List<OrderResponse> items = orderPage.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, page, size, orderPage.getTotalElements());
    }

    private OrderResponse toResponse(OrderEntity entity) {
        OrderStatus status = OrderStatus.fromCode(entity.getStatus());
        return new OrderResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getRequestNo(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getTotalAmount(),
                entity.getOriginAmount(),
                entity.getDiscountAmount(),
                entity.getFinalAmount(),
                entity.getStatus(),
                status.name(),
                entity.getRejectReason(),
                entity.getPaymentTime(),
                entity.getShipTime(),
                entity.getExpectedDeliveryTime(),
                entity.getCompleteTime(),
                entity.getRefundTime(),
                entity.getApproveTime(),
                entity.getCancelTime(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
