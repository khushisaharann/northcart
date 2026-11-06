package com.northcart.order.event;

import com.northcart.order.entity.OrderStatus;

/**
 * Published on every status change (cancel, expiry, payment, shipping ...).
 * Listeners: status-update emails (Phase 8) and refunds for cancelled paid orders.
 */
public record OrderStatusChangedEvent(Long orderId, String orderNumber, OrderStatus from, OrderStatus to) {
}
