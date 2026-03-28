package com.example.orders.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "order-service");
    }

    @GetMapping("/ready")
    public Map<String, String> ready() {
        return Map.of("status", "READY", "service", "order-service");
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "LIVE", "service", "order-service");
    }
}
