package com.northcart.order.dto;

import com.northcart.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One row of the order history page. */
public record OrderSummaryResponse(
        String orderNumber,
        OrderStatus status,
        int itemCount,
        List<String> itemPreview,   // first few product names
        BigDecimal total,
        Instant placedAt
) {
}
