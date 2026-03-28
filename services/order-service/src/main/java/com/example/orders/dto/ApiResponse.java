package com.example.orders.dto;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, "-", Instant.now());
    }
}
