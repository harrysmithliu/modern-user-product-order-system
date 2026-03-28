package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PageResponse;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.dto.ReviewOrderRequest;
import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.exception.BusinessException;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.RequestUser;
import com.example.orders.util.OrderNoGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
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

        try {
            OrderEntity entity = new OrderEntity();
            entity.setOrderNo(OrderNoGenerator.next());
            entity.setRequestNo(request.requestNo());
            entity.setUserId(requestUser.userId());
            entity.setProductId(request.productId());
            entity.setQuantity(request.quantity());
            entity.setTotalAmount(reserved.price().multiply(BigDecimal.valueOf(request.quantity())));
            entity.setStatus(OrderStatus.PENDING_APPROVAL.getCode());
            entity.setVersion(0);

            OrderEntity saved = orderRepository.save(entity);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            productClient.releaseStock(request.productId(), request.quantity());
            throw ex;
        }
    }

    @Transactional
    public OrderResponse cancelOrder(RequestUser requestUser, Long orderId) {
        OrderEntity entity = orderRepository.findByIdAndUserId(orderId, requestUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!entity.getStatus().equals(OrderStatus.PENDING_APPROVAL.getCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only pending approval orders can be cancelled");
        }

        entity.setStatus(OrderStatus.CANCELLED.getCode());
        entity.setCancelTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
        OrderEntity saved = orderRepository.save(entity);
        productClient.releaseStock(saved.getProductId(), saved.getQuantity());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMyOrders(RequestUser requestUser, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<OrderEntity> orderPage = orderRepository.findByUserId(requestUser.userId(), pageable);
        return toPageResponse(orderPage, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAllOrders(RequestUser requestUser, int page, int size, Integer status) {
        ensureAdmin(requestUser);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
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

        if (!entity.getStatus().equals(OrderStatus.PENDING_APPROVAL.getCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }

        entity.setStatus(OrderStatus.APPROVED.getCode());
        entity.setApproveTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
        return toResponse(orderRepository.save(entity));
    }

    @Transactional
    public OrderResponse reject(RequestUser requestUser, Long orderId, ReviewOrderRequest request) {
        ensureAdmin(requestUser);
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!entity.getStatus().equals(OrderStatus.PENDING_APPROVAL.getCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }

        entity.setStatus(OrderStatus.REJECTED.getCode());
        entity.setRejectReason(request.rejectReason());
        entity.setVersion(entity.getVersion() + 1);
        OrderEntity saved = orderRepository.save(entity);
        productClient.releaseStock(saved.getProductId(), saved.getQuantity());
        return toResponse(saved);
    }

    private void ensureAdmin(RequestUser requestUser) {
        if (!requestUser.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Admin role required");
        }
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
                entity.getStatus(),
                status.name(),
                entity.getRejectReason(),
                entity.getApproveTime(),
                entity.getCancelTime(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
