package com.example.orders.service;

import com.example.orders.config.CouponPlatformProperties;
import com.example.orders.dto.CouponClaimResult;
import com.example.orders.dto.CouponIssueResult;
import com.example.orders.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CouponPlatformClient {

    private final RestTemplate restTemplate;
    private final CouponPlatformProperties properties;
    private final ObjectMapper objectMapper;

    public CouponPlatformClient(
            RestTemplate restTemplate,
            CouponPlatformProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CouponIssueResult issueCoupon(Long userId, BigDecimal orderAmount, String orderNo) {
        ensureEnabled();
        String url = buildUrl(properties.issuePathTemplate(), userId);
        ResponseEntity<Map> response = post(url, orderAmount, orderNo);
        return extractResponseData(response, CouponIssueResult.class);
    }

    public CouponClaimResult claimBestCoupon(Long userId, BigDecimal orderAmount, String orderNo) {
        ensureEnabled();
        String url = buildUrl(properties.claimBestPathTemplate(), userId);
        ResponseEntity<Map> response = post(url, orderAmount, orderNo);
        return extractResponseData(response, CouponClaimResult.class);
    }

    private ResponseEntity<Map> post(String url, BigDecimal orderAmount, String orderNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", properties.internalToken());
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_amount", orderAmount);
        if (orderNo != null && !orderNo.isBlank()) {
            payload.put("order_no", orderNo);
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        try {
            return restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        } catch (HttpStatusCodeException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            String detail = extractErrorDetail(ex.getResponseBodyAsString());
            throw new BusinessException(
                    status == null ? HttpStatus.BAD_GATEWAY : status,
                    "Coupon-platform request failed: " + detail
            );
        } catch (RestClientException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Coupon-platform unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T extractResponseData(ResponseEntity<Map> response, Class<T> targetType) {
        Map<String, Object> body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Invalid response from coupon-platform");
        }
        Object code = body.get("code");
        if (!(code instanceof Number number) || number.intValue() != 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Coupon-platform rejected the request");
        }
        Object data = body.get("data");
        if (!(data instanceof Map<?, ?>)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Invalid response data from coupon-platform");
        }
        return objectMapper.convertValue(data, targetType);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Coupon-platform integration is disabled");
        }
        if (properties.internalToken() == null || properties.internalToken().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Coupon-platform internal token is not configured");
        }
    }

    private String buildUrl(String pathTemplate, Long userId) {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String relative = String.format(pathTemplate, userId);
        if (!relative.startsWith("/")) {
            relative = "/" + relative;
        }
        return baseUrl + relative;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Coupon-platform base url is not configured");
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String extractErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty error body";
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(responseBody, Map.class);
            Object detail = payload.get("detail");
            if (detail != null) {
                return detail.toString();
            }
        } catch (Exception ignored) {
            // best-effort parsing only
        }
        return responseBody;
    }

}
