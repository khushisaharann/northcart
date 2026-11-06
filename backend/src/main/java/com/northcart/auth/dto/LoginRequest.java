package com.northcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** No password-strength rules here: login must accept whatever was valid when the account was made. */
public record LoginRequest(
        @Schema(example = "khushi@example.com") @NotBlank @Email String email,
        @Schema(example = "Secret123") @NotBlank String password
) {
}
