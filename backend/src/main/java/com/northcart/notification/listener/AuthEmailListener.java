package com.northcart.notification.listener;

import com.northcart.auth.event.PasswordResetRequestedEvent;
import com.northcart.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Sends auth-related emails.
 *
 * <ul>
 *   <li>AFTER_COMMIT: the email goes out only if the token was really saved. If the
 *       transaction rolls back, no email is sent with a link that doesn't work.</li>
 *   <li>@Async: runs on a background thread, so the HTTP response doesn't wait for SMTP.</li>
 *   <li>Failures are logged, never thrown: the user's request already succeeded.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            emailService.sendHtml(event.email(), "Reset your NorthCart password", "password-reset",
                    Map.of("name", event.fullName(),
                            "resetLink", event.resetLink(),
                            "validForMinutes", event.validForMinutes()));
            log.info("Password reset email sent");
        } catch (Exception e) {
            log.error("Failed to send password reset email", e);
        }
    }
}
