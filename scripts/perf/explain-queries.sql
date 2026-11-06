-- =====================================================================================
-- NorthCart: EXPLAIN ANALYZE the hot queries, copied from the repositories.
-- Run after generate-data.sql:   psql -d ncperf -f scripts/perf/explain-queries.sql
--
-- Read each plan bottom-up. Look for: "Index Scan"/"Bitmap Index Scan" (good),
-- "Seq Scan" on a big table inside a hot path (suspicious), the "actual time" of the top
-- node, and "Rows Removed by Filter" (work thrown away).
-- =====================================================================================
\pset pager off
\timing off

\echo '=== Q1  storefront search "wire keyb", relevance, page 1 (ProductSearchRepository)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id
  FROM products p JOIN categories c ON c.id = p.category_id JOIN inventory i ON i.product_id = p.id
 WHERE 1 = 1 AND p.active AND c.active
   AND (p.search_vector @@ to_tsquery('english', 'wire:* & keyb:*') OR p.sku = upper(trim('wire keyb')))
 ORDER BY ts_rank(p.search_vector, to_tsquery('english', 'wire:* & keyb:*')) DESC, p.id DESC
 LIMIT 12 OFFSET 0;

\echo '=== Q2  category + price range, cheapest first, page 1'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id
  FROM products p JOIN categories c ON c.id = p.category_id JOIN inventory i ON i.product_id = p.id
 WHERE 1 = 1 AND p.active AND c.active
   AND c.slug = 'category-7' AND p.price >= 500 AND p.price <= 2000 AND i.quantity_available > 0
 ORDER BY p.price ASC, p.id DESC
 LIMIT 12 OFFSET 0;

\echo '=== Q3  browse everything, newest first, page 1 (the home page)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id
  FROM products p JOIN categories c ON c.id = p.category_id JOIN inventory i ON i.product_id = p.id
 WHERE 1 = 1 AND p.active AND c.active
 ORDER BY p.created_at DESC, p.id DESC
 LIMIT 12 OFFSET 0;

\echo '=== Q3b same, page 200 (deep OFFSET)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id
  FROM products p JOIN categories c ON c.id = p.category_id JOIN inventory i ON i.product_id = p.id
 WHERE 1 = 1 AND p.active AND c.active
 ORDER BY p.created_at DESC, p.id DESC
 LIMIT 12 OFFSET 2388;

\echo '=== Q3c the COUNT(*) behind the pagination of Q3'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT count(*)
  FROM products p JOIN categories c ON c.id = p.category_id JOIN inventory i ON i.product_id = p.id
 WHERE 1 = 1 AND p.active AND c.active;

\echo '=== Q4  "My orders" page 1 (OrderRepository.findByUserId, newest first)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT o.* FROM orders o WHERE o.user_id = 4242 ORDER BY o.created_at DESC, o.id DESC LIMIT 10;

\echo '=== Q5  admin orders filtered by status (OrderSpecifications, newest first)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT o.* FROM orders o WHERE o.status = 'SHIPPED' ORDER BY o.created_at DESC, o.id DESC LIMIT 20;

\echo '=== Q5b admin orders text search (contains, on order number / email / name)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT o.* FROM orders o JOIN users u ON u.id = o.user_id
 WHERE lower(o.order_number) LIKE '%customer4242@%' ESCAPE '\'
    OR lower(u.email)        LIKE '%customer4242@%' ESCAPE '\'
    OR lower(u.full_name)    LIKE '%customer4242@%' ESCAPE '\'
 ORDER BY o.created_at DESC, o.id DESC LIMIT 20;

\echo '=== Q6  dashboard: sales by day, last 30 days (AdminStatsRepository.salesByDay)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT CAST(d.day AS date) AS day, coalesce(s.revenue, 0) AS revenue, coalesce(s.orders, 0) AS orders
  FROM generate_series(CAST(current_date - 29 AS date), CAST(current_date AS date), interval '1 day') AS d(day)
  LEFT JOIN (
        SELECT CAST(o.confirmed_at AT TIME ZONE 'Asia/Kolkata' AS date) AS day, sum(o.total) AS revenue, count(*) AS orders
          FROM orders o
         WHERE o.status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED')
           AND o.confirmed_at >= CAST(CAST(current_date - 29 AS date) AS timestamp) AT TIME ZONE 'Asia/Kolkata'
           AND o.confirmed_at <  CAST(CAST(current_date AS date) + 1 AS timestamp) AT TIME ZONE 'Asia/Kolkata'
         GROUP BY 1) s ON s.day = CAST(d.day AS date)
 ORDER BY d.day;

\echo '=== Q7  dashboard: top products, last 30 days (AdminStatsRepository.topProducts)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id, p.name, p.slug, t.units, t.revenue
  FROM (SELECT oi.product_id, sum(oi.quantity) AS units, sum(oi.line_total) AS revenue
          FROM orders o JOIN order_items oi ON oi.order_id = o.id
         WHERE o.status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED')
           AND o.confirmed_at >= CAST(CAST(current_date - 29 AS date) AS timestamp) AT TIME ZONE 'Asia/Kolkata'
           AND o.confirmed_at <  CAST(CAST(current_date AS date) + 1 AS timestamp) AT TIME ZONE 'Asia/Kolkata'
         GROUP BY oi.product_id
         ORDER BY units DESC, revenue DESC, oi.product_id
         LIMIT 5) t
  JOIN products p ON p.id = t.product_id
 ORDER BY t.units DESC, t.revenue DESC, p.id;

\echo '=== Q8  dashboard: summary tiles (one pass over orders)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT
  (SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id
     JOIN roles r ON r.id = ur.role_id AND r.name = 'CUSTOMER')                   AS customers,
  (SELECT count(*) FROM products WHERE active)                                      AS active_products,
  o.total_orders, o.paid_orders, o.revenue,
  (SELECT count(*) FROM inventory i JOIN products p ON p.id = i.product_id
     WHERE p.active AND i.quantity_available <= i.low_stock_threshold)              AS low_stock
  FROM (SELECT count(*) AS total_orders,
               count(*) FILTER (WHERE status IN ('CONFIRMED','SHIPPED','DELIVERED')) AS paid_orders,
               coalesce(sum(total) FILTER (WHERE status IN ('CONFIRMED','SHIPPED','DELIVERED')), 0) AS revenue
          FROM orders) o;

\echo '=== Q9  expiry job batch (OrderRepository.lockExpiredPlacedOrderIds)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT id FROM orders
 WHERE status = 'PLACED' AND created_at < now() - interval '30 minutes'
 ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED;

\echo '=== Q10 customer stats for one admin page of 20 customers (no N+1)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT user_id, count(*) AS orders,
       coalesce(sum(total) FILTER (WHERE status IN ('CONFIRMED','SHIPPED','DELIVERED')), 0) AS spent
  FROM orders WHERE user_id IN (SELECT generate_series(1000, 1019)) GROUP BY user_id;
