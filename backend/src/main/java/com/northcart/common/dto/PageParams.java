package com.northcart.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Reusable ?page=&size= query parameters with sane limits. */
public record PageParams(
        @Schema(description = "Zero-based page index", example = "0") @Min(0) Integer page,
        @Schema(description = "Page size (max 50)", example = "10") @Min(1) @Max(50) Integer size
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 10 : size;
    }
}
