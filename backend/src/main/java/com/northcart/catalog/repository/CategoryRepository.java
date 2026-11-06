package com.northcart.catalog.repository;

import com.northcart.catalog.dto.CategoryResponse;
import com.northcart.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    /**
     * One query returns every active category with its number of active products.
     * "select new ..." builds DTOs directly (no entities loaded); LEFT JOIN keeps
     * categories that have zero products.
     */
    @Query("""
            SELECT new com.northcart.catalog.dto.CategoryResponse(
                       c.id, c.name, c.slug, c.description, COUNT(p.id))
              FROM Category c
              LEFT JOIN Product p ON p.category = c AND p.active = true
             WHERE c.active = true
             GROUP BY c.id, c.name, c.slug, c.description
             ORDER BY c.name
            """)
    List<CategoryResponse> findActiveWithProductCount();
}
