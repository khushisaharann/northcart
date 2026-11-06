package com.northcart.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * app.payment.* settings. Kept separate from AppProperties so the payment module owns
 * its configuration.
 *
 * @param provider "fake" (local simulator, default for dev/tests) or "razorpay" (sandbox/live)
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        @NotBlank @Pattern(regexp = "fake|razorpay", message = "must be 'fake' or 'razorpay'") String provider,
        @NotBlank String currency,
        Razorpay razorpay
) {
    /**
     * @param keyId     public key (rzp_test_...), safe to send to the browser
     * @param keySecret PRIVATE: server-side only, used for API auth and signature checks
     */
    public record Razorpay(String keyId, String keySecret, String baseUrl) {
    }
}
