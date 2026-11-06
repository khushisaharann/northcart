package com.northcart.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Type-safe binding of the "app.*" block in application.yml.
 *
 * <p>Why not @Value everywhere? One validated object means a typo or a missing env var
 * fails at startup with a clear message, instead of a null showing up at runtime.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid PasswordReset passwordReset,
        @NotNull @Valid Mail mail,
        Admin admin,                     // optional: bootstrap admin account
        @NotNull @Valid Shipping shipping,
        @NotNull @Valid Orders orders
) {
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * @param secret     Base64-encoded HMAC key, at least 256 bits (32 bytes) for HS256
     * @param expiration access-token lifetime, e.g. "2h"
     * @param issuer     "iss" claim; tokens from any other issuer are rejected
     */
    public record Jwt(@NotBlank String secret, @NotNull Duration expiration, @NotBlank String issuer) {
    }

    public record PasswordReset(@NotNull Duration tokenTtl) {
    }

    public record Mail(@NotBlank @Email String from) {
    }

    /** Flat shipping fee, waived when the subtotal reaches the threshold. */
    public record Shipping(
            @NotNull @DecimalMin("0.00") BigDecimal flatFee,
            @NotNull @DecimalMin("0.00") BigDecimal freeShippingThreshold) {
    }

    /**
     * @param paymentTimeout    PLACED orders not paid within this time are cancelled and their stock released
     * @param expiryJobEnabled  turn the background expiry job off (tests call it directly)
     */
    public record Orders(@NotNull Duration paymentTimeout, boolean expiryJobEnabled) {
    }

    public record Admin(String email, String password) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
