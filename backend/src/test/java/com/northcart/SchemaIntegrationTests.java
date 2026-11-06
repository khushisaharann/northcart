package com.northcart;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the WHOLE application against a real PostgreSQL container.
 *
 * <p>If this class runs at all, three things are already proven:</p>
 * <ol>
 *   <li>Flyway applied every migration without SQL errors.</li>
 *   <li>Hibernate's ddl-auto=validate found every entity field matching a column of the
 *       right type (a mismatch aborts startup with "Schema-validation: ...").</li>
 *   <li>All configuration (AppProperties validation, security, beans, Swagger) is wired.</li>
 * </ol>
 */
class SchemaIntegrationTests extends AbstractIntegrationTest {

    @Test
    void flywayAppliedAllMigrations() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isEqualTo(5); // V1-V5
    }

    @Test
    void rolesAreSeeded() {
        List<String> roles = jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roles).containsExactly("ADMIN", "CUSTOMER");
    }

    @Test
    @Transactional // rolled back after the test, so tests don't leak data into each other
    void databaseRejectsNegativeStock() {
        jdbc.update("INSERT INTO categories (name, slug) VALUES ('Test', 'test')");
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, sku, price)
                VALUES ((SELECT id FROM categories WHERE slug = 'test'), 'Mouse', 'mouse', 'SKU-T1', 10)
                """);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory (product_id, quantity_available)
                VALUES ((SELECT id FROM products WHERE sku = 'SKU-T1'), -1)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_quantity_non_negative");
    }

    @Test
    void fullTextSearchUsesStemming() {
        // "keyboards" must match "Keyboard" thanks to the English text-search configuration.
        Boolean matches = jdbc.queryForObject(
                "SELECT to_tsvector('english', 'Mechanical Keyboard') @@ websearch_to_tsquery('english', 'keyboards')",
                Boolean.class);
        assertThat(matches).isTrue();
    }

    @Test
    void bootstrapAdminIsCreatedWithAdminRole() {
        List<String> roles = jdbc.queryForList("""
                SELECT r.name FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN roles r ON r.id = ur.role_id
                 WHERE u.email = 'admin@northcart.test'
                """, String.class);
        assertThat(roles).containsExactly("ADMIN");
    }
}
