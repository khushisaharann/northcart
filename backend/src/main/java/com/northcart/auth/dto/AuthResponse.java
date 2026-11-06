package com.northcart.auth.dto;

import com.northcart.user.dto.UserResponse;

/**
 * <pre>
 * {
 *   "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 7200,
 *   "user": { "id": 1, "email": "khushi@example.com", "fullName": "Khushi Kumar", "roles": ["CUSTOMER"], ... }
 * }
 * </pre>
 * The frontend stores the token and sends it as "Authorization: Bearer &lt;accessToken&gt;".
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,      // seconds
        UserResponse user
) {
}
