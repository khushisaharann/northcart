package com.northcart.catalog.dto;

import java.math.BigDecimal;

/** One card on the product listing page: just enough to render it. */
public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        String thumbnailUrl,
        CategorySummary category,
        boolean inStock
) {
}
