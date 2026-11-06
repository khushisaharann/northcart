package com.northcart.auth.dto;

import com.northcart.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Schema(description = "Token from the reset link in the email")
        @NotBlank @Size(max = 200)
        String token,

        @Schema(example = "NewSecret456")
        @NotBlank
        @Size(min = ValidationPatterns.PASSWORD_MIN, max = ValidationPatterns.PASSWORD_MAX)
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword
) {
}
