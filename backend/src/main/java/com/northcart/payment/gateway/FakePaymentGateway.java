package com.northcart.payment.gateway;

import com.northcart.payment.entity.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local stand-in for Razorpay, so the whole checkout runs without an account, keys or
 * internet (development, CI, anyone cloning the repo). It uses the SAME signature scheme
 * as Razorpay, so PaymentService runs exactly the same verification code path.
 *
 * <p>"Paying" happens through FakeCheckoutController, which plays the role of the
 * Razorpay checkout popup. State is in memory and lost on restart.</p>
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    /** Not a real secret: the fake gateway can't move money. */
    static final String FAKE_SECRET = "fake-gateway-signing-secret";

    private final Map<String, String> capturedPayments = new ConcurrentHashMap<>();

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.FAKE;
    }

    @Override
    public String publicKeyId() {
        return "rzp_test_fake";
    }

    @Override
    public GatewayOrder createOrder(String receipt, BigDecimal amount, String currency) {
        return new GatewayOrder("order_fake_" + shortId());
    }

    @Override
    public boolean verifyPaymentSignature(String providerOrderId, String providerPaymentId, String signature) {
        String expected = PaymentSignatures.razorpaySignature(providerOrderId, providerPaymentId, FAKE_SECRET);
        return PaymentSignatures.constantTimeEquals(expected, signature);
    }

    @Override
    public Optional<String> findCapturedPaymentId(String providerOrderId) {
        return Optional.ofNullable(capturedPayments.get(providerOrderId));
    }

    @Override
    public String refund(String providerPaymentId, BigDecimal amount, String currency) {
        return "rfnd_fake_" + shortId();
    }

    /**
     * Simulates the customer completing payment in the popup. Returns what Razorpay's
     * checkout would hand to the browser: payment id + signature.
     */
    public SimulatedPayment simulateSuccessfulPayment(String providerOrderId) {
        String paymentId = "pay_fake_" + shortId();
        capturedPayments.put(providerOrderId, paymentId);
        return new SimulatedPayment(providerOrderId, paymentId,
                PaymentSignatures.razorpaySignature(providerOrderId, paymentId, FAKE_SECRET));
    }

    public record SimulatedPayment(String providerOrderId, String providerPaymentId, String signature) {
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
