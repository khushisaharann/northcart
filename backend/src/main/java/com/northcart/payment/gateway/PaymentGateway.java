package com.northcart.payment.gateway;

import com.northcart.payment.entity.PaymentProvider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Everything NorthCart needs from a payment provider. PaymentService depends only on this
 * interface, so switching Razorpay for Stripe means writing one new class, nothing else.
 * Exactly one implementation is active, chosen by app.payment.provider.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Public key the browser needs to open the checkout widget. Never the secret. */
    String publicKeyId();

    /** Creates a payment order for this amount at the gateway. */
    GatewayOrder createOrder(String receipt, BigDecimal amount, String currency);

    /**
     * Verifies that (orderId, paymentId) really came from the gateway: the signature is an
     * HMAC computed with our secret key, which the browser never sees.
     */
    boolean verifyPaymentSignature(String providerOrderId, String providerPaymentId, String signature);

    /**
     * Asks the gateway directly whether this order was paid (used when the customer closed
     * the browser before our /verify call arrived).
     */
    Optional<String> findCapturedPaymentId(String providerOrderId);

    /** Full refund. Returns the gateway's refund id. */
    String refund(String providerPaymentId, BigDecimal amount, String currency);
}
