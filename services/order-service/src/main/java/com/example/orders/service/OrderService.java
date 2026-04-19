package com.example.orders.service;

import com.example.orders.config.CouponPlatformProperties;
import com.example.orders.config.PaymentPlatformProperties;
import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PageResponse;
import com.example.orders.dto.PaymentResult;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.dto.ReviewOrderRequest;
import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.exception.BusinessException;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.RequestUser;
import com.example.orders.util.OrderNoGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventPublisher orderEventPublisher;
    private final CouponPlatformClient couponPlatformClient;
    private final PaymentPlatformClient paymentPlatformClient;
    private final CouponPlatformProperties couponPlatformProperties;
    private final PaymentPlatformProperties paymentPlatformProperties;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            OrderEventPublisher orderEventPublisher,
            CouponPlatformClient couponPlatformClient,
            PaymentPlatformClient paymentPlatformClient,
            CouponPlatformProperties couponPlatformProperties,
            PaymentPlatformProperties paymentPlatformProperties
    ) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderEventPublisher = orderEventPublisher;
        this.couponPlatformClient = couponPlatformClient;
        this.paymentPlatformClient = paymentPlatformClient;
        this.couponPlatformProperties = couponPlatformProperties;
        this.paymentPlatformProperties = paymentPlatformProperties;
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
        DiscountBreakdown discount = claimBestCoupon(requestUser.userId(), originAmount);
        PaymentResult paymentResult = payOrder(orderNo, requestUser.userId(), discount.finalAmount());

        try {
            OrderEntity entity = new OrderEntity();
            entity.setOrderNo(orderNo);
            entity.setRequestNo(request.requestNo());
            entity.setUserId(requestUser.userId());
            entity.setProductId(request.productId());
            entity.setQuantity(request.quantity());
            entity.setTotalAmount(discount.finalAmount());
            entity.setOriginAmount(originAmount);
            entity.setDiscountAmount(discount.discountAmount());
            entity.setFinalAmount(discount.finalAmount());
            entity.setStatus(OrderStatus.PAID_PENDING_APPROVAL.getCode());
            entity.setPaymentTime(toLocalDateTime(paymentResult.completedAt()));
            entity.setVersion(0);

            OrderEntity saved = orderRepository.save(entity);
            orderEventPublisher.publishCreated(saved, requestUser);
            issueCouponAfterOrder(requestUser.userId(), discount.finalAmount());
            return toResponse(saved);
        } catch (RuntimeException ex) {
            productClient.releaseStock(request.productId(), request.quantity());
            tryRefundAfterPersistFailure(orderNo, requestUser.userId(), discount.finalAmount());
            throw ex;
        }
    }

    @Transactional
    public OrderResponse cancelOrder(RequestUser requestUser, Long orderId) {
        OrderEntity entity = orderRepository.findByIdAndUserId(orderId, requestUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!isPendingApprovalStatus(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only pending approval orders can be cancelled");
        }

        entity.setStatus(OrderStatus.CANCELLED.getCode());
        entity.setCancelTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
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

        entity.setStatus(OrderStatus.APPROVED.getCode());
        entity.setApproveTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
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

        entity.setStatus(OrderStatus.REJECTED.getCode());
        entity.setRejectReason(request.rejectReason());
        entity.setVersion(entity.getVersion() + 1);
        OrderEntity saved = orderRepository.save(entity);
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

    private DiscountBreakdown claimBestCoupon(Long userId, BigDecimal originAmount) {
        if (!couponPlatformProperties.enabled()) {
            return new DiscountBreakdown(originAmount, ZERO);
        }
        CouponClaimResult claim = couponPlatformClient.claimBestCoupon(userId, originAmount);
        BigDecimal discountAmount = claim.discountAmount() == null ? ZERO : claim.discountAmount();
        BigDecimal finalAmount = claim.finalAmount() == null ? originAmount : claim.finalAmount();
        return new DiscountBreakdown(finalAmount, discountAmount);
    }

    private PaymentResult payOrder(String orderNo, Long userId, BigDecimal finalAmount) {
        if (!paymentPlatformProperties.enabled()) {
            return new PaymentResult(true, "pay_skipped_" + orderNo, finalAmount, Instant.now(), "Payment skipped");
        }
        return paymentPlatformClient.pay(orderNo, userId, finalAmount);
    }

    private void issueCouponAfterOrder(Long userId, BigDecimal orderAmount) {
        if (!couponPlatformProperties.enabled()) {
            return;
        }
        try {
            couponPlatformClient.issueCoupon(userId, orderAmount);
        } catch (RuntimeException ex) {
            log.warn("coupon issue failed user_id={} amount={} error={}", userId, orderAmount, ex.getMessage());
        }
    }

    private void tryRefundAfterPersistFailure(String orderNo, Long userId, BigDecimal finalAmount) {
        if (!paymentPlatformProperties.enabled()) {
            return;
        }
        try {
            paymentPlatformClient.refund(orderNo, userId, finalAmount, "order persistence failure");
        } catch (RuntimeException ex) {
            log.warn("payment refund failed order_no={} user_id={} error={}", orderNo, userId, ex.getMessage());
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? LocalDateTime.now() : LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private record DiscountBreakdown(BigDecimal finalAmount, BigDecimal discountAmount) {
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
