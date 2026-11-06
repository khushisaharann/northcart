-- =====================================================================================
-- NorthCart: load a LARGE synthetic dataset for query-performance testing.
--
--   20 categories · 50,000 products · 20,000 customers · 200,000 orders
--   ~400,000 order items · a payment row for every paid order
--
-- Run it against an EMPTY, freshly migrated database (never your dev data):
--   createdb ncperf
--   (run Flyway migrations V1..V4 into it, e.g. start the app once pointed at it,
--    or psql -f each V*.sql file in order)
--   psql -d ncperf -f scripts/perf/generate-data.sql      (~30-60 s)
--   psql -d ncperf -f scripts/perf/explain-queries.sql
--
-- Everything is set-based (generate_series), no loops, so it is fast. random() is seeded
-- so two runs produce the same data and the timings are comparable.
-- =====================================================================================
\set ON_ERROR_STOP on
SELECT setseed(0.42);

BEGIN;

-- ---------------------------------------------------------------- roles (V2 seeds them)
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN') ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------- categories
INSERT INTO categories (name, slug, description)
SELECT 'Category ' || g, 'category-' || g, 'Synthetic category ' || g
  FROM generate_series(1, 20) g;

-- ---------------------------------------------------------------- products
-- Names are built from small word lists so full-text search has realistic hits
-- ("wireless", "keyboard", ...) at realistic selectivity.
WITH adj(w)  AS (SELECT unnest(ARRAY['Wireless','Mechanical','Portable','Ergonomic','Classic','Smart',
                                     'Compact','Premium','Organic','Bamboo','Steel','Cotton','Leather',
                                     'Vintage','Ultra','Silent','Foldable','Insulated','Magnetic','Solar'])),
     noun(w) AS (SELECT unnest(ARRAY['Keyboard','Mouse','Lamp','Bottle','Backpack','Headphones','Speaker',
                                     'Notebook','Chair','Desk','Mug','Charger','Cable','Watch','Wallet',
                                     'Jacket','Sneakers','Blender','Kettle','Pillow','Tripod','Monitor',
                                     'Router','Planter','Candle'])),
     a AS (SELECT array_agg(w) AS arr FROM adj),
     n AS (SELECT array_agg(w) AS arr FROM noun)
INSERT INTO products (category_id, name, slug, sku, description, price, active, sold_count, created_at)
SELECT 1 + (g % 20),
       a.arr[1 + (g % 20)] || ' ' || n.arr[1 + ((g / 20) % 25)] || ' ' || g,
       'product-' || g,
       'SKU-' || lpad(g::text, 6, '0'),
       'A ' || lower(a.arr[1 + ((g * 7) % 20)]) || ' item that pairs well with any '
            || lower(n.arr[1 + ((g * 3) % 25)]) || '. Model ' || g || '.',
       round((99 + random() * 19900)::numeric, 2),
       random() > 0.05,                               -- ~5 % soft-deleted
       0,
       now() - (random() * interval '730 days')
  FROM generate_series(1, 50000) g, a, n;

INSERT INTO inventory (product_id, quantity_available, low_stock_threshold)
SELECT id,
       CASE WHEN random() < 0.08 THEN 0 ELSE (random() * 200)::int END,
       5
  FROM products;

-- ---------------------------------------------------------------- customers
INSERT INTO users (email, password_hash, full_name, phone, created_at)
SELECT 'customer' || g || '@perf.test',
       '$2a$12$PerfTestHashPerfTestHashPerfTestHashPerfTestHashPerfTe',  -- not a real login
       'Customer ' || g,
       '98' || lpad(g::text, 8, '0'),
       now() - (random() * interval '730 days')
  FROM generate_series(1, 20000) g;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE r.name = 'CUSTOMER';

-- ---------------------------------------------------------------- orders
-- Status mix: 70 % delivered/shipped/confirmed (paid), 20 % cancelled, 10 % placed.
-- A few PLACED orders are older than 30 minutes so the expiry job query has work.
CREATE TEMP TABLE tmp_orders AS
SELECT g AS n,
       1 + floor(random() * 20000)::int AS user_id,
       now() - (power(random(), 1.5) * interval '365 days') AS created_at,  -- skewed towards recent
       random() AS r
  FROM generate_series(1, 200000) g;

