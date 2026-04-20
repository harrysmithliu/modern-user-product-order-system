package com.example.orders.controller;

import com.example.orders.dto.ApiResponse;
import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PageResponse;
import com.example.orders.security.RequestUser;
import com.example.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            RequestUser requestUser,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ApiResponse.success(orderService.createOrder(requestUser, request));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            RequestUser requestUser,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(orderService.cancelOrder(requestUser, orderId));
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<OrderResponse> payOrder(
            RequestUser requestUser,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(orderService.payOrder(requestUser, orderId));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<OrderResponse>> myOrders(
            RequestUser requestUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(orderService.listMyOrders(requestUser, page, size));
    }
}
