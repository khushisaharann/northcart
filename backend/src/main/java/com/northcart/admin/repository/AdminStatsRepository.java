package com.northcart.admin.repository;

import com.northcart.admin.dto.DashboardResponse.DailySales;
import com.northcart.admin.dto.DashboardResponse.LowStockItem;
import com.northcart.admin.dto.DashboardResponse.RecentOrder;
import com.northcart.admin.dto.DashboardResponse.TopProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reporting queries in SQL. Aggregations (SUM, COUNT, GROUP BY, generate_series)
 * belong in the database: loading thousands of orders into Java to add them up would be
 * slow and memory-hungry. Results map straight into small DTOs.
 */
@Repository
@RequiredArgsConstructor
public class AdminStatsRepository {

    /** Paid and not cancelled. Cancelled-after-payment orders were refunded. */
    static final String PAID = "('CONFIRMED', 'SHIPPED', 'DELIVERED')";
    /** Business days are counted in Indian time, not UTC. */
    static final String ZONE = "'Asia/Kolkata'";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The headline numbers. The three order figures come from ONE pass over orders using
     * FILTER, instead of three separate scans (see docs/performance.md).
     */
    public Map<String, Object> summary() {
        return jdbc.queryForMap("""
                SELECT
                  (SELECT count(*) FROM users u
                     JOIN user_roles ur ON ur.user_id = u.id
                     JOIN roles r ON r.id = ur.role_id AND r.name = 'CUSTOMER')         AS customers,
                  (SELECT count(*) FROM products WHERE active)                            AS active_products,
                  o.total_orders, o.paid_orders, o.revenue,
                  (SELECT count(*) FROM inventory i JOIN products p ON p.id = i.product_id
                     WHERE p.active AND i.quantity_available <= i.low_stock_threshold)    AS low_stock
                  FROM (SELECT count(*)                                           AS total_orders,
                               count(*) FILTER (WHERE status IN %1$s)             AS paid_orders,
                               coalesce(sum(total) FILTER (WHERE status IN %1$s), 0) AS revenue
                          FROM orders) o
                """.formatted(PAID), new MapSqlParameterSource());
    }

    /** Count per status, including statuses with zero orders, in lifecycle order. */
    public Map<String, Long> ordersByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String s : List.of("PLACED", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED")) {
            result.put(s, 0L);
        }
        jdbc.query("SELECT status, count(*) AS n FROM orders GROUP BY status", rs -> {
            result.put(rs.getString("status"), rs.getLong("n"));
        });
        return result;
    }

    /**
     * Revenue and paid orders per day. generate_series produces every day of the range,
     * and the LEFT JOIN keeps days without sales as 0, so the chart has no gaps.
     *
     * <p>Orders are filtered to the range FIRST (a plain comparison on confirmed_at, which can
     * use the partial index ix_orders_paid_confirmed_at) and grouped, then joined to the 30
     * calendar days. Comparing CAST(confirmed_at ...) = day in the join instead would read
     * every paid order ever made.</p>
     */
    public List<DailySales> salesByDay(LocalDate from, LocalDate to) {
        String sql = """
                SELECT CAST(d.day AS date) AS day,
                       coalesce(s.revenue, 0) AS revenue,
                       coalesce(s.orders, 0)  AS orders
                  FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d(day)
                  LEFT JOIN (
                        SELECT CAST(o.confirmed_at AT TIME ZONE %2$s AS date) AS day,
                               sum(o.total) AS revenue,
                               count(*)     AS orders
                          FROM orders o
                         WHERE %1$s
                         GROUP BY 1) s ON s.day = CAST(d.day AS date)
                 ORDER BY d.day
                """.formatted(paidBetween("o"), ZONE);
        return jdbc.query(sql, range(from, to),
                (rs, i) -> new DailySales(rs.getDate("day").toLocalDate(), rs.getBigDecimal("revenue"), rs.getLong("orders")));
    }

    /** Best sellers among orders paid in the range: aggregate by product id, then fetch 5 names. */
    public List<TopProduct> topProducts(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT p.id, p.name, p.slug, t.units, t.revenue
                  FROM (SELECT oi.product_id, sum(oi.quantity) AS units, sum(oi.line_total) AS revenue
                          FROM orders o
                          JOIN order_items oi ON oi.order_id = o.id
                         WHERE %s
                         GROUP BY oi.product_id
                         ORDER BY units DESC, revenue DESC, oi.product_id
                         LIMIT :limit) t
                  JOIN products p ON p.id = t.product_id
                 ORDER BY t.units DESC, t.revenue DESC, p.id
                """.formatted(paidBetween("o"));
        return jdbc.query(sql, range(from, to).addValue("limit", limit), (rs, i) -> new TopProduct(
                rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getLong("units"), rs.getBigDecimal("revenue")));
    }

    /**
     * Paid orders confirmed between the start of :from and the end of :to, in India time.
     * "timestamp AT TIME ZONE zone" turns local midnight into an absolute instant, so the
     * column is compared as-is and the index stays usable.
     */
    private static String paidBetween(String alias) {
        return """
                %1$s.status IN %2$s
                           AND %1$s.confirmed_at >= CAST(CAST(:from AS date) AS timestamp) AT TIME ZONE %3$s
                           AND %1$s.confirmed_at <  CAST(CAST(:to AS date) + 1 AS timestamp) AT TIME ZONE %3$s"""
                .formatted(alias, PAID, ZONE);
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    public List<LowStockItem> lowStock(int limit) {
        return jdbc.query("""
                SELECT p.id, p.name, p.sku, i.quantity_available, i.low_stock_threshold
                  FROM inventory i JOIN products p ON p.id = i.product_id
                 WHERE p.active AND i.quantity_available <= i.low_stock_threshold
                 ORDER BY i.quantity_available, p.name
                 LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (rs, i) -> new LowStockItem(
                rs.getLong("id"), rs.getString("name"), rs.getString("sku"),
                rs.getInt("quantity_available"), rs.getInt("low_stock_threshold")));
    }

    public List<RecentOrder> recentOrders(int limit) {
        return jdbc.query("""
                SELECT o.order_number, u.full_name, o.status, o.total, o.created_at
                  FROM orders o JOIN users u ON u.id = o.user_id
                 ORDER BY o.created_at DESC, o.id DESC
                 LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (rs, i) -> new RecentOrder(
                rs.getString("order_number"), rs.getString("full_name"), rs.getString("status"),
                rs.getBigDecimal("total"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Order count and paid total for one page of customers, in ONE query (no N+1). */
    public Map<Long, CustomerStats> customerStats(Collection<Long> userIds) {
        Map<Long, CustomerStats> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT user_id,
                       count(*) AS orders,
                       coalesce(sum(total) FILTER (WHERE status IN %s), 0) AS spent
                  FROM orders
                 WHERE user_id IN (:ids)
                 GROUP BY user_id
                """.formatted(PAID), new MapSqlParameterSource("ids", userIds), rs -> {
            result.put(rs.getLong("user_id"), new CustomerStats(rs.getLong("orders"), rs.getBigDecimal("spent")));
        });
        return result;
    }

    public record CustomerStats(long orders, BigDecimal spent) {
        public static final CustomerStats NONE = new CustomerStats(0, BigDecimal.ZERO);
    }
}
