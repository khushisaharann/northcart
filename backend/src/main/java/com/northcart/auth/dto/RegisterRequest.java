package com.northcart.auth.dto;

import com.northcart.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Note what is NOT here: role, enabled, id. A client cannot choose its own role;
 * AuthService always assigns CUSTOMER. (Unknown JSON fields are rejected outright.)
 */
public record RegisterRequest(
        @Schema(example = "Khushi Kumar")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "khushi@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Secret123")
        @NotBlank
        @Size(min = ValidationPatterns.PASSWORD_MIN, max = ValidationPatterns.PASSWORD_MAX)
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,

        @Schema(example = "+91 98765 43210", nullable = true)
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone
) {
}
