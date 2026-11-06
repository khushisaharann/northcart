package com.northcart.admin.repository;

import com.northcart.TestcontainersConfiguration;
import com.northcart.admin.dto.DashboardResponse.DailySales;
import com.northcart.admin.dto.DashboardResponse.TopProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the edges of the range-limited dashboard queries (V5 / docs/performance.md):
 * business days are India time, both ends of the range are inclusive, and a paid order
 * that was later cancelled does not count.
 *
 * <p>Uses dates in January 2020 so orders created by other tests never fall in the range.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AdminStatsRepository.class})
@ActiveProfiles("test")
class AdminStatsRepositoryTest {

    private static final LocalDate FROM = LocalDate.of(2020, 1, 10);
    private static final LocalDate TO = LocalDate.of(2020, 1, 12);

    @Autowired
    private AdminStatsRepository stats;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long lamp;
    private long mug;
    private int seq;

    @BeforeEach
    void seed() {
        String tag = "stats-" + System.nanoTime();
        jdbc.update("INSERT INTO users (email, password_hash, full_name) VALUES (?, 'x', 'Khushi Kumar')", tag + "@test.com");
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, tag + "@test.com");
        jdbc.update("INSERT INTO categories (name, slug) VALUES (?, ?)", tag, tag);
        lamp = product(tag, "lamp");
        mug = product(tag, "mug");

        // IST = UTC+05:30, so 18:30Z is midnight in India.
        order("CONFIRMED", "2020-01-09T18:29:59Z", lamp, 9);   // 23:59:59 IST on Jan 9 -> outside
        order("SHIPPED",   "2020-01-09T18:30:00Z", lamp, 2);   // 00:00 IST on Jan 10  -> first day
        order("DELIVERED", "2020-01-12T18:29:00Z", mug, 1);    // 23:59 IST on Jan 12  -> last day
        order("CONFIRMED", "2020-01-12T18:30:00Z", mug, 9);    // 00:00 IST on Jan 13  -> outside
        order("CANCELLED", "2020-01-11T06:00:00Z", mug, 5);    // paid, then cancelled -> refunded, excluded
    }

    @Test
    void salesByDay_usesIndiaDays_inclusiveRange_everyDayPresent() {
        List<DailySales> days = stats.salesByDay(FROM, TO);

        assertThat(days).extracting(DailySales::date)
                .containsExactly(FROM, FROM.plusDays(1), TO);
        assertThat(days).extracting(DailySales::orders).containsExactly(1L, 0L, 1L);
        assertThat(days.get(0).revenue()).isEqualByComparingTo("200.00");   // 2 x 100
        assertThat(days.get(1).revenue()).isEqualByComparingTo("0");
        assertThat(days.get(2).revenue()).isEqualByComparingTo("100.00");
    }

    @Test
    void topProducts_countsOnlyPaidOrdersInsideTheRange() {
        List<TopProduct> top = stats.topProducts(FROM, TO, 5);

        assertThat(top).extracting(TopProduct::productId).containsExactly(lamp, mug);
        assertThat(top).extracting(TopProduct::unitsSold).containsExactly(2L, 1L);
        assertThat(top.get(0).revenue()).isEqualByComparingTo("200.00");
    }

    // ------------------------------------------------------------------ helpers

    private long product(String tag, String name) {
        String slug = tag + "-" + name;
        jdbc.update("INSERT INTO products (category_id, name, slug, sku, price) VALUES ((SELECT id FROM categories WHERE slug = ?), ?, ?, ?, 100)",
                tag, name, slug, slug.toUpperCase());
        return jdbc.queryForObject("SELECT id FROM products WHERE slug = ?", Long.class, slug);
    }

    /** One order with one line at 100.00 per unit, confirmed (paid) at the given instant. */
    private void order(String status, String confirmedAt, long productId, int quantity) {
        Timestamp confirmed = Timestamp.from(Instant.parse(confirmedAt));
        BigDecimal total = BigDecimal.valueOf(100L * quantity);
        String number = "T-" + System.nanoTime() + "-" + (seq++);   // fits VARCHAR(30)
        jdbc.update("""
                INSERT INTO orders (order_number, user_id, status, subtotal, shipping_fee, total,
                                    ship_full_name, ship_phone, ship_line1, ship_city, ship_state,
                                    ship_postal_code, ship_country, confirmed_at, cancelled_at, created_at)
                VALUES (?, ?, ?, ?, 0, ?, 'Khushi Kumar', '9800000000', '12 MG Road', 'Bengaluru',
                        'Karnataka', '560001', 'India', ?, ?, ?)
                """, number, userId, status, total, total, confirmed,
                "CANCELLED".equals(status) ? confirmed : null, confirmed);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_name, sku, unit_price, quantity, line_total)
                SELECT o.id, p.id, p.name, p.sku, 100, ?, ?
                  FROM orders o, products p WHERE o.order_number = ? AND p.id = ?
                """, quantity, total, number, productId);
    }
}
