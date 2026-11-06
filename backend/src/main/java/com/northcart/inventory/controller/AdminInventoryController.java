package com.northcart.inventory.controller;

import com.northcart.config.OpenApiConfig;
import com.northcart.inventory.dto.AdjustStockRequest;
import com.northcart.inventory.dto.StockResponse;
import com.northcart.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected by the "/api/v1/admin/** -> ADMIN" rule in SecurityConfig. */
@Tag(name = "Admin - Inventory")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Add or remove stock with a relative delta (e.g. +50 restock, -2 damaged)")
    @PatchMapping("/{productId}")
    public StockResponse adjust(@PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request) {
        return inventoryService.adjustStock(productId, request.delta(), request.reason());
    }
}
