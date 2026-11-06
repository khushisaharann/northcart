package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full replacement of the editable fields (PUT semantics).
 * Not included on purpose:
 *  - sku and slug: stable identifiers (old orders and shared links refer to them)
 *  - stock: changed only through PATCH /admin/inventory/{productId} with a delta,
 *    so an admin edit can never overwrite stock that orders changed meanwhile.
 */
public record UpdateProductRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull Long categoryId,
        @NotNull Boolean active,
        @Schema(example = "5") @NotNull @Min(0) @Max(100_000) Integer lowStockThreshold,
        @NotNull @Size(max = 10) List<@Valid ProductImageRequest> images
) {
}
