-- =====================================================================
-- DEV-ONLY sample catalog (loaded only by the dev profile, see application-dev.yml).
--
-- "R__" = repeatable migration: Flyway runs it after all versioned migrations and
-- re-runs it whenever this file changes. Every statement is idempotent
-- (ON CONFLICT DO NOTHING / NOT EXISTS), so re-running never duplicates data
-- and never overwrites changes you made through the admin API.
-- Images are seeded placeholder photos from picsum.photos.
-- =====================================================================

INSERT INTO categories (name, slug, description) VALUES
    ('Electronics', 'electronics', 'Gadgets, accessories and peripherals'),
    ('Books',       'books',       'Programming, design and business books'),
    ('Home & Kitchen', 'home-kitchen', 'Everyday essentials for your home'),
    ('Fitness',     'fitness',     'Gear for training at home or the gym')
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE seed_products (
    name          VARCHAR(150),
    slug          VARCHAR(180),
    sku           VARCHAR(50),
    description   TEXT,
    price         NUMERIC(12,2),
    category_slug VARCHAR(100),
    stock         INTEGER,
    sold          INTEGER
) ON COMMIT DROP;

INSERT INTO seed_products VALUES
    ('Wireless Mouse',           'wireless-mouse',           'ELEC-001', 'Ergonomic 2.4 GHz wireless mouse with silent clicks and 18-month battery life.', 799.00,  'electronics', 120, 340),
    ('Mechanical Keyboard',      'mechanical-keyboard',      'ELEC-002', 'Tenkeyless mechanical keyboard with hot-swappable switches and white backlight.', 3499.00, 'electronics', 35, 210),
    ('USB-C Hub 7-in-1',         'usb-c-hub-7-in-1',         'ELEC-003', 'HDMI 4K, 3 USB-A ports, SD card reader and 100 W power delivery pass-through.', 2199.00, 'electronics', 4, 95),
    ('Noise Cancelling Headphones', 'noise-cancelling-headphones', 'ELEC-004', 'Over-ear Bluetooth headphones with active noise cancellation and 30-hour battery.', 8999.00, 'electronics', 18, 160),
    ('Laptop Stand',             'laptop-stand',             'ELEC-005', 'Adjustable aluminium laptop stand for better posture.', 1299.00, 'electronics', 0, 75),
    ('Clean Code',               'clean-code',               'BOOK-001', 'A handbook of agile software craftsmanship: writing readable, maintainable code.', 649.00, 'books', 50, 410),
    ('Designing Data-Intensive Applications', 'designing-data-intensive-applications', 'BOOK-002', 'The big ideas behind reliable, scalable and maintainable systems and databases.', 1899.00, 'books', 22, 280),
    ('Spring Boot in Practice',  'spring-boot-in-practice',  'BOOK-003', 'Hands-on guide to building production-ready Java applications with Spring Boot.', 1499.00, 'books', 3, 60),
    ('Steel Water Bottle 1L',    'steel-water-bottle-1l',    'HOME-001', 'Double-walled insulated bottle that keeps drinks cold for 24 hours.', 899.00, 'home-kitchen', 80, 190),
    ('Ceramic Coffee Mug Set',   'ceramic-coffee-mug-set',   'HOME-002', 'Set of four 350 ml ceramic mugs, microwave and dishwasher safe.', 1199.00, 'home-kitchen', 40, 55),
    ('Yoga Mat 6mm',             'yoga-mat-6mm',             'FIT-001',  'Non-slip 6 mm yoga mat with carrying strap.', 999.00, 'fitness', 60, 230),
    ('Adjustable Dumbbells 10kg', 'adjustable-dumbbells-10kg', 'FIT-002', 'Pair of adjustable dumbbells, 2-10 kg each, with quick-lock collars.', 4599.00, 'fitness', 7, 120);

INSERT INTO products (category_id, name, slug, sku, description, price, sold_count)
SELECT c.id, s.name, s.slug, s.sku, s.description, s.price, s.sold
FROM seed_products s
JOIN categories c ON c.slug = s.category_slug
ON CONFLICT DO NOTHING;

INSERT INTO inventory (product_id, quantity_available, low_stock_threshold)
SELECT p.id, s.stock, 5
FROM seed_products s
JOIN products p ON p.sku = s.sku
ON CONFLICT DO NOTHING;

-- Two images per product, only for products that have none yet.
INSERT INTO product_images (product_id, url, alt_text, sort_order)
SELECT p.id,
       'https://picsum.photos/seed/' || p.slug || '-' || n || '/800/800',
       p.name || ' image ' || (n + 1),
       n
FROM seed_products s
JOIN products p ON p.sku = s.sku
CROSS JOIN generate_series(0, 1) AS n
WHERE NOT EXISTS (SELECT 1 FROM product_images pi WHERE pi.product_id = p.id);
