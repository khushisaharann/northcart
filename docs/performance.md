# Query performance

How the hot queries behave on a realistic amount of data, what was changed because of it
(migration **V5**), and what was deliberately left alone.

## Method

| | |
|---|---|
| Dataset | 20 categories · **50,000 products** · 20,000 customers · **200,000 orders** · 400,000 order items · 160,000 payments |
| Generator | [`scripts/perf/generate-data.sql`](../scripts/perf/generate-data.sql) (seeded, ~30 s) |
| Queries | [`scripts/perf/explain-queries.sql`](../scripts/perf/explain-queries.sql), copied verbatim from the repositories |
| Measure | `EXPLAIN (ANALYZE, BUFFERS)`, **median of 5 runs**, warm cache, PostgreSQL 16, laptop-class machine |

Absolute numbers depend on hardware; the *ratios* and the *plan shapes* are what matter.

Reproduce:

```bash
createdb ncperf
for f in backend/src/main/resources/db/migration/V*.sql; do psql -d ncperf -f "$f"; done
psql -d ncperf -f scripts/perf/generate-data.sql
psql -d ncperf -f scripts/perf/explain-queries.sql
```

## Results

| # | Query (where it runs) | Before | After | Plan after |
|---|---|---:|---:|---|
| Q1 | Search "wire keyb", relevance sort (storefront) | 18.6 ms | **3.4 ms** | BitmapOr of the GIN index + `uk_products_sku` |
| Q2 | Category + price range + in stock, cheapest first | 0.6 ms | 0.7 ms | Index scan on `ix_products_price` |
| Q3 | Home page: all products, newest first, page 1 | 0.1 ms | 0.1 ms | Index scan on `ix_products_created_at`, stops after 12 rows |
| Q3b | Same, page 200 (OFFSET 2388) | 7.5 ms | 7.9 ms | Same index, reads and throws away 2,388 rows |
| Q3c | `count(*)` for the pagination of Q3 | 41 ms | 43 ms | Seq scan (needs every row) |
| Q4 | My orders, page 1 | 0.07 ms | 0.07 ms | `ix_orders_user_created_at` |
| Q5 | Admin orders filtered by status | 0.07 ms | 0.07 ms | Backward scan on `ix_orders_status_created_at` |
| Q5b | Admin orders free-text search (`LIKE '%…%'`) | 118 ms | 128 ms | Seq scan, see "Left alone" |
| Q6 | Dashboard: sales per day, 30 days | 146 ms | **24 ms** | Partial index `ix_orders_paid_confirmed_at` |
| Q7 | Dashboard: top 5 products | 397 ms | **117 ms** | Partial index on orders, then a hash join to items |
| Q8 | Dashboard: summary tiles | 121 ms | **79 ms** | One pass over orders instead of three |
| Q9 | Expiry job: next 50 unpaid orders past the deadline | 0.16 ms | 0.14 ms | `ix_orders_status_created_at` |
| Q10 | Order stats for one admin page of 20 customers | 0.48 ms | 0.45 ms | `ix_orders_user_created_at`, one query, no N+1 |

The whole admin dashboard (Q6 + Q7 + Q8 plus the small queries) went from roughly **670 ms to 225 ms**.
Every storefront and customer query was already under 10 ms, apart from the count in Q3c.

## What was changed

### 1. Search: an `OR` that switched off the index (Q1)

```sql
-- before
WHERE p.search_vector @@ to_tsquery(...) OR upper(p.sku) = upper(:text)
-- after
WHERE p.search_vector @@ to_tsquery(...) OR p.sku = upper(trim(:text))
```

`upper(p.sku)` applies a function to the **column**, so no index on `sku` can be used.
Postgres can only use indexes for an `OR` if **both sides** have one (it combines them with a
`BitmapOr`). One side had none, so it gave up on the GIN index as well and scanned all 50,000 products.

The fix moves `upper()` to the **parameter**. That is only correct if SKUs are always stored in
upper case. `AdminProductService` already upper-cases them, and V5 now enforces it:
`ck_products_sku_uppercase CHECK (sku = upper(sku))`. The existing unique index `uk_products_sku`
then serves the SKU side, so no new index was needed.

> **Rule:** keep indexed columns bare in `WHERE`. Put the function on the value, or index
> the expression.

### 2. Dashboard: filter first, then group (Q6, Q7)

