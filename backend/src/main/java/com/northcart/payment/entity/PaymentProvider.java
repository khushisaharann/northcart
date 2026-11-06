package com.northcart.payment.entity;

/**
 * Which gateway processed the payment. Named "Provider" so it doesn't clash with the
 * PaymentGateway interface.
 */
public enum PaymentProvider {
    RAZORPAY,
    /** Local simulator for development and tests. Moves no real money. */
    FAKE
}
