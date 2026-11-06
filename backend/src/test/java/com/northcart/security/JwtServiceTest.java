package com.northcart.security;

import com.northcart.config.AppProperties;
import com.northcart.user.entity.Role;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring context. */
class JwtServiceTest {

    private static final String KEY_A = "dGVzdC1vbmx5LW5vcnRoY2FydC1qd3Qta2V5LTMyLWJ5dGVzLW1pbmltdW0hIQ==";
    private static final String KEY_B = "YW5vdGhlci10ZXN0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLTMyIQ==";
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private static JwtService service(String key, Instant now) {
        AppProperties props = new AppProperties(null, null,
                new AppProperties.Jwt(key, Duration.ofHours(2), "northcart"), null, null, null, null, null);
        return new JwtService(props, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user(long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id); // id normally assigned by the database
        user.setEmail("khushi@example.com");
        user.addRole(new Role(RoleName.CUSTOMER));
        return user;
    }

    @Test
    void generatedTokenRoundTrips() {
        JwtService jwt = service(KEY_A, NOW);
        String token = jwt.generateToken(user(42));

        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        assertThat(jwt.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(KEY_A, NOW).generateToken(user(42));
        JwtService threeHoursLater = service(KEY_A, NOW.plus(Duration.ofHours(3)));

        assertThatThrownBy(() -> threeHoursLater.extractUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = service(KEY_B, NOW).generateToken(user(42));

        assertThatThrownBy(() -> service(KEY_A, NOW).extractUserId(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service(KEY_A, NOW);
        String[] victim = jwt.generateToken(user(42)).split("\\.");
        String[] attacker = jwt.generateToken(user(1)).split("\\.");

        // Attacker swaps in the payload "sub: 1" but keeps the original signature.
        String tampered = victim[0] + "." + attacker[1] + "." + victim[2];

        assertThatThrownBy(() -> jwt.extractUserId(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortKeyIsRefusedAtStartup() {
        assertThatThrownBy(() -> service("c2hvcnQta2V5", NOW)) // "short-key"
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
