package com.northcart.payment.dto;

import com.northcart.payment.entity.PaymentProvider;

/**
 * Everything the browser needs to open the checkout popup (Razorpay Checkout options).
 * Contains the PUBLIC key id only; the secret never leaves the server.
 *
 * <pre>
 * { "provider": "RAZORPAY", "keyId": "rzp_test_abc", "providerOrderId": "order_Pxyz",
 *   "amount": 159800, "currency": "INR", "orderNumber": "NC-20261106-7F3K9A",
 *   "customerName": "Khushi", "customerEmail": "khushi@example.com", "customerPhone": null }
 * </pre>
 */
public record PaymentInitResponse(
        PaymentProvider provider,
        String keyId,
        String providerOrderId,
        long amount,             // smallest currency unit (paise)
        String currency,
        String orderNumber,
        String customerName,
        String customerEmail,
        String customerPhone
) {
}
