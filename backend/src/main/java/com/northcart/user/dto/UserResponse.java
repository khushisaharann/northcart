package com.northcart.user.dto;

import java.time.Instant;
import java.util.Set;

/** Public view of a user. No password hash, no "enabled" internals. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Set<String> roles,
        Instant memberSince
) {
}
