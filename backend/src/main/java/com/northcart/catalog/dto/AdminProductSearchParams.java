package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** GET /api/v1/admin/products?q=&category=&lowStock=true&page=0&size=20 (includes inactive products). */
public record AdminProductSearchParams(
        @Schema(description = "Name/description words or an exact SKU") @Size(max = 100) String q,
        @Size(max = 100) String category,
        @Schema(description = "Only products at or below their low-stock threshold") Boolean lowStock,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
