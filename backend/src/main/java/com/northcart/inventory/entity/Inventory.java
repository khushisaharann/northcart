package com.northcart.inventory.entity;

import com.northcart.catalog.entity.Product;
import com.northcart.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stock lives in its own table so that frequent stock writes don't lock or bump the
 * product row (which customers read constantly), and so stock rules live in one place.
 *
 * <p>Only InventoryService (Phase 5/7) may change quantityAvailable.</p>
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory extends BaseEntity {

    // Owning side of the 1:1 (holds product_id, which is UNIQUE in the schema).
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    // Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and throws
    // OptimisticLockException if another transaction changed the row first.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Inventory(Product product, int quantityAvailable) {
        this.product = product;
        this.quantityAvailable = quantityAvailable;
    }

    public boolean canFulfil(int requestedQuantity) {
        return requestedQuantity > 0 && quantityAvailable >= requestedQuantity;
    }

    public boolean isLowStock() {
        return quantityAvailable <= lowStockThreshold;
    }
}
