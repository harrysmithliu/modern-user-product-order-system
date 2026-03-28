package com.example.orders.controller;

import com.example.orders.dto.ApiResponse;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.PageResponse;
import com.example.orders.dto.ReviewOrderRequest;
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
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> listAll(
            RequestUser requestUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status
    ) {
        return ApiResponse.success(orderService.listAllOrders(requestUser, page, size, status));
    }

    @PostMapping("/{orderId}/approve")
    public ApiResponse<OrderResponse> approve(
            RequestUser requestUser,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(orderService.approve(requestUser, orderId));
    }

    @PostMapping("/{orderId}/reject")
    public ApiResponse<OrderResponse> reject(
            RequestUser requestUser,
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewOrderRequest request
    ) {
        return ApiResponse.success(orderService.reject(requestUser, orderId, request));
    }
}
