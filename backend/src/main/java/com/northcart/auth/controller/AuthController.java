package com.northcart.auth.controller;

import com.northcart.auth.dto.AuthResponse;
import com.northcart.auth.dto.ForgotPasswordRequest;
import com.northcart.auth.dto.LoginRequest;
import com.northcart.auth.dto.RegisterRequest;
import com.northcart.auth.dto.ResetPasswordRequest;
import com.northcart.auth.service.AuthService;
import com.northcart.common.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller: validate input (@Valid), delegate to the service, return a DTO.
 * No business logic and no try/catch; errors are handled by GlobalExceptionHandler.
 *
 * <p>Logout has no endpoint: tokens are stateless, so the client simply discards its token.
 * (A stolen token stays valid until it expires; refresh tokens with server-side revocation
 * are the planned upgrade.)</p>
 */
@Tag(name = "Auth", description = "Registration, login and password reset")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String RESET_REQUESTED_MESSAGE =
            "If an account exists for this email, a password reset link has been sent.";

    private final AuthService authService;

    @Operation(summary = "Register a new customer account and log in")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Log in with email and password; returns a JWT")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Email a password reset link (same response whether or not the email exists)")
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return new MessageResponse(RESET_REQUESTED_MESSAGE);
    }

    @Operation(summary = "Set a new password using the token from the reset email")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Your password has been reset. You can now log in.");
    }
}
