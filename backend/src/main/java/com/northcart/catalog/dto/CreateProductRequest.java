package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @Schema(example = "Wireless Mouse") @NotBlank @Size(max = 150) String name,

        @Schema(example = "ELEC-001", description = "Unique stock-keeping unit; cannot be changed later")
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "may contain only letters, digits and '-'")
        String sku,

        @Schema(example = "Ergonomic wireless mouse with silent clicks") @Size(max = 5000) String description,

        // Digits(10, 2) mirrors NUMERIC(12,2) in the database.
        @Schema(example = "799.00") @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,

        @Schema(example = "1") @NotNull Long categoryId,

        @Schema(example = "100") @NotNull @Min(0) @Max(1_000_000) Integer initialStock,

        @Schema(example = "5", description = "Defaults to 5") @Min(0) @Max(100_000) Integer lowStockThreshold,

        @Schema(description = "Defaults to true") Boolean active,

        @Size(max = 10) List<@Valid ProductImageRequest> images
) {
}
