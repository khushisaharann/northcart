package com.northcart.payment.event;

/** Money must go back (late payment for a cancelled order). Handled after commit. */
public record PaymentRefundRequestedEvent(Long paymentId) {
}
