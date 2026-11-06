package com.northcart.security;

import com.northcart.user.entity.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The logged-in user as Spring Security sees it (the "principal").
 *
 * <p>A lightweight, detached copy of the User entity: safe to keep in the
 * SecurityContext for the whole request without holding a Hibernate session open.
 * Controllers receive it with {@code @AuthenticationPrincipal AuthenticatedUser me}.</p>
 *
 * <p>CredentialsContainer: Spring Security calls eraseCredentials() after login so the
 * password hash does not linger in memory.</p>
 */
public class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private String passwordHash;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;

    private AuthenticatedUser(Long id, String email, String passwordHash, boolean enabled,
                              Set<GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static AuthenticatedUser from(User user) {
        // Spring's hasRole("ADMIN") checks for the authority "ROLE_ADMIN".
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.isEnabled(), authorities);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        // Never include the password hash in logs.
        return "AuthenticatedUser[id=" + id + ", email=" + email + ", authorities=" + authorities + "]";
    }
}
