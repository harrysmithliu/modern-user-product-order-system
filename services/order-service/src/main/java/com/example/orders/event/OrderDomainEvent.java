package com.example.orders.event;

public record OrderDomainEvent(String routingKey, OrderEventMessage payload) {
}
