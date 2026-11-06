package com.northcart.order.dto;

import com.northcart.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order details + tracking timeline. A null timestamp means that step hasn't happened.
 * cancellable tells the UI whether to show the Cancel button (the server re-checks).
 */
public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal total,
        ShippingAddressResponse shippingAddress,
        Instant placedAt,
        Instant confirmedAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        boolean cancellable
) {
}
