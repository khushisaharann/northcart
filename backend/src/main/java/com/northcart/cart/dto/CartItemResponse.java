package com.northcart.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long itemId,
        Long productId,
        String name,
        String slug,
        String thumbnailUrl,
        BigDecimal unitPrice,        // CURRENT price; the cart never stores prices
        int quantity,
        BigDecimal lineTotal,
        int availableQuantity,
        CartItemIssue issue          // null when the line is fine
) {
}
