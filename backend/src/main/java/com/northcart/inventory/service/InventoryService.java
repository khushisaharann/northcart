package com.northcart.inventory.service;

import com.northcart.catalog.entity.Product;
import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.common.exception.InsufficientStockException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.inventory.dto.StockResponse;
import com.northcart.inventory.entity.Inventory;
import com.northcart.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ONLY class that changes stock. Cart (Phase 6) and orders (Phase 7) call it too,
 * so every stock rule lives in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    @Transactional
    public Inventory createFor(Product product, int initialQuantity, Integer lowStockThreshold) {
        Inventory inventory = new Inventory(product, initialQuantity);
        inventory.setLowStockThreshold(lowStockThreshold == null ? DEFAULT_LOW_STOCK_THRESHOLD : lowStockThreshold);
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Inventory getForProduct(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }

    /** One query for a whole page of products, keyed by product id. */
    @Transactional(readOnly = true)
    public Map<Long, Inventory> findByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));
    }

    @Transactional
    public void updateLowStockThreshold(Long productId, int threshold) {
        getForProduct(productId).setLowStockThreshold(threshold); // dirty checking saves it
    }

    /**
     * Checkout: takes stock out atomically. Throws (and so rolls back the whole checkout)
     * if there isn't enough. Callers must reserve products in ascending id order.
     */
    @Transactional
    public void reserve(Long productId, String productName, int quantity) {
        if (inventoryRepository.adjustQuantity(productId, -quantity, clock.instant()) == 0) {
            int available = inventoryRepository.findByProductId(productId)
                    .map(Inventory::getQuantityAvailable).orElse(0);
            throw new InsufficientStockException(productName, available);
        }
    }

    /** Cancellation / expiry: puts stock back. Adding can never violate the >= 0 rule. */
    @Transactional
    public void release(Long productId, int quantity) {
        inventoryRepository.adjustQuantity(productId, quantity, clock.instant());
    }

    /** Admin restock / correction. Positive delta adds stock, negative removes it. */
    @Transactional
    public StockResponse adjustStock(Long productId, int delta, String reason) {
        int updated = inventoryRepository.adjustQuantity(productId, delta, clock.instant());
        if (updated == 0) {
            Inventory current = getForProduct(productId); // 404 if the product doesn't exist
            throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
                    "Stock cannot go below zero. Available: " + current.getQuantityAvailable());
        }
        Inventory inventory = getForProduct(productId);
        log.info("Stock adjusted: productId={}, delta={}, now={}, reason={}",
                productId, delta, inventory.getQuantityAvailable(), reason);
        return toResponse(inventory);
    }

    public static StockResponse toResponse(Inventory inventory) {
        return new StockResponse(inventory.getProduct().getId(), inventory.getQuantityAvailable(),
                inventory.getLowStockThreshold(), inventory.isLowStock());
    }
}
