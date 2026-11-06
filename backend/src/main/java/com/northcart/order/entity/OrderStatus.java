package com.northcart.order.entity;

/**
 * Order lifecycle as a small state machine. Keeping the allowed transitions here (not
 * scattered across services) means an invalid jump like DELIVERED -> PLACED is impossible
 * no matter which endpoint triggers it.
 *
 * <pre>
 * PLACED ──► CONFIRMED ──► SHIPPED ──► DELIVERED
 *   │            │
 *   └────────────┴──► CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PLACED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /**
     * What an admin may move an order to from here. CONFIRMED is missing on purpose:
     * only a verified payment confirms an order.
     */
    public java.util.List<OrderStatus> adminTransitions() {
        return java.util.Arrays.stream(values())
                .filter(next -> next != CONFIRMED && canTransitionTo(next))
                .toList();
    }

    /** Customers may cancel until the parcel has left the warehouse. */
    public boolean isCancellable() {
        return this == PLACED || this == CONFIRMED;
    }
}
