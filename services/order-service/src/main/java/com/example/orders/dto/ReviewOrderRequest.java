package com.example.orders.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewOrderRequest(
        @NotBlank String rejectReason
) {
}
