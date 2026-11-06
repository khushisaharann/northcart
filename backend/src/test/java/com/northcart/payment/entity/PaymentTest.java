package com.northcart.payment.entity;

import com.northcart.common.exception.InvalidStateException;
import com.northcart.order.entity.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment newPayment() {
        return new Payment(new Order(), PaymentProvider.RAZORPAY, "order_test_1", new BigDecimal("1598.00"));
    }

    @Test
    void newPaymentIsPending() {
        assertThat(newPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void markSuccessRecordsGatewayPaymentId() {
        Payment payment = newPayment();
        Instant now = Instant.now();

        payment.markSuccess("pay_abc", now);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_abc");
        assertThat(payment.getPaidAt()).isEqualTo(now);
    }

    @Test
    void aFinishedPaymentCannotChangeAgain() {
        // Protects against a replayed "verify" call or a late webhook flipping SUCCESS to FAILED.
        Payment payment = newPayment();
        payment.markSuccess("pay_abc", Instant.now());

        assertThatThrownBy(() -> payment.markFailed("late failure"))
                .isInstanceOf(InvalidStateException.class);
    }
}
