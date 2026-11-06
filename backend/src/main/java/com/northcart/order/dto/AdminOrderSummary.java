package com.northcart.order.dto;

import com.northcart.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the admin orders table. */
public record AdminOrderSummary(
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        int itemCount,
        BigDecimal total,
        Instant placedAt
) {
}
