import { useEffect, useState } from 'react';
import { useDebounce } from '../../hooks/useDebounce';

/**
 * Filters live in the URL (?category=books&minPrice=500 ...), not in component state:
 * results are bookmarkable and shareable, and the browser Back button works.
 */
export function FilterSidebar({ categories, filters, onChange }) {
  const [minPrice, setMinPrice] = useState(filters.minPrice ?? '');
  const [maxPrice, setMaxPrice] = useState(filters.maxPrice ?? '');
  const debouncedMin = useDebounce(minPrice);
  const debouncedMax = useDebounce(maxPrice);

  // Keep inputs in sync when the URL changes from outside (e.g. "Clear filters", Back button).
  useEffect(() => {
    setMinPrice(filters.minPrice ?? '');
  }, [filters.minPrice]);
  useEffect(() => {
    setMaxPrice(filters.maxPrice ?? '');
  }, [filters.maxPrice]);

  useEffect(() => {
    if ((filters.minPrice ?? '') !== debouncedMin || (filters.maxPrice ?? '') !== debouncedMax) {
      onChange({ minPrice: debouncedMin || null, maxPrice: debouncedMax || null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedMin, debouncedMax]);

  const categoryButton = (slug, label, count) => {
    const active = (filters.category ?? null) === slug;
    return (
      <li key={slug ?? 'all'}>
        <button onClick={() => onChange({ category: slug })} aria-pressed={active}
                className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm ${
                  active ? 'bg-brand-50 font-semibold text-brand-700' : 'text-gray-700 hover:bg-gray-100'}`}>
          {label}
          {count !== undefined && <span className="text-xs text-gray-400">{count}</span>}
        </button>
      </li>
    );
  };

  return (
    <aside className="space-y-6" aria-label="Filters">
      <section>
        <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">Category</h2>
        <ul className="space-y-0.5">
          {categoryButton(null, 'All products')}
          {categories.map((c) => categoryButton(c.slug, c.name, c.productCount))}
        </ul>
      </section>

      <section>
        <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">Price (₹)</h2>
        <div className="flex items-center gap-2">
          <input type="number" min="0" inputMode="numeric" placeholder="Min" aria-label="Minimum price"
                 value={minPrice} onChange={(e) => setMinPrice(e.target.value)}
                 className="h-9 w-full rounded-lg border border-gray-300 px-2 text-sm outline-none focus:border-brand-500" />
          <span className="text-gray-400">–</span>
          <input type="number" min="0" inputMode="numeric" placeholder="Max" aria-label="Maximum price"
                 value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)}
                 className="h-9 w-full rounded-lg border border-gray-300 px-2 text-sm outline-none focus:border-brand-500" />
        </div>
      </section>

      <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700">
        <input type="checkbox" checked={filters.inStock === 'true'}
               onChange={(e) => onChange({ inStock: e.target.checked ? 'true' : null })}
               className="h-4 w-4 rounded border-gray-300 text-brand-600" />
        In stock only
      </label>
    </aside>
  );
}
