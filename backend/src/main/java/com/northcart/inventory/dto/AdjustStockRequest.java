package com.northcart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Relative change, not an absolute value: "+50 received", "-2 damaged".
 * Two admins (or an admin and a checkout) adjusting at once can't overwrite each other.
 */
public record AdjustStockRequest(
        @Schema(example = "50") @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta,
        @Schema(example = "Restock from supplier") @Size(max = 200) String reason
) {
    @AssertTrue(message = "delta must not be 0")
    public boolean isNonZero() {
        return delta == null || delta != 0;
    }
}
