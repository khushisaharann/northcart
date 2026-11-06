package com.northcart.inventory.dto;

public record StockResponse(
        Long productId,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock
) {
}
