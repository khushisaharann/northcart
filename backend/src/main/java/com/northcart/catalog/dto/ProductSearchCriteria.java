package com.northcart.catalog.dto;

import java.math.BigDecimal;

/**
 * Internal, already-normalised search input shared by the customer and admin searches.
 * Built by the service layer from the request params; never bound from HTTP directly.
 */
public record ProductSearchCriteria(
        String searchText,       // trimmed, null if blank
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        boolean lowStockOnly,
        boolean includeInactive,
        ProductSort sort
) {
    public static ProductSearchCriteria forCustomer(ProductSearchParams p) {
        String text = blankToNull(p.q());
        return new ProductSearchCriteria(text, blankToNull(p.category()), p.minPrice(), p.maxPrice(),
                Boolean.TRUE.equals(p.inStock()), false, false, ProductSort.from(p.sort(), text != null));
    }

    public static ProductSearchCriteria forAdmin(AdminProductSearchParams p) {
        String text = blankToNull(p.q());
        return new ProductSearchCriteria(text, blankToNull(p.category()), null, null,
                false, Boolean.TRUE.equals(p.lowStock()), true, ProductSort.NEWEST);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
