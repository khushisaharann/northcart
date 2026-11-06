package com.northcart.address.dto;

import com.northcart.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAddressRequest(
        @Schema(example = "Khushi Kumar") @NotBlank @Size(max = 100) String fullName,
        @Schema(example = "+91 98765 43210")
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,
        @Schema(example = "Flat 12, Green Residency") @NotBlank @Size(max = 200) String line1,
        @Schema(example = "Sector 62", nullable = true) @Size(max = 200) String line2,
        @Schema(example = "Noida") @NotBlank @Size(max = 100) String city,
        @Schema(example = "Uttar Pradesh") @NotBlank @Size(max = 100) String state,
        @Schema(example = "201301") @NotBlank @Size(max = 20) String postalCode,
        @Schema(example = "India", nullable = true, description = "Defaults to India") @Size(max = 60) String country,
        @Schema(description = "Make this the default address. The first address is always the default.")
        Boolean makeDefault
) {
}
