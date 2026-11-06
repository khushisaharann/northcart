package com.northcart.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSignaturesTest {

    @Test
    void razorpaySignatureMatchesIndependentlyComputedHmac() {
        // Expected value computed outside Java:
        // python3 -c "import hmac,hashlib; print(hmac.new(b'test_secret',
        //   b'order_DBJOWzybf0sJbb|pay_DBJOWzybf0sJbb2', hashlib.sha256).hexdigest())"
        assertThat(PaymentSignatures.razorpaySignature("order_DBJOWzybf0sJbb", "pay_DBJOWzybf0sJbb2", "test_secret"))
                .isEqualTo("f3a5f49c3e0d44aa97b563449c4ecf3eca1c1c3948310da3e5c8b8564ca9701c");
    }

    @Test
    void anyChangedInputChangesTheSignature() {
        String original = PaymentSignatures.razorpaySignature("order_A", "pay_B", "secret");
        assertThat(PaymentSignatures.razorpaySignature("order_A", "pay_C", "secret")).isNotEqualTo(original);
        assertThat(PaymentSignatures.razorpaySignature("order_A", "pay_B", "other-secret")).isNotEqualTo(original);
    }

    @Test
    void constantTimeEquals() {
        assertThat(PaymentSignatures.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(PaymentSignatures.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(PaymentSignatures.constantTimeEquals("abc", null)).isFalse();
    }

    @Test
    void amountsAreConvertedToPaise() {
        assertThat(PaymentSignatures.toMinorUnits(new BigDecimal("1598.50"))).isEqualTo(159850);
        assertThat(PaymentSignatures.toMinorUnits(new BigDecimal("49"))).isEqualTo(4900);
        assertThatThrownBy(() -> PaymentSignatures.toMinorUnits(new BigDecimal("1.005")))
                .isInstanceOf(ArithmeticException.class); // fractions of a paisa are a bug, not rounding
    }

    @Test
    void fakeGatewayVerifiesItsOwnPaymentsLikeRazorpay() {
        FakePaymentGateway gateway = new FakePaymentGateway();
        String orderId = gateway.createOrder("NC-1", BigDecimal.TEN, "INR").id();
        var paid = gateway.simulateSuccessfulPayment(orderId);

        assertThat(gateway.verifyPaymentSignature(orderId, paid.providerPaymentId(), paid.signature())).isTrue();
        assertThat(gateway.verifyPaymentSignature(orderId, "pay_forged", paid.signature())).isFalse();
        assertThat(gateway.findCapturedPaymentId(orderId)).contains(paid.providerPaymentId());
    }
}
