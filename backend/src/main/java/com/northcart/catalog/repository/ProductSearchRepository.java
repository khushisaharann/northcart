package com.northcart.catalog.repository;

import com.northcart.catalog.dto.ProductSearchCriteria;
import com.northcart.catalog.dto.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Dynamic product search in plain SQL.
 *
 * <p>Why SQL instead of JPQL / Specifications? Full-text search uses PostgreSQL-only
 * features (tsvector, to_tsquery, ts_rank) that JPQL cannot express. We still use
 * bind parameters (:name) for every user value, so there is no SQL injection risk;
 * the only concatenated pieces are fixed strings chosen by our code.</p>
 *
 * <p>Returns product IDS for one page ("step 1"). ProductRepository then loads the full
 * entities for exactly those ids ("step 2"). This keeps LIMIT/OFFSET in the database even
 * though products have a one-to-many image collection.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private static final int MAX_SEARCH_WORDS = 8;

    private static final String FROM = """
             FROM products p
             JOIN categories c ON c.id = p.category_id
             JOIN inventory i ON i.product_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Page<Long> searchIds(ProductSearchCriteria criteria, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (!criteria.includeInactive()) {
            where.append(" AND p.active AND c.active");
        }

        String tsQuery = toPrefixTsQuery(criteria.searchText());
        boolean searching = tsQuery != null;
        if (searching) {
            // Text match uses the GIN index ix_products_search; an exact SKU also matches.
            // SKUs are stored upper-case (ck_products_sku_uppercase), so "p.sku = upper(...)"
            // can use uk_products_sku. Wrapping the COLUMN in upper() would defeat every index
            // and, inside this OR, force a full table scan (see docs/performance.md).
            where.append(" AND (p.search_vector @@ to_tsquery('english', :tsQuery) OR p.sku = upper(trim(:rawText)))");
            params.addValue("tsQuery", tsQuery);
            params.addValue("rawText", criteria.searchText());
        }
        if (criteria.categorySlug() != null) {
            where.append(" AND c.slug = :categorySlug");
            params.addValue("categorySlug", criteria.categorySlug());
        }
        if (criteria.minPrice() != null) {
            where.append(" AND p.price >= :minPrice");
            params.addValue("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice");
            params.addValue("maxPrice", criteria.maxPrice());
        }
        if (criteria.inStockOnly()) {
            where.append(" AND i.quantity_available > 0");
        }
        if (criteria.lowStockOnly()) {
            where.append(" AND i.quantity_available <= i.low_stock_threshold");
        }

        PageRequest pageRequest = PageRequest.of(page, size);
        Long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, params, Long.class);
        if (total == null || total == 0 || (long) page * size >= total) {
            return new PageImpl<>(List.of(), pageRequest, total == null ? 0 : total);
        }

        ProductSort sort = criteria.sort() == ProductSort.RELEVANCE && !searching ? ProductSort.NEWEST : criteria.sort();
        // p.id as tie-breaker: without a unique last sort key, rows with equal prices can
        // swap between pages, so a product could appear twice or never while paging.
        String sql = "SELECT p.id" + FROM + where
                + " ORDER BY " + sort.orderBy() + ", p.id DESC"
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);

        List<Long> ids = jdbc.queryForList(sql, params, Long.class);
        return new PageImpl<>(ids, pageRequest, total);
    }

    /**
     * "mech keyb" -> "mech:* & keyb:*" so partial words match ("keyb" finds "Keyboard").
     * Only letters/digits are kept, so user input can never break the tsquery syntax.
     */
    static String toPrefixTsQuery(String text) {
        if (text == null) {
            return null;
        }
        String query = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isBlank())
                .limit(MAX_SEARCH_WORDS)
                .map(w -> w + ":*")
                .collect(Collectors.joining(" & "));
        return query.isEmpty() ? null : query;
    }
}
