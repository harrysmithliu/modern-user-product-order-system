package com.example.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ProductSnapshot(
        Long id,
        @JsonProperty("product_name")
        String productName,
        @JsonProperty("product_code")
        String productCode,
        BigDecimal price,
        Integer stock,
        Integer status
) {
}
