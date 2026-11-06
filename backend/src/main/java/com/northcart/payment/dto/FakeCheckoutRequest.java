package com.northcart.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** Dev-only: "pay" a fake gateway order, like completing the Razorpay popup. */
public record FakeCheckoutRequest(@NotBlank String providerOrderId) {
}
