package com.northcart.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Reset tokens: generated with a cryptographically secure RNG, stored only as SHA-256.
 *
 * <p>Why SHA-256 and not BCrypt like passwords? The token is 256 bits of randomness, so
 * brute-forcing the hash is impossible anyway, and we must look the token up by its hash
 * (BCrypt's random salt would make lookup impossible).</p>
 */
final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    /** 32 random bytes, URL-safe Base64 (43 chars), safe to put in a link. */
    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // never happens on a standard JVM
        }
    }
}
