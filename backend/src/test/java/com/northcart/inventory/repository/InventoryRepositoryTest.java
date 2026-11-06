package com.northcart.inventory.repository;

import com.northcart.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The atomic stock update is the core of "never oversell"; test it directly in SQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long productId;

    @BeforeEach
    void seed() {
        String slug = "inv-test-" + System.nanoTime();
        jdbc.update("INSERT INTO categories (name, slug) VALUES (?, ?)", slug, slug);
        jdbc.update("INSERT INTO products (category_id, name, slug, sku, price) VALUES ((SELECT id FROM categories WHERE slug = ?), 'Item', ?, ?, 10)",
                slug, slug, slug.toUpperCase());
        productId = jdbc.queryForObject("SELECT id FROM products WHERE slug = ?", Long.class, slug);
        jdbc.update("INSERT INTO inventory (product_id, quantity_available) VALUES (?, 5)", productId);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT quantity_available FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    void reservesWhenEnoughStock_andBumpsTheVersion() {
        assertThat(inventoryRepository.adjustQuantity(productId, -3, Instant.now())).isEqualTo(1);
        assertThat(stock()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM inventory WHERE product_id = ?", Long.class, productId))
                .isEqualTo(1L);
    }

    @Test
    void refusesToGoBelowZero_withoutThrowing() {
        assertThat(inventoryRepository.adjustQuantity(productId, -6, Instant.now())).isZero();
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void unknownProductUpdatesNothing() {
        assertThat(inventoryRepository.adjustQuantity(-1L, 1, Instant.now())).isZero();
    }
}
