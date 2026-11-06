package com.northcart.order.event;

/** Published after checkout. Phase 8 adds the "order confirmation" email listener. */
public record OrderPlacedEvent(Long orderId, String orderNumber) {
}
