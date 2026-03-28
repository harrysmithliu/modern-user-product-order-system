package com.example.orders.security;

public record RequestUser(
        Long userId,
        String username,
        String role
) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
