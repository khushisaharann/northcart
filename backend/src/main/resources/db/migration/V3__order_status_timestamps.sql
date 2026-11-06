-- =====================================================================
-- V3: timestamps for each order status, used by the order tracking page.
--
-- A new migration file, never an edit of V1: V1 has already run on every
-- existing database, and Flyway would reject the changed checksum.
-- =====================================================================

ALTER TABLE orders
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN shipped_at   TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ;

-- cancelled_at is set if and only if the order is CANCELLED.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_cancelled_at_matches_status
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));
