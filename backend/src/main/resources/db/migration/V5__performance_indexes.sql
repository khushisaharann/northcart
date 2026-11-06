-- =====================================================================================
-- V5: changes found by EXPLAIN ANALYZE on a 50k-product / 200k-order dataset.
-- Details and before/after timings: docs/performance.md
-- =====================================================================================

-- 1) SKUs are upper-cased by AdminProductService. Enforcing it lets product search compare
--    "p.sku = upper(:text)", which can use the existing unique index uk_products_sku.
--    The old "upper(p.sku) = upper(:text)" could not use any index, and because it sat
--    inside an OR with the full-text match, it made Postgres scan the whole products table
--    instead of using the GIN index (27 ms -> 3 ms on 50k products).
ALTER TABLE products
    ADD CONSTRAINT ck_products_sku_uppercase CHECK (sku = upper(sku));

-- 2) The dashboard charts only look at paid orders confirmed in the selected range.
--    A PARTIAL index holds just those rows (paid orders), ordered by confirmation time,
--    so "last 30 days" reads ~13 % of the table instead of all of it.
--    The WHERE clause must match the queries' status filter for the planner to use it.
CREATE INDEX ix_orders_paid_confirmed_at ON orders (confirmed_at)
    WHERE status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED');
