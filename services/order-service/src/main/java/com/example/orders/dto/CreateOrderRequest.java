package com.example.orders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotBlank String requestNo,
        @NotNull Long productId,
        @Positive int quantity
) {
}
