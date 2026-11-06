package com.northcart.payment.entity;

import com.northcart.common.entity.BaseEntity;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per payment ATTEMPT. A customer whose card fails can retry, so an order has
 * many payments, of which at most one should end in SUCCESS.
 * Status changes only through markSuccess()/markFailed(), called after the backend has
 * verified the gateway signature - never because the frontend said so.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private PaymentProvider provider;

    @Column(name = "provider_order_id", nullable = false, length = 100, updatable = false)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public Payment(Order order, PaymentProvider provider, String providerOrderId, BigDecimal amount) {
        this.order = order;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.amount = amount;
    }

    public void markSuccess(String providerPaymentId, Instant paidAt) {
        requirePending();
        this.providerPaymentId = providerPaymentId;
        this.paidAt = paidAt;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        requirePending();
        this.failureReason = reason;
        this.status = PaymentStatus.FAILED;
    }

    /**
     * The customer's money arrived after the order was already cancelled or expired
     * (payment was FAILED by then). We must record it truthfully, then refund it.
     */
    public void recordLateCapture(String providerPaymentId, Instant paidAt) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.FAILED) {
            throw new InvalidStateException("Payment " + providerOrderId + " is already " + status);
        }
        this.providerPaymentId = providerPaymentId;
        this.paidAt = paidAt;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markRefunded(String providerRefundId, Instant refundedAt) {
        if (status != PaymentStatus.SUCCESS) {
            throw new InvalidStateException("Only a successful payment can be refunded (is " + status + ")");
        }
        this.providerRefundId = providerRefundId;
        this.refundedAt = refundedAt;
        this.status = PaymentStatus.REFUNDED;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidStateException("Payment " + providerOrderId + " is already " + status);
        }
    }
}
