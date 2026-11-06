-- =====================================================================
-- NorthCart - V1: initial schema
-- PostgreSQL 14+
--
-- Conventions
--   * Surrogate keys: BIGINT GENERATED ALWAYS AS IDENTITY
--   * Money: NUMERIC(12,2)  (never FLOAT/DOUBLE)
--   * Time:  TIMESTAMPTZ    (stored in UTC, mapped to java.time.Instant)
--   * Enums: VARCHAR + CHECK (mapped with @Enumerated(EnumType.STRING))
--   * Named constraints: pk_, fk_, uk_, ck_, ix_ / ux_ prefixes so error
--     messages tell you exactly which rule was violated.
--   * PostgreSQL does NOT auto-index foreign keys, so every FK we join or
--     filter on gets an explicit index below.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Users, roles, auth
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    name        VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    email          VARCHAR(255) NOT NULL,          -- stored lower-case by the service layer
    password_hash  VARCHAR(100) NOT NULL,          -- BCrypt hash (60 chars), never plaintext
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

-- Many-to-many join table. Composite PK prevents assigning the same role twice.
CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);
-- PK index covers (user_id, ...) lookups; this one covers "all users with role X".
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE password_reset_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,             -- SHA-256 hex of the emailed token
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,                       -- NULL = not yet used
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);


-- ---------------------------------------------------------------------
-- 2. Addresses
-- ---------------------------------------------------------------------

CREATE TABLE addresses (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id      BIGINT       NOT NULL,
    full_name    VARCHAR(100) NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    line1        VARCHAR(200) NOT NULL,
    line2        VARCHAR(200),
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(20)  NOT NULL,
    country      VARCHAR(60)  NOT NULL DEFAULT 'India',
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_addresses_user_id ON addresses (user_id);
-- Partial unique index: at most ONE default address per user, enforced by the DB.
CREATE UNIQUE INDEX ux_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;


-- ---------------------------------------------------------------------
-- 3. Catalog
-- ---------------------------------------------------------------------

CREATE TABLE categories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    name         VARCHAR(80)  NOT NULL,
    slug         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    category_id  BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    slug         VARCHAR(180)  NOT NULL,
    sku          VARCHAR(50)   NOT NULL,
    description  TEXT,
    price        NUMERIC(12,2) NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,   -- soft delete: never hard-delete a product
    sold_count   INTEGER       NOT NULL DEFAULT 0,      -- denormalised for "sort by popularity"
    -- Full-text search document, maintained by PostgreSQL itself.
    -- Name matches (weight A) rank higher than description matches (weight B).
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')
    ) STORED,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_products_sold_count_non_negative CHECK (sold_count >= 0),
    -- RESTRICT: a category that still has products cannot be deleted.
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);
CREATE INDEX ix_products_category_id ON products (category_id);
CREATE INDEX ix_products_price       ON products (price);
CREATE INDEX ix_products_created_at  ON products (created_at DESC);
CREATE INDEX ix_products_sold_count  ON products (sold_count DESC);
CREATE INDEX ix_products_search      ON products USING GIN (search_vector);

CREATE TABLE product_images (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id  BIGINT       NOT NULL,
    url         VARCHAR(500) NOT NULL,
    alt_text    VARCHAR(150),
    sort_order  INTEGER      NOT NULL DEFAULT 0,     -- 0 = primary/thumbnail image
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_images_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX ix_product_images_product_id ON product_images (product_id, sort_order);


-- ---------------------------------------------------------------------
-- 4. Inventory (1:1 with products)
-- ---------------------------------------------------------------------

CREATE TABLE inventory (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id           BIGINT      NOT NULL,
    quantity_available   INTEGER     NOT NULL DEFAULT 0,
    low_stock_threshold  INTEGER     NOT NULL DEFAULT 5,
    version              BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking (@Version)
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory PRIMARY KEY (id),
    CONSTRAINT uk_inventory_product UNIQUE (product_id),    -- makes the relationship 1:1
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    -- Last line of defence against overselling.
    CONSTRAINT ck_inventory_quantity_non_negative CHECK (quantity_available >= 0),
    CONSTRAINT ck_inventory_threshold_non_negative CHECK (low_stock_threshold >= 0)
);
CREATE INDEX ix_inventory_quantity_available ON inventory (quantity_available);


-- ---------------------------------------------------------------------
-- 5. Cart (one per user)
-- ---------------------------------------------------------------------

CREATE TABLE carts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- No price column on purpose: cart prices are always read live from products.
CREATE TABLE cart_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    cart_id     BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),  -- same product = one line
    CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX ix_cart_items_product_id ON cart_items (product_id);


