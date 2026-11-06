package com.northcart.catalog.dto;

import java.util.Arrays;

/**
 * Allowed sort orders. The SQL fragment is fixed per constant and never built from user
 * input: ORDER BY can't use bind parameters, so a whitelist is how we prevent SQL injection
 * in sorting.
 */
public enum ProductSort {

    RELEVANCE("relevance", "ts_rank(p.search_vector, to_tsquery('english', :tsQuery)) DESC"),
    NEWEST("newest", "p.created_at DESC"),
    PRICE_ASC("price_asc", "p.price ASC"),
    PRICE_DESC("price_desc", "p.price DESC"),
    POPULAR("popular", "p.sold_count DESC");

    /** Regex for @Pattern on the request parameter. */
    public static final String PARAM_PATTERN = "relevance|newest|price_asc|price_desc|popular";

    private final String param;
    private final String orderBy;

    ProductSort(String param, String orderBy) {
        this.param = param;
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }

    /**
     * Default: relevance when searching, newest when browsing.
     * Relevance without a search term makes no sense, so it falls back to newest.
     */
    public static ProductSort from(String param, boolean hasSearchTerm) {
        ProductSort sort = param == null ? null : Arrays.stream(values())
                .filter(s -> s.param.equals(param))
                .findFirst()
                .orElse(null);
        if (sort == null) {
            return hasSearchTerm ? RELEVANCE : NEWEST;
        }
        return sort == RELEVANCE && !hasSearchTerm ? NEWEST : sort;
    }
}
