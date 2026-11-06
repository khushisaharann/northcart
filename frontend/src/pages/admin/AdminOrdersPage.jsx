import { Link, useSearchParams } from 'react-router';
import { ShoppingBag } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { SearchInput } from '../../components/admin/SearchInput';
import { Table, Td } from '../../components/admin/Table';
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatPrice } from '../../utils/format';

const TABS = [
  ['', 'All'], ['CONFIRMED', 'To ship'], ['SHIPPED', 'Shipped'], ['DELIVERED', 'Delivered'],
  ['PLACED', 'Awaiting payment'], ['CANCELLED', 'Cancelled'],
];

export default function AdminOrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const { data, loading, error, reload } = useAsync(
    () => adminApi.orders({ status: status || undefined, q: q || undefined, page, size: 20 }), [status, q, page]);

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  return (
    <>
      <PageHeader title="Orders" />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-1 rounded-lg bg-gray-100 p-1">
          {TABS.map(([value, label]) => (
            <button key={label} role="tab" aria-selected={status === value} onClick={() => update({ status: value })}
                    className={`rounded-md px-3 py-1.5 text-sm font-medium ${status === value ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}>
              {label}
            </button>
          ))}
        </div>
        <SearchInput value={q} onChange={(v) => update({ q: v })} placeholder="Order no., name or email" label="Search orders" />
      </div>
      {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : data.content.length === 0 ? (
        <EmptyState icon={ShoppingBag} title="No orders found" />
      ) : (
        <>
          <Table head={['Order', 'Customer', 'Status', { label: 'Items', align: 'right' }, { label: 'Total', align: 'right' }, 'Placed']} caption="Orders">
            {data.content.map((o) => (
              <tr key={o.orderNumber} className="hover:bg-gray-50">
                <Td><Link to={`/admin/orders/${o.orderNumber}`} className="font-medium text-brand-700 hover:underline">{o.orderNumber}</Link></Td>
                <Td><p className="text-gray-900">{o.customerName}</p><p className="text-xs text-gray-500">{o.customerEmail}</p></Td>
                <Td><OrderStatusBadge status={o.status} /></Td>
                <Td align="right">{o.itemCount}</Td>
                <Td align="right">{formatPrice(o.total)}</Td>
                <Td className="text-gray-500">{formatDateTime(o.placedAt)}</Td>
              </tr>
            ))}
          </Table>
          <Pagination page={page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />
        </>
      )}
    </>
  );
}
