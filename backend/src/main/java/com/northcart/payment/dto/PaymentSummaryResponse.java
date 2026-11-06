package com.northcart.payment.dto;

import com.northcart.payment.entity.PaymentProvider;
import com.northcart.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One payment attempt, as shown to admins. */
public record PaymentSummaryResponse(
        PaymentProvider provider,
        String providerOrderId,
        String providerPaymentId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt
) {
}
