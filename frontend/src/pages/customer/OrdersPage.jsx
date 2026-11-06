import { Link, useSearchParams } from 'react-router';
import { ChevronRight, Package } from 'lucide-react';
import { orderApi } from '../../api/endpoints';
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { formatDate, formatPrice } from '../../utils/format';

export default function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const { data, loading, error, reload } = useAsync(() => orderApi.list(page, 10), [page]);

  return (
    <>
      <PageHeader title="My orders" />
      {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : data.content.length === 0 ? (
        <EmptyState icon={Package} title="No orders yet" description="When you place an order, it will appear here."
                    action={<Link to="/products"><Button>Start shopping</Button></Link>} />
      ) : (
        <>
          <ul className="space-y-3">
            {data.content.map((o) => (
              <li key={o.orderNumber}>
                <Link to={`/orders/${o.orderNumber}`}
                      className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-brand-200">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-3">
                      <p className="font-semibold text-gray-900">{o.orderNumber}</p>
                      <OrderStatusBadge status={o.status} />
                    </div>
                    <p className="mt-1 truncate text-sm text-gray-500">
                      {o.itemPreview.join(', ')}{o.itemCount > o.itemPreview.length ? '…' : ''}
                    </p>
                    <p className="mt-1 text-xs text-gray-400">
                      {formatDate(o.placedAt)} · {o.itemCount} {o.itemCount === 1 ? 'item' : 'items'}
                    </p>
                  </div>
                  <p className="font-semibold text-gray-900">{formatPrice(o.total)}</p>
                  <ChevronRight className="h-5 w-5 text-gray-400" aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
          <Pagination page={page} totalPages={data.totalPages} onChange={(p) => setParams({ page: String(p) })} />
        </>
      )}
    </>
  );
}
