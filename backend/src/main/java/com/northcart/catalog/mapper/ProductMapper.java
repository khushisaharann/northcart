package com.northcart.catalog.mapper;

import com.northcart.catalog.dto.AdminProductResponse;
import com.northcart.catalog.dto.CategorySummary;
import com.northcart.catalog.dto.ProductDetailResponse;
import com.northcart.catalog.dto.ProductImageResponse;
import com.northcart.catalog.dto.ProductSummaryResponse;
import com.northcart.catalog.entity.Category;
import com.northcart.catalog.entity.Product;
import com.northcart.inventory.entity.Inventory;

import java.util.List;

/**
 * Product entity -> DTOs. Must be called inside a transaction (or with category and
 * images already fetched), because those are lazy relationships.
 */
public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductSummaryResponse toSummary(Product p, Inventory inventory) {
        String thumbnail = p.getImages().isEmpty() ? null : p.getImages().get(0).getUrl();
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getPrice(), thumbnail,
                toCategorySummary(p.getCategory()), available(inventory) > 0);
    }

    public static ProductDetailResponse toDetail(Product p, Inventory inventory) {
        int available = available(inventory);
        return new ProductDetailResponse(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getDescription(),
                p.getPrice(), toCategorySummary(p.getCategory()), images(p), available > 0, available);
    }

    public static AdminProductResponse toAdmin(Product p, Inventory inventory) {
        return new AdminProductResponse(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getDescription(),
                p.getPrice(), p.isActive(), toCategorySummary(p.getCategory()), images(p),
                available(inventory),
                inventory == null ? 0 : inventory.getLowStockThreshold(),
                inventory != null && inventory.isLowStock(),
                p.getSoldCount(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static CategorySummary toCategorySummary(Category c) {
        return new CategorySummary(c.getId(), c.getName(), c.getSlug());
    }

    private static List<ProductImageResponse> images(Product p) {
        return p.getImages().stream()
                .map(i -> new ProductImageResponse(i.getUrl(), i.getAltText(), i.getSortOrder()))
                .toList();
    }

    private static int available(Inventory inventory) {
        return inventory == null ? 0 : inventory.getQuantityAvailable();
    }
}
