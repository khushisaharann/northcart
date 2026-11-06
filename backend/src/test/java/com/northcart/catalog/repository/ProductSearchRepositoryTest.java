package com.northcart.catalog.repository;

import com.northcart.TestcontainersConfiguration;
import com.northcart.catalog.dto.ProductSearchCriteria;
import com.northcart.catalog.dto.ProductSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test: only the persistence layer starts (no web, no security), against
 * a real PostgreSQL so the full-text search and the generated tsvector column really run.
 * Each test is rolled back automatically (@DataJpaTest is @Transactional).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // keep Postgres, no H2
@Import({TestcontainersConfiguration.class, ProductSearchRepository.class})
@ActiveProfiles("test")
class ProductSearchRepositoryTest {

    @Autowired
    private ProductSearchRepository searchRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String category;

    @BeforeEach
    void seed() {
        category = "repo-test-" + System.nanoTime();
        jdbc.update("INSERT INTO categories (name, slug) VALUES (?, ?)", category, category);
        product("Mechanical Keyboard", "RT-1", "3499.00", 10, true);
        product("Wireless Mouse", "RT-2", "799.00", 0, true);
        product("Keyboard Wrist Rest", "RT-3", "799.00", 4, true);
        product("Hidden Keyboard", "RT-4", "999.00", 9, false);
    }

    private void product(String name, String sku, String price, int stock, boolean active) {
        String slug = sku.toLowerCase() + "-" + category;
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, sku, description, price, active)
                VALUES ((SELECT id FROM categories WHERE slug = ?), ?, ?, ?, ?, ?, ?)
                """, category, name, slug, sku + "-" + category.hashCode(), name + " for your desk",
                new BigDecimal(price), active);
        jdbc.update("INSERT INTO inventory (product_id, quantity_available) VALUES ((SELECT id FROM products WHERE slug = ?), ?)",
                slug, stock);
    }

    private ProductSearchCriteria criteria(String text, BigDecimal min, BigDecimal max, boolean inStock,
                                           boolean includeInactive, ProductSort sort) {
        return new ProductSearchCriteria(text, category, min, max, inStock, false, includeInactive, sort);
    }

    private List<String> names(Page<Long> page) {
        return page.getContent().stream()
                .map(id -> jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, id))
                .toList();
    }

    @Test
    void prefixSearchFindsPartialWords_andHidesInactiveProducts() {
        Page<Long> page = searchRepository.searchIds(criteria("keyb", null, null, false, false, ProductSort.RELEVANCE), 0, 10);
        assertThat(names(page)).containsExactlyInAnyOrder("Mechanical Keyboard", "Keyboard Wrist Rest");
    }

    @Test
    void adminSearchIncludesInactiveProducts() {
        Page<Long> page = searchRepository.searchIds(criteria("keyb", null, null, false, true, ProductSort.NEWEST), 0, 10);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void priceRangeAndStockFilters() {
        Page<Long> cheap = searchRepository.searchIds(
                criteria(null, new BigDecimal("500"), new BigDecimal("1000"), false, false, ProductSort.PRICE_ASC), 0, 10);
        assertThat(names(cheap)).containsExactlyInAnyOrder("Wireless Mouse", "Keyboard Wrist Rest");

        Page<Long> inStock = searchRepository.searchIds(criteria(null, null, null, true, false, ProductSort.NEWEST), 0, 10);
        assertThat(names(inStock)).doesNotContain("Wireless Mouse");
    }

    @Test
    void equalPricesHaveAStableOrderAcrossPages() {
        // Two products cost 799.00: the id tie-breaker keeps pages from overlapping
        var sort = criteria(null, null, null, false, false, ProductSort.PRICE_ASC);
        List<Long> first = searchRepository.searchIds(sort, 0, 1).getContent();
        List<Long> second = searchRepository.searchIds(sort, 1, 1).getContent();
        List<Long> both = searchRepository.searchIds(sort, 0, 2).getContent();
        assertThat(List.of(first.get(0), second.get(0))).isEqualTo(both);
    }

    @Test
    void pagination_reportsTotalsAndReturnsNothingPastTheEnd() {
        var all = criteria(null, null, null, false, false, ProductSort.NEWEST);
        Page<Long> page = searchRepository.searchIds(all, 1, 2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(searchRepository.searchIds(all, 5, 2).getContent()).isEmpty();
    }
}
