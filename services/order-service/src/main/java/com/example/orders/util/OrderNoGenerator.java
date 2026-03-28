package com.example.orders.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {
    }

    public static String next() {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "ORD" + LocalDateTime.now().format(FORMATTER) + suffix;
    }
}
