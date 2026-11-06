package com.northcart.order.entity;

import com.northcart.catalog.entity.Product;
import com.northcart.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Immutable line item. productName, sku and unitPrice are SNAPSHOTS taken at checkout:
 * if an admin renames the product or changes its price tomorrow, this order still shows
 * exactly what the customer paid for. product is kept only for links and analytics.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Setter(AccessLevel.PACKAGE) // set by Order.addItem()
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150, updatable = false)
    private String productName;

    @Column(name = "sku", nullable = false, length = 50, updatable = false)
    private String sku;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    public static OrderItem snapshotOf(Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        OrderItem item = new OrderItem();
        item.product = product;
        item.productName = product.getName();
        item.sku = product.getSku();
        item.unitPrice = product.getPrice();
        item.quantity = quantity;
        item.lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        return item;
    }
}
