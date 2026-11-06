package com.northcart.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What Razorpay's checkout hands the browser after a successful payment
 * (razorpay_order_id, razorpay_payment_id, razorpay_signature). The browser forwards it;
 * the server decides whether it's genuine by recomputing the signature.
 */
public record VerifyPaymentRequest(
        @Schema(example = "order_Pxyz123") @NotBlank @Size(max = 100) String providerOrderId,
        @Schema(example = "pay_Qabc456") @NotBlank @Size(max = 100) String providerPaymentId,
        @Schema(description = "HMAC-SHA256 hex from the gateway") @NotBlank @Size(max = 128) String signature
) {
}
