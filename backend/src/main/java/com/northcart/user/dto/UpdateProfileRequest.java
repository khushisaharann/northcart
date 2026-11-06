package com.northcart.user.dto;

import com.northcart.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email is intentionally not editable here: changing it safely needs a verification
 * email to the new address, otherwise a typo locks the user out of password reset.
 */
public record UpdateProfileRequest(
        @Schema(example = "Khushi Kumar") @NotBlank @Size(max = 100) String fullName,
        @Schema(example = "+91 98765 43210", nullable = true)
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone
) {
}
