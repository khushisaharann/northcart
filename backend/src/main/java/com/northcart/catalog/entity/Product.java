package com.northcart.catalog.entity;

import com.northcart.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Notes:
 *  - search_vector is a GENERATED column maintained by PostgreSQL. It is intentionally not
 *    mapped; full-text search uses a native query in Phase 5.
 *  - Inventory is NOT mapped here. A lazy @OneToOne on the non-owning side cannot be truly
 *    lazy in Hibernate (it must query to know whether to put null or a proxy), so loading a
 *    page of 20 products would fire 20 extra inventory queries. Inventory owns the link.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    // columnDefinition TEXT rather than @Lob: on PostgreSQL, @Lob String maps to a large-object
    // OID column, which is not what we want for ordinary text.
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    // Product is the aggregate root for its images: saving/deleting the product saves/deletes
    // its images; removing an image from this list deletes its row (orphanRemoval).
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    /** Keeps both sides of the bidirectional relationship in sync. */
    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }

    public void increaseSoldCount(int quantity) {
        this.soldCount += quantity;
    }
}
