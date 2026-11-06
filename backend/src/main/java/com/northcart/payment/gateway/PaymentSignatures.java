package com.northcart.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Crypto and money helpers shared by gateway implementations. */
public final class PaymentSignatures {

    private PaymentSignatures() {
    }

    /** Razorpay's payment signature: hex(HMAC_SHA256(orderId + "|" + paymentId, keySecret)). */
    public static String razorpaySignature(String orderId, String paymentId, String keySecret) {
        return hmacSha256Hex(orderId + "|" + paymentId, keySecret);
    }

    public static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /**
     * Constant-time comparison. A normal equals() returns as soon as one character differs,
     * so an attacker measuring response times could guess a valid signature byte by byte.
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    /** ₹1,598.50 -> 159850 paise. Gateways take amounts in the smallest currency unit, as integers. */
    public static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
