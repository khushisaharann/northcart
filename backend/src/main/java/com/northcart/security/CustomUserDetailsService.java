package com.northcart.security;

import com.northcart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Bridge between Spring Security and our users table.
 *
 * <ul>
 *   <li>{@link #loadUserByUsername}: used at LOGIN by Spring's DaoAuthenticationProvider,
 *       which then checks the password with BCrypt.</li>
 *   <li>{@link #loadUserById}: used on EVERY authenticated request by the JWT filter.
 *       Re-reading the user (one primary-key lookup) means a disabled account or changed
 *       roles take effect immediately, not only when the token expires.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(AuthenticatedUser::from)
                // Spring converts this into BadCredentialsException, so the client cannot
                // tell "no such email" apart from "wrong password".
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserById(Long id) {
        return userRepository.findById(id)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
