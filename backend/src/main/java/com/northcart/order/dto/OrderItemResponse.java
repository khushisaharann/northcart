package com.northcart.order.dto;

import java.math.BigDecimal;

/** Snapshot values as they were at purchase time. */
public record OrderItemResponse(
        Long productId,
        String productSlug,   // for a link back to the product page
        String productName,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
