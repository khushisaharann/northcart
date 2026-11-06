package com.northcart.auth.service;

import com.northcart.common.validation.ValidationPatterns;
import com.northcart.config.AppProperties;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import com.northcart.user.repository.RoleRepository;
import com.northcart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first ADMIN account at startup from ADMIN_EMAIL / ADMIN_PASSWORD.
 *
 * <p>Why not a SQL migration? It would put a password hash in Git, and every environment
 * would share the same admin password. Idempotent: does nothing if the email exists.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (admin == null || !admin.isConfigured()) {
            log.info("No bootstrap admin configured (ADMIN_EMAIL / ADMIN_PASSWORD not set)");
            return;
        }
        if (admin.password().length() < ValidationPatterns.PASSWORD_MIN) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least "
                    + ValidationPatterns.PASSWORD_MIN + " characters");
        }

        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName("NorthCart Admin");
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.addRole(roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing. Did Flyway run V2?")));
        userRepository.save(user);
        log.info("Bootstrap admin account created: {}", email);
    }
}
