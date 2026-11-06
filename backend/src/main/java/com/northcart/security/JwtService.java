package com.northcart.security;

import com.northcart.config.AppProperties;
import com.northcart.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies access tokens.
 *
 * <p>A JWT has three Base64URL parts: header.payload.signature. The signature is
 * HMAC-SHA256(header + "." + payload, secretKey). Anyone can READ the payload (it is not
 * encrypted), but nobody without the key can CHANGE it without breaking the signature.
 * So: never put secrets in a JWT, and always verify the signature server-side.</p>
 *
 * <p>Payload we issue:</p>
 * <pre>
 * { "iss": "northcart", "sub": "42", "email": "khushi@example.com",
 *   "roles": ["CUSTOMER"], "iat": 1762425600, "exp": 1762432800 }
 * </pre>
 */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32; // 256 bits, required for HS256

    private final SecretKey signingKey;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(AppProperties properties, Clock clock) {
        AppProperties.Jwt jwt = properties.jwt();
        byte[] keyBytes = Decoders.BASE64.decode(jwt.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be a Base64-encoded key of at least 256 bits. "
                            + "Generate one with: openssl rand -base64 32");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = jwt.expiration();
        this.issuer = jwt.issuer();
        this.clock = clock;
        // Parser is thread-safe and reusable; build it once.
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(r -> r.getName().name()).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then returns the user id from "sub".
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired
     * @throws JwtException                       if the token is malformed or tampered with
     */
    public Long extractUserId(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("Token subject is not a user id");
        }
    }

    public Duration getExpiration() {
        return expiration;
    }
}
