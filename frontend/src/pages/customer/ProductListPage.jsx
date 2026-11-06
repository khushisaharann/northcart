import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { PackageSearch, SlidersHorizontal } from 'lucide-react';
import { catalogApi } from '../../api/endpoints';
import { FilterSidebar } from '../../components/product/FilterSidebar';
import { ProductGrid, ProductGridSkeleton } from '../../components/product/ProductCard';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Pagination } from '../../components/ui/Pagination';
import { useAsync } from '../../hooks/useAsync';

const SORTS = [
  { value: '', label: 'Recommended' },
  { value: 'newest', label: 'Newest' },
  { value: 'popular', label: 'Best selling' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
];
const PARAM_KEYS = ['q', 'category', 'minPrice', 'maxPrice', 'inStock', 'sort', 'page'];

/**
 * Product listing AND search results: the URL is the state.
 * /products?q=keyb&category=electronics&sort=price_asc&page=1
 */
export default function ProductListPage() {
  const [params, setParams] = useSearchParams();
  const [showFilters, setShowFilters] = useState(false); // mobile only; always visible on large screens
  const filters = Object.fromEntries(PARAM_KEYS.map((k) => [k, params.get(k)]).filter(([, v]) => v !== null));
  const page = Number(filters.page ?? 0);
  const queryKey = params.toString();

  const categories = useAsync(() => catalogApi.categories(), []);
  const result = useAsync(() => catalogApi.searchProducts({ ...filters, page, size: 12 }), [queryKey]);

  /** Any filter change resets to page 0; null removes the parameter. */
  const update = (changes) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '' || value === undefined) next.delete(key);
      else next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  const activeCategory = categories.data?.find((c) => c.slug === filters.category);
  const title = filters.q ? `Results for "${filters.q}"` : activeCategory?.name ?? 'All products';
  const hasFilters = ['category', 'minPrice', 'maxPrice', 'inStock'].some((k) => filters[k]);

  return (
    <div className="grid gap-8 lg:grid-cols-[220px_1fr]">
      <div>
        <button onClick={() => setShowFilters((v) => !v)} aria-expanded={showFilters}
                className="mb-2 inline-flex h-9 items-center gap-2 rounded-lg px-3 text-sm font-medium text-gray-700 ring-1 ring-gray-300 lg:hidden">
          <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
          {showFilters ? 'Hide filters' : 'Filters'}{hasFilters ? ' (active)' : ''}
        </button>
        <div className={showFilters ? 'block' : 'hidden lg:block'}>
          <FilterSidebar categories={categories.data ?? []} filters={filters} onChange={update} />
        </div>
      </div>

      <div>
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">{title}</h1>
            {result.data && (
              <p className="mt-1 text-sm text-gray-500">
                {result.data.totalElements} {result.data.totalElements === 1 ? 'product' : 'products'}
              </p>
            )}
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-600">
            Sort by
            <select value={filters.sort ?? ''} onChange={(e) => update({ sort: e.target.value || null })}
                    className="h-9 rounded-lg border border-gray-300 bg-white px-2 text-sm outline-none focus:border-brand-500">
              {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
          </label>
        </div>

        {result.loading ? (
          <ProductGridSkeleton count={8} />
        ) : result.error ? (
          <ErrorState error={result.error} onRetry={result.reload} />
        ) : result.data.content.length === 0 ? (
          <EmptyState
            icon={PackageSearch}
            title="No products found"
            description={filters.q ? 'Try a shorter or different search term.' : 'Try removing some filters.'}
            action={(hasFilters || filters.q) && (
              <Button variant="secondary" onClick={() => setParams(new URLSearchParams())}>Clear search and filters</Button>
            )}
          />
        ) : (
          <>
            <ProductGrid products={result.data.content} />
            <Pagination page={page} totalPages={result.data.totalPages} onChange={(p) => update({ page: p })} />
          </>
        )}
      </div>
    </div>
  );
}