INSERT INTO orders (order_number, user_id, status, subtotal, shipping_fee, total,
                    ship_full_name, ship_phone, ship_line1, ship_city, ship_state, ship_postal_code,
                    ship_country, cancelled_at, confirmed_at, shipped_at, delivered_at, created_at, updated_at)
SELECT 'NC-' || to_char(t.created_at, 'YYYYMMDD') || '-' || upper(lpad(to_hex(t.n), 6, '0')),
       t.user_id,
       s.status,
       0, 0, 0,                                          -- fixed up after items are inserted
       'Customer ' || t.user_id, '9800000000', '12 MG Road', 'Bengaluru', 'Karnataka', '560001', 'India',
       CASE WHEN s.status = 'CANCELLED' THEN t.created_at + interval '1 hour' END,
       CASE WHEN s.status IN ('CONFIRMED','SHIPPED','DELIVERED') THEN t.created_at + interval '3 minutes' END,
       CASE WHEN s.status IN ('SHIPPED','DELIVERED') THEN t.created_at + interval '1 day' END,
       CASE WHEN s.status = 'DELIVERED' THEN t.created_at + interval '4 days' END,
       t.created_at, t.created_at
  FROM tmp_orders t
 CROSS JOIN LATERAL (SELECT CASE
          WHEN t.r < 0.50 THEN 'DELIVERED'
          WHEN t.r < 0.60 THEN 'SHIPPED'
          WHEN t.r < 0.70 THEN 'CONFIRMED'
          WHEN t.r < 0.90 THEN 'CANCELLED'
          ELSE 'PLACED' END AS status) s;

-- 1-3 distinct products per order (product id derived from the order id, so no duplicates)
INSERT INTO order_items (order_id, product_id, product_name, sku, unit_price, quantity, line_total)
SELECT o.id, p.id, p.name, p.sku, p.price, q.qty, p.price * q.qty
  FROM orders o
 CROSS JOIN LATERAL generate_series(1, 1 + (o.id % 3)::int) k
  JOIN products p ON p.id = 1 + ((o.id * 7919 + k * 104729) % 50000)
 CROSS JOIN LATERAL (SELECT 1 + (o.id + k) % 3 AS qty) q;

UPDATE orders o
   SET subtotal = x.subtotal,
       shipping_fee = CASE WHEN x.subtotal >= 999 THEN 0 ELSE 49 END,
       total = x.subtotal + CASE WHEN x.subtotal >= 999 THEN 0 ELSE 49 END
  FROM (SELECT order_id, sum(line_total) AS subtotal FROM order_items GROUP BY order_id) x
 WHERE x.order_id = o.id;

-- popularity counter, as ProductService.recordSale would have maintained it
UPDATE products p SET sold_count = x.units
  FROM (SELECT oi.product_id, sum(oi.quantity) AS units
          FROM order_items oi JOIN orders o ON o.id = oi.order_id
         WHERE o.status IN ('CONFIRMED','SHIPPED','DELIVERED')
         GROUP BY oi.product_id) x
 WHERE x.product_id = p.id;

-- ---------------------------------------------------------------- payments
INSERT INTO payments (order_id, provider, provider_order_id, provider_payment_id, amount, status, paid_at, created_at)
SELECT id, 'FAKE', 'fake_order_' || id, 'fake_pay_' || id, total, 'SUCCESS', confirmed_at, created_at
  FROM orders WHERE status IN ('CONFIRMED','SHIPPED','DELIVERED');

INSERT INTO payments (order_id, provider, provider_order_id, amount, status, created_at)
SELECT id, 'FAKE', 'fake_order_' || id, total, 'PENDING', created_at
  FROM orders WHERE status = 'PLACED';

COMMIT;

-- Fresh statistics, so the planner sees the real row counts.
VACUUM ANALYZE;

SELECT (SELECT count(*) FROM products)    AS products,
       (SELECT count(*) FROM users)       AS users,
       (SELECT count(*) FROM orders)      AS orders,
       (SELECT count(*) FROM order_items) AS order_items,
       (SELECT count(*) FROM payments)    AS payments;
