import { Link, useSearchParams } from 'react-router';
import { Package, Plus } from 'lucide-react';
import { adminApi, catalogApi } from '../../api/endpoints';
import { SearchInput } from '../../components/admin/SearchInput';
import { Table, Td } from '../../components/admin/Table';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { ProductImage } from '../../components/ui/ProductImage';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { formatPrice } from '../../utils/format';

export function StockBadge({ product }) {
  if (product.availableQuantity === 0) return <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Out of stock</span>;
  if (product.lowStock) return <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">{product.availableQuantity} · low</span>;
  return <span className="tabular-nums text-gray-700">{product.availableQuantity}</span>;
}

/** All products, including inactive ones. Filters live in the URL like the shop. */
export default function AdminProductsPage({ lowStockOnly = false }) {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const page = Number(params.get('page') ?? 0);
  const categories = useAsync(() => catalogApi.categories(), []);
  const { data, loading, error, reload } = useAsync(
    () => adminApi.products({ q: q || undefined, category: category || undefined, lowStock: lowStockOnly || undefined, page, size: 20 }),
    [q, category, page, lowStockOnly],
  );

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  return (
    <>
      <PageHeader title="Products" subtitle="Inactive products are hidden from the shop but kept for order history."
                  actions={<Link to="/admin/products/new" className="inline-flex h-10 items-center gap-2 rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700">
                    <Plus className="h-4 w-4" aria-hidden="true" /> New product</Link>} />
      <div className="mb-4 flex flex-wrap gap-3">
        <SearchInput value={q} onChange={(v) => update({ q: v })} placeholder="Name, description or SKU" label="Search products" />
        <select value={category} onChange={(e) => update({ category: e.target.value })} aria-label="Category"
                className="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm">
          <option value="">All categories</option>
          {(categories.data ?? []).map((c) => <option key={c.id} value={c.slug}>{c.name}</option>)}
        </select>
      </div>
      {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : data.content.length === 0 ? (
        <EmptyState icon={Package} title="No products match" description="Try another search." />
      ) : (
        <>
          <Table head={['Product', 'SKU', 'Category', { label: 'Price', align: 'right' }, { label: 'Stock', align: 'right' }, { label: 'Sold', align: 'right' }, 'Status', '']} caption="Products">
            {data.content.map((p) => (
              <tr key={p.id} className="hover:bg-gray-50">
                <Td>
                  <div className="flex items-center gap-3">
                    <ProductImage src={p.images[0]?.url} alt="" className="h-10 w-10 rounded-md" />
                    <span className="max-w-64 truncate font-medium text-gray-900">{p.name}</span>
                  </div>
                </Td>
                <Td className="text-gray-500">{p.sku}</Td>
                <Td>{p.category.name}</Td>
                <Td align="right">{formatPrice(p.price)}</Td>
                <Td align="right"><StockBadge product={p} /></Td>
                <Td align="right">{p.soldCount}</Td>
                <Td>{p.active
                  ? <span className="rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">Active</span>
                  : <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">Hidden</span>}</Td>
                <Td><Link to={`/admin/products/${p.id}/edit`} className="font-medium text-brand-700 hover:underline">Edit</Link></Td>
              </tr>
            ))}
          </Table>
          <Pagination page={page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />
        </>
      )}
    </>
  );
}
