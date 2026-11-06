package com.northcart.payment.entity;

/**
 * PENDING  -> SUCCESS   signature verified (or confirmed by reconciliation)
 * PENDING  -> FAILED    order cancelled/expired before the customer paid
 * SUCCESS  -> REFUNDED  paid order cancelled, money returned
 * FAILED   -> SUCCESS   only for a "late capture": customer paid after the order expired;
 *                       it is then refunded immediately
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
