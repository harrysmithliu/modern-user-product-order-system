package com.example.orders.service;

import com.example.orders.config.AppProperties;
import com.example.orders.dto.ProductSnapshot;
import com.example.orders.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductClient {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ProductClient(RestTemplate restTemplate, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public ProductSnapshot getProduct(Long productId) {
        String url = appProperties.baseUrl() + "/internal/products/" + productId;
        return extractProduct(restTemplate.getForEntity(url, Map.class));
    }

    public ProductSnapshot reserveStock(Long productId, int quantity) {
        String url = appProperties.baseUrl() + "/internal/products/" + productId + "/reserve";
        return extractProduct(postQuantity(url, quantity));
    }

    public ProductSnapshot releaseStock(Long productId, int quantity) {
        String url = appProperties.baseUrl() + "/internal/products/" + productId + "/release";
        return extractProduct(postQuantity(url, quantity));
    }

    private ResponseEntity<Map> postQuantity(String url, int quantity) {
        try {
            return restTemplate.postForEntity(url, Map.of("quantity", quantity), Map.class);
        } catch (RestClientException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to call product-service");
        }
    }

    @SuppressWarnings("unchecked")
    private ProductSnapshot extractProduct(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Invalid response from product-service");
        }
        Object code = response.getBody().get("code");
        if (!(code instanceof Number number) || number.intValue() != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Product-service rejected the request");
        }
        Object data = response.getBody().get("data");
        return objectMapper.convertValue(data, ProductSnapshot.class);
    }
}
