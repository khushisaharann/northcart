package com.northcart.order.entity;

import com.northcart.common.entity.BaseEntity;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved SQL keyword
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    /** Public, non-guessable identifier used in URLs instead of the numeric id. */
    @Column(name = "order_number", nullable = false, length = 30, updatable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter(lombok.AccessLevel.NONE) // status changes only through changeStatus()
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    // Status timestamps (V3 migration) for the order tracking timeline.
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** Recomputes subtotal and total from the line items (all BigDecimal, no rounding errors). */
    public void recalculateTotals() {
        this.subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.total = subtotal.add(shippingFee);
    }

    public void changeStatus(OrderStatus next) {
        changeStatus(next, Instant.now());
    }

    /**
     * The only way to change status; rejects transitions the state machine doesn't allow
     * and records when each step happened.
     */
    public void changeStatus(OrderStatus next, Instant at) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStateException(
                    "Order " + orderNumber + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        switch (next) {
            case CONFIRMED -> this.confirmedAt = at;
            case SHIPPED -> this.shippedAt = at;
            case DELIVERED -> this.deliveredAt = at;
            case CANCELLED -> this.cancelledAt = at;
            case PLACED -> { /* initial state, never a target */ }
        }
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }
}
