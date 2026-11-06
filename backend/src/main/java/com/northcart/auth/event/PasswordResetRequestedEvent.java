package com.northcart.auth.event;

/**
 * Published by AuthService; consumed by the notification module, which sends the email.
 * The auth module doesn't know or care HOW the user is notified.
 */
public record PasswordResetRequestedEvent(
        String email,
        String fullName,
        String resetLink,
        long validForMinutes
) {
}
