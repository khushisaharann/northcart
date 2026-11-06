-- =====================================================================
-- V4: payment refunds, a local FAKE provider, and "one successful payment per order".
-- =====================================================================

-- FAKE = local simulator used in development and tests (no real money).
ALTER TABLE payments DROP CONSTRAINT ck_payments_provider;
ALTER TABLE payments ADD CONSTRAINT ck_payments_provider CHECK (provider IN ('RAZORPAY', 'FAKE'));

-- REFUNDED = money was captured and then returned (cancelled paid order, late payment).
ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'));

ALTER TABLE payments
    ADD COLUMN provider_refund_id VARCHAR(100),
    ADD COLUMN refunded_at        TIMESTAMPTZ;

-- Captured money always has a gateway payment id and a timestamp.
ALTER TABLE payments DROP CONSTRAINT ck_payments_success_has_payment_id;
ALTER TABLE payments ADD CONSTRAINT ck_payments_captured_has_payment_id
    CHECK (status NOT IN ('SUCCESS', 'REFUNDED')
           OR (provider_payment_id IS NOT NULL AND paid_at IS NOT NULL));

ALTER TABLE payments ADD CONSTRAINT ck_payments_refunded_consistent
    CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL AND provider_refund_id IS NOT NULL));

-- An order can be paid at most once, enforced by the database.
CREATE UNIQUE INDEX ux_payments_one_success_per_order
    ON payments (order_id) WHERE status = 'SUCCESS';

-- Reconciliation job: "pending payments" lookups.
CREATE INDEX ix_payments_status ON payments (status) WHERE status = 'PENDING';
