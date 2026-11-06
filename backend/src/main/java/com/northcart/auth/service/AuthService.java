package com.northcart.auth.service;

import com.northcart.auth.dto.AuthResponse;
import com.northcart.auth.dto.ForgotPasswordRequest;
import com.northcart.auth.dto.LoginRequest;
import com.northcart.auth.dto.RegisterRequest;
import com.northcart.auth.dto.ResetPasswordRequest;
import com.northcart.auth.entity.PasswordResetToken;
import com.northcart.auth.event.PasswordResetRequestedEvent;
import com.northcart.auth.repository.PasswordResetTokenRepository;
import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.DuplicateResourceException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.config.AppProperties;
import com.northcart.security.AuthenticatedUser;
import com.northcart.security.JwtService;
import com.northcart.user.entity.Role;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import com.northcart.user.mapper.UserMapper;
import com.northcart.user.repository.RoleRepository;
import com.northcart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Registration, login and password reset.
 *
 * <p>The auth module is part of the identity core together with the user module, so it
 * uses the user repositories directly. Other modules go through UserService.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());

        // Friendly fast path. The UNIQUE constraint (uk_users_email) still guards the race
        // where two requests pass this check at the same time.
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing. Did Flyway run V2?"));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.addRole(customerRole); // ALWAYS customer; the request cannot choose a role
        userRepository.save(user);

        log.info("New customer registered: id={}", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Delegates to Spring Security: AuthenticationManager -> DaoAuthenticationProvider ->
     * CustomUserDetailsService.loadUserByUsername -> BCrypt match. Throws
     * BadCredentialsException (401) or DisabledException (403), handled globally.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        normaliseEmail(request.email()), request.password()));

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished"));
        return buildAuthResponse(user);
    }

    /**
     * Always succeeds from the caller's point of view, whether or not the email exists,
     * so this endpoint can't be used to discover which emails have accounts.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(normaliseEmail(request.email()))
                .filter(User::isEnabled)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN,
                        "This reset link is invalid or has expired"));

        User user = token.getUser();
        // Run the bulk update first; it executes as SQL immediately.
        resetTokenRepository.invalidateActiveTokens(user.getId(), now);
        token.markUsed(now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // No save() calls: both entities are managed, so Hibernate's dirty checking writes the
        // changes when the transaction commits.
        log.info("Password reset completed for user id={}", user.getId());
    }

    // ------------------------------------------------------------------

    private void issueResetToken(User user) {
        Instant now = clock.instant();
        Duration ttl = properties.passwordReset().tokenTtl();

        resetTokenRepository.invalidateActiveTokens(user.getId(), now); // only the newest link works

        String rawToken = TokenHasher.newRawToken();
        resetTokenRepository.save(new PasswordResetToken(user, TokenHasher.sha256Hex(rawToken), now.plus(ttl)));

        String link = properties.frontendUrl() + "/reset-password?token=" + rawToken;
        // Delivered to listeners only AFTER this transaction commits (see AuthEmailListener).
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                user.getEmail(), user.getFullName(), link, ttl.toMinutes()));
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpiration().toSeconds(),
                UserMapper.toResponse(user));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