-- ---------------------------------------------------------------------
-- 6. Orders
-- ---------------------------------------------------------------------

-- "order" is a reserved SQL keyword, hence the plural table name.
CREATE TABLE orders (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    order_number      VARCHAR(30)   NOT NULL,          -- public id, e.g. NC-20261106-7F3K9A
    user_id           BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    subtotal          NUMERIC(12,2) NOT NULL,
    shipping_fee      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total             NUMERIC(12,2) NOT NULL,
    -- Shipping address SNAPSHOT (copied at checkout, not a FK to addresses)
    ship_full_name    VARCHAR(100)  NOT NULL,
    ship_phone        VARCHAR(20)   NOT NULL,
    ship_line1        VARCHAR(200)  NOT NULL,
    ship_line2        VARCHAR(200),
    ship_city         VARCHAR(100)  NOT NULL,
    ship_state        VARCHAR(100)  NOT NULL,
    ship_postal_code  VARCHAR(20)   NOT NULL,
    ship_country      VARCHAR(60)   NOT NULL,
    idempotency_key   VARCHAR(64),                     -- blocks duplicate checkout submits
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN ('PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_amounts_non_negative CHECK (subtotal >= 0 AND shipping_fee >= 0 AND total >= 0),
    CONSTRAINT ck_orders_total_matches CHECK (total = subtotal + shipping_fee),
    -- RESTRICT: never lose order history because a user was deleted (disable users instead).
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);
-- "My orders, newest first" -> one index range scan, no sort step.
CREATE INDEX ix_orders_user_created_at   ON orders (user_id, created_at DESC);
-- Admin filters by status + the "cancel unpaid PLACED orders" scheduled job.
CREATE INDEX ix_orders_status_created_at ON orders (status, created_at);

CREATE TABLE order_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id      BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,              -- kept for links/analytics only
    -- SNAPSHOT of the product at purchase time
    product_name  VARCHAR(150)  NOT NULL,
    sku           VARCHAR(50)   NOT NULL,
    unit_price    NUMERIC(12,2) NOT NULL,
    quantity      INTEGER       NOT NULL,
    line_total    NUMERIC(12,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_line_total_matches CHECK (line_total = unit_price * quantity),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
);
-- uk_order_items_order_product already indexes (order_id, ...); this one serves best-seller reports.
CREATE INDEX ix_order_items_product_id ON order_items (product_id);


-- ---------------------------------------------------------------------
-- 7. Payments (one order can have several attempts)
-- ---------------------------------------------------------------------

CREATE TABLE payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id            BIGINT        NOT NULL,
    provider            VARCHAR(20)   NOT NULL,          -- RAZORPAY
    provider_order_id   VARCHAR(100)  NOT NULL,          -- e.g. order_Pxyz... from Razorpay
    provider_payment_id VARCHAR(100),                    -- set only after the customer pays
    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    failure_reason      VARCHAR(500),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_provider_order_id UNIQUE (provider_order_id),
    -- UNIQUE allows many NULLs in PostgreSQL, so pending rows are fine,
    -- but one gateway payment can never be recorded twice (idempotent verify).
    CONSTRAINT uk_payments_provider_payment_id UNIQUE (provider_payment_id),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('RAZORPAY')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_success_has_payment_id
        CHECK (status <> 'SUCCESS' OR (provider_payment_id IS NOT NULL AND paid_at IS NOT NULL)),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT
);
CREATE INDEX ix_payments_order_id ON payments (order_id);
