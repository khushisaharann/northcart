package com.northcart.catalog.dto;

/** Built directly by a JPQL constructor expression in CategoryRepository. */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Long productCount
) {
}