The sales chart used to join 30 calendar days to orders with
`CAST(o.confirmed_at AT TIME ZONE 'Asia/Kolkata' AS date) = d.day`. That is correct, but there is no
range condition on `confirmed_at`, so it read **every paid order ever** (140k rows) and sorted them
on disk (`external merge Disk: 6200kB`) to discard all but the last 30 days.

Now:

1. The query filters on the bare column: `confirmed_at >= <from, midnight IST> AND confirmed_at < <to + 1 day, midnight IST>`.
   `CAST(date AS timestamp) AT TIME ZONE 'Asia/Kolkata'` turns local midnight into an absolute instant,
   so the result is the same whatever time zone the DB session uses (tested in UTC, IST and New York).
2. It groups those orders by day, then LEFT JOINs the 30-day series so empty days still appear as 0.
3. A **partial index** covers only paid orders:
   ```sql
   CREATE INDEX ix_orders_paid_confirmed_at ON orders (confirmed_at)
       WHERE status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED');
   ```
   It is 3 MB against an 80 MB table, and unpaid or cancelled orders never touch it. The planner uses
   a partial index only when the query's `WHERE` implies the index's `WHERE`, so both use the exact
   same status list (`AdminStatsRepository.PAID`).

The old and new versions were compared on the full dataset: **0 differing rows**.
`AdminStatsRepositoryTest` pins the edges: 23:59:59 IST is the previous day, both ends of the range
are included, and a paid-then-cancelled order is excluded.

**Behaviour change (intended):** "Best sellers" now follows the selected range (7/30/90 days), like
the chart beside it, instead of all time. All-time best sellers grow more expensive as orders pile
up, and they stop telling you what is selling *now*. The card label says "last N days".

Q7 is still the slowest query at 117 ms, because ~26k orders and ~53k item rows fall in a busy
30-day window. Forcing an index nested loop was slower (158 ms), so the planner's hash join is
the right plan. If it ever matters, see "Next steps".

### 3. Summary tiles: one scan instead of three (Q8)

`total_orders`, `paid_orders` and `revenue` were three separate subqueries over `orders`. Using
`count(*) FILTER (WHERE …)` and `sum(total) FILTER (WHERE …)` computes all three in a single pass.

## Left alone, on purpose

| Query | Why it stays |
|---|---|
| **Q5b** admin text search, 120 ms | `LIKE '%text%'` can't use a B-tree. The fix is a `pg_trgm` GIN index on email, name and order number. It is admin-only, used a few times a day, and adding an extension costs ops work (managed databases need it allow-listed). Worth doing at ~1M orders, or if customer support uses it all day. |
| **Q3c** `count(*)` per catalog page, 43 ms | Exact totals need every matching row. Options when it matters: cache the count for a minute; show "1,000+" past a cap (`SELECT count(*) FROM (… LIMIT 1001)`); or drop totals and use "Load more". Not worth the complexity at 50k products. |
| **Q3b** deep OFFSET, 8 ms at page 200 | OFFSET reads and discards the skipped rows, so cost grows with page number. Keyset pagination (`WHERE (created_at, id) < (:lastCreated, :lastId)`) is constant time, but it can't jump to page N. Nobody browses to page 200, so this is fine. |
| Unused-looking indexes | `ix_products_sold_count`, `ix_inventory_quantity_available` are used by sort/filter combinations not in this list. Check `pg_stat_user_indexes.idx_scan` in production before dropping anything. |

## Application-level checks (already in place)

- **No N+1 queries**:
  - Product pages fetch ids first, then entities in one query.
  - `default_batch_fetch_size: 50` batches lazy collections.
  - The admin customer list loads its stats with one `IN (...)` query.
- **`open-in-view: false`**: no lazy loading from controllers, so accidental queries fail loudly in tests.
- **Response compression**: JSON responses over 1 KB are gzipped (`server.compression`).
- **Atomic stock updates**: a single conditional `UPDATE … WHERE quantity_available >= :qty`. No read-modify-write, and no lock held across a round-trip.

## Next steps if traffic grows

1. **Daily roll-up table** (`daily_sales(day, product_id, units, revenue)`), filled when an order is
   confirmed or refunded. Dashboard queries then read about 30 × N rows instead of thousands of orders.
2. `pg_trgm` for the admin search (above).
3. Cache the public catalog (categories, the first page) for 30–60 s. It is read constantly and rarely changes.
4. Turn on `pg_stat_statements` in production, and tune whatever is actually slow. Measure first, don't guess.
