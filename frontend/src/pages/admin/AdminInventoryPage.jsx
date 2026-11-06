import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import toast from 'react-hot-toast';
import { Boxes } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { SearchInput } from '../../components/admin/SearchInput';
import { Table, Td } from '../../components/admin/Table';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage } from '../../utils/errors';
import { StockBadge } from './AdminProductsPage';

/**
 * Stock changes are RELATIVE (+50 received, -2 damaged), never "set to 40":
 * a customer checking out at the same moment can't be overwritten.
 */
function AdjustStock({ product, onAdjusted }) {
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const value = Number(delta);
  const valid = Number.isInteger(value) && value !== 0 && product.availableQuantity + value >= 0;

  const apply = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const stock = await adminApi.adjustStock(product.id, value, reason.trim() || null);
      onAdjusted(stock);
      toast.success(`${product.name}: ${value > 0 ? '+' : ''}${value} → ${stock.availableQuantity} in stock`);
      setDelta('');
      setReason('');
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={apply} className="flex items-center gap-2">
      <input type="number" step="1" value={delta} onChange={(e) => setDelta(e.target.value)} placeholder="+/-"
             aria-label={`Stock change for ${product.name}`}
             className="h-9 w-20 rounded-lg border border-gray-300 px-2 text-sm tabular-nums outline-none focus:border-brand-500" />
      <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason (optional)" maxLength={200}
             aria-label={`Reason for ${product.name}`}
             className="hidden h-9 w-44 rounded-lg border border-gray-300 px-2 text-sm outline-none focus:border-brand-500 md:block" />
      <Button type="submit" size="sm" variant="secondary" disabled={!valid} loading={saving}>Apply</Button>
    </form>
  );
}

export default function AdminInventoryPage() {
  const [params, setParams] = useSearchParams();
  const lowOnly = params.get('all') !== '1';
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const { data, loading, error, reload, setData } = useAsync(
    () => adminApi.products({ lowStock: lowOnly || undefined, q: q || undefined, page, size: 20 }), [lowOnly, q, page]);

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  const applyStock = (stock) => setData({
    ...data,
    content: data.content.map((p) => (p.id === stock.productId
      ? { ...p, availableQuantity: stock.availableQuantity, lowStock: stock.lowStock } : p)),
  });

  return (
    <>
      <PageHeader title="Inventory" subtitle="Restock or correct stock levels." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="radiogroup" aria-label="Show" className="inline-flex rounded-lg bg-gray-100 p-1">
          {[['Low stock', true], ['All products', false]].map(([label, low]) => (
            <button key={label} role="radio" aria-checked={lowOnly === low} onClick={() => update({ all: low ? null : '1' })}
                    className={`rounded-md px-3 py-1.5 text-sm font-medium ${lowOnly === low ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600'}`}>
              {label}
            </button>
          ))}
        </div>
        <SearchInput value={q} onChange={(v) => update({ q: v })} placeholder="Name or SKU" label="Search inventory" />
      </div>
      {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : data.content.length === 0 ? (
        <EmptyState icon={Boxes} title={lowOnly ? 'Nothing is running low' : 'No products found'}
                    description={lowOnly ? 'Every product is above its low-stock threshold.' : undefined} />
      ) : (
        <>
          <Table head={['Product', 'SKU', { label: 'In stock', align: 'right' }, { label: 'Alert at', align: 'right' }, 'Adjust']} caption="Inventory">
            {data.content.map((p) => (
              <tr key={p.id}>
                <Td><Link to={`/admin/products/${p.id}/edit`} className="font-medium text-gray-900 hover:text-brand-700">{p.name}</Link></Td>
                <Td className="text-gray-500">{p.sku}</Td>
                <Td align="right"><StockBadge product={p} /></Td>
                <Td align="right">{p.lowStockThreshold}</Td>
                <Td><AdjustStock product={p} onAdjusted={applyStock} /></Td>
              </tr>
            ))}
          </Table>
          <Pagination page={page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />
        </>
      )}
    </>
  );
}
