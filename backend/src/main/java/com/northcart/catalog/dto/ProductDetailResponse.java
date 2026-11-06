package com.northcart.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/** Product details page. availableQuantity lets the UI cap the quantity selector. */
public record ProductDetailResponse(
        Long id,
        String name,
        String slug,
        String sku,
        String description,
        BigDecimal price,
        CategorySummary category,
        List<ProductImageResponse> images,
        boolean inStock,
        int availableQuantity
) {
}
