package com.northcart.payment.event;

/** A payment was verified and its order confirmed. Triggers the payment receipt email. */
public record PaymentSucceededEvent(Long paymentId, Long orderId) {
}
