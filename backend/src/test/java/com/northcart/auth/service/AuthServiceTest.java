package com.northcart.auth.service;

import com.northcart.auth.dto.ForgotPasswordRequest;
import com.northcart.auth.dto.RegisterRequest;
import com.northcart.auth.dto.ResetPasswordRequest;
import com.northcart.auth.entity.PasswordResetToken;
import com.northcart.auth.repository.PasswordResetTokenRepository;
import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.DuplicateResourceException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.config.AppProperties;
import com.northcart.security.JwtService;
import com.northcart.user.entity.Role;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import com.northcart.user.repository.RoleRepository;
import com.northcart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Service-layer unit tests with Mockito: every dependency is a mock, so we test only
 * AuthService's own logic, in milliseconds, without a database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost:5173",
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("unused", Duration.ofHours(2), "northcart"),
                new AppProperties.PasswordReset(Duration.ofMinutes(15)),
                new AppProperties.Mail("no-reply@test"),
                null,
                new AppProperties.Shipping(new java.math.BigDecimal("49.00"), new java.math.BigDecimal("999.00")),
                new AppProperties.Orders(Duration.ofMinutes(30), false));
        authService = new AuthService(userRepository, roleRepository, resetTokenRepository, passwordEncoder,
                authenticationManager, jwtService, eventPublisher, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void register_rejectsExistingEmail_withoutSaving() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("X", "Taken@Test.com", "Secret123", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_hashesPassword_normalisesEmail_andAssignsCustomerRole() {
        when(userRepository.existsByEmail("khushi@test.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$12$hashed");
        when(jwtService.generateToken(any())).thenReturn("jwt-token");
        when(jwtService.getExpiration()).thenReturn(Duration.ofHours(2));

        var response = authService.register(new RegisterRequest("  Khushi  ", " Khushi@Test.com ", "Secret123", ""));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("khushi@test.com");
        assertThat(user.getFullName()).isEqualTo("Khushi");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(user.hasRole(RoleName.CUSTOMER)).isTrue();
        assertThat(user.hasRole(RoleName.ADMIN)).isFalse();

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(7200);
    }

    @Test
    void forgotPassword_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset(new ForgotPasswordRequest("nobody@test.com"));

        verifyNoInteractions(resetTokenRepository, eventPublisher);
    }

    @Test
    void forgotPassword_disabledUser_doesNothing() {
        User disabled = new User();
        disabled.setEnabled(false);
        when(userRepository.findByEmail("off@test.com")).thenReturn(Optional.of(disabled));

        authService.requestPasswordReset(new ForgotPasswordRequest("off@test.com"));

        verifyNoInteractions(resetTokenRepository, eventPublisher);
    }

    @Test
    void resetPassword_expiredToken_isRejected() {
        PasswordResetToken expired = new PasswordResetToken(new User(), "hash", NOW.minusSeconds(1));
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("raw", "NewPass123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void tokenHasherProducesUrlSafeTokensAndStableHashes() {
        String raw = TokenHasher.newRawToken();
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(TokenHasher.sha256Hex(raw)).hasSize(64).isEqualTo(TokenHasher.sha256Hex(raw));
        assertThat(TokenHasher.newRawToken()).isNotEqualTo(raw);
    }
}
