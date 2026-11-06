package com.northcart.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin view: includes inactive products, stock levels and audit timestamps. */
public record AdminProductResponse(
        Long id,
        String name,
        String slug,
        String sku,
        String description,
        BigDecimal price,
        boolean active,
        CategorySummary category,
        List<ProductImageResponse> images,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock,
        int soldCount,
        Instant createdAt,
        Instant updatedAt
) {
}
