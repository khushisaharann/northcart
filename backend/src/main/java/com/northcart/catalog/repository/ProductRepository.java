package com.northcart.catalog.repository;

import com.northcart.catalog.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySlug(String slug);

    /**
     * Atomic counter update. "load, +1, save" would lose increments when two orders for the
     * same product are placed at once (both read 340, both write 341).
     */
    @Modifying
    @Query(value = "UPDATE products SET sold_count = GREATEST(sold_count + :delta, 0) WHERE id = :id",
            nativeQuery = true)
    void addToSoldCount(@Param("id") Long id, @Param("delta") int delta);

    boolean existsBySku(String sku);

    /** @EntityGraph = load category and images in the same query (no lazy-loading later). */
    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findWithDetailsById(Long id);

    /**
     * Second step of "IDs first, then entities" pagination (see ProductSearchRepository).
     * Fetch-joining a collection (images) together with LIMIT would make Hibernate page
     * in memory; loading a fixed list of ids avoids that.
     */
    @Query("""
            SELECT p FROM Product p
              JOIN FETCH p.category
              LEFT JOIN FETCH p.images
             WHERE p.id IN :ids
            """)
    List<Product> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);
}
