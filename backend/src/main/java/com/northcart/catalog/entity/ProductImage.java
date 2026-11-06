package com.northcart.catalog.entity;

import com.northcart.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage extends BaseEntity {

    // Owning side of Product.images: this entity holds the product_id foreign key.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "alt_text", length = 150)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public ProductImage(String url, String altText, int sortOrder) {
        this.url = url;
        this.altText = altText;
        this.sortOrder = sortOrder;
    }
}
