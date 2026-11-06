package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Query parameters of GET /api/v1/products, bound into one validated object:
 * /api/v1/products?q=keyboard&category=electronics&minPrice=1000&maxPrice=5000&inStock=true&sort=price_asc&page=0&size=12
 */
public record ProductSearchParams(
        @Schema(description = "Search words (matches name, description; word prefixes allowed)", example = "keyb")
        @Size(max = 100) String q,

        @Schema(description = "Category slug", example = "electronics")
        @Size(max = 100) String category,

        @Schema(example = "1000") @DecimalMin("0") BigDecimal minPrice,
        @Schema(example = "5000") @DecimalMin("0") BigDecimal maxPrice,

        @Schema(description = "Only products with stock > 0") Boolean inStock,

        @Schema(allowableValues = {"relevance", "newest", "price_asc", "price_desc", "popular"})
        @Pattern(regexp = ProductSort.PARAM_PATTERN,
                message = "must be one of: relevance, newest, price_asc, price_desc, popular")
        String sort,

        @Schema(description = "Zero-based page index", example = "0") @Min(0) Integer page,
        @Schema(description = "Page size (max 50)", example = "12") @Min(1) @Max(50) Integer size
) {
    public static final int DEFAULT_SIZE = 12;

    /** Cross-field rule. Bean Validation reports it as the field "priceRangeValid". */
    @AssertTrue(message = "minPrice must not be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
