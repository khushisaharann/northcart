import { useState } from 'react';
import { Link } from 'react-router';
import { AlertTriangle, IndianRupee, Package, ShoppingBag, Users } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { BarList } from '../../components/admin/BarList';
import { SalesChart } from '../../components/admin/SalesChart';
import { StatTile } from '../../components/admin/StatTile';
import { Table, Td } from '../../components/admin/Table';
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatNumber, formatPrice, formatShortDate, titleCase } from '../../utils/format';

const RANGES = [7, 30, 90];
const STATUS_LABEL = { PLACED: 'Awaiting payment' };

function RangeSelect({ days, onChange }) {
  return (
    <div role="radiogroup" aria-label="Date range" className="inline-flex rounded-lg bg-gray-100 p-1">
      {RANGES.map((d) => (
        <button key={d} role="radio" aria-checked={days === d} onClick={() => onChange(d)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ${days === d ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}>
          {d} days
        </button>
      ))}
    </div>
  );
}

function SalesCard({ sales, days }) {
  const [asTable, setAsTable] = useState(false);
  const total = sales.reduce((sum, d) => sum + Number(d.revenue), 0);
  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-gray-900">Revenue per day</h2>
          <p className="text-sm text-gray-500">{formatPrice(total)} in the last {days} days</p>
        </div>
        <button onClick={() => setAsTable((t) => !t)} className="text-sm font-medium text-brand-700 hover:underline">
          {asTable ? 'Show chart' : 'Show as table'}
        </button>
      </div>
      {asTable ? (
        <div className="max-h-72 overflow-y-auto">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-xs uppercase text-gray-500"><th className="py-2">Date</th><th className="text-right">Orders</th><th className="text-right">Revenue</th></tr></thead>
            <tbody className="divide-y divide-gray-100">
              {[...sales].reverse().map((d) => (
                <tr key={d.date}><td className="py-1.5">{formatShortDate(d.date)}</td>
                  <td className="text-right tabular-nums">{d.orders}</td>
                  <td className="text-right tabular-nums">{formatPrice(d.revenue)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <SalesChart data={sales} />
      )}
    </Card>
  );
}

export default function AdminDashboardPage() {
  const [days, setDays] = useState(30);
  const { data, loading, error, reload } = useAsync(() => adminApi.dashboard(days), [days]);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Paid orders only: confirmed, shipped and delivered."
                  actions={<RangeSelect days={days} onChange={setDays} />} />
      {loading && !data ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : (
        <div className={`space-y-6 ${loading ? 'opacity-60' : ''}`}>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
            <div className="sm:col-span-2">
              <StatTile hero icon={IndianRupee} label="Total revenue" value={formatPrice(data.summary.revenue)}
                        hint={`${formatNumber(data.summary.paidOrders)} paid orders · ${formatPrice(data.summary.averageOrderValue)} average`} />
            </div>
            <StatTile icon={ShoppingBag} label="All orders" value={formatNumber(data.summary.totalOrders)} to="/admin/orders" />
            <StatTile icon={Users} label="Customers" value={formatNumber(data.summary.customers)} to="/admin/customers" />
            <StatTile icon={Package} label="Active products" value={formatNumber(data.summary.activeProducts)} to="/admin/products" />
            <StatTile icon={AlertTriangle} label="Low-stock products" value={formatNumber(data.summary.lowStockProducts)}
                      tone={data.summary.lowStockProducts > 0 ? 'warning' : 'default'} to="/admin/inventory"
                      hint={data.summary.lowStockProducts > 0 ? 'Restock soon' : 'All stock levels healthy'} />
          </div>

          <SalesCard sales={data.salesByDay} days={days} />

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Card>
              <h2 className="mb-4 font-semibold text-gray-900">Best sellers <span className="font-normal text-gray-500">· units sold, last {days} days</span></h2>
              <BarList items={data.topProducts.map((p) => ({
                key: p.productId, label: p.name, value: p.unitsSold, detail: formatPrice(p.revenue),
              }))} formatValue={formatNumber} emptyText="No paid orders in this period" />
            </Card>
            <Card>
              <h2 className="mb-4 font-semibold text-gray-900">Orders by status</h2>
              <BarList items={Object.entries(data.ordersByStatus).map(([status, count]) => ({
                key: status, label: STATUS_LABEL[status] ?? titleCase(status), value: count,
              }))} formatValue={formatNumber} />
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-semibold text-gray-900">Recent orders</h2>
                <Link to="/admin/orders" className="text-sm font-medium text-brand-700 hover:underline">All orders</Link>
              </div>
              <Table head={['Order', 'Customer', 'Status', { label: 'Total', align: 'right' }, 'Placed']} caption="Recent orders">
                {data.recentOrders.map((o) => (
                  <tr key={o.orderNumber} className="hover:bg-gray-50">
                    <Td><Link to={`/admin/orders/${o.orderNumber}`} className="font-medium text-brand-700 hover:underline">{o.orderNumber}</Link></Td>
                    <Td>{o.customerName}</Td>
                    <Td><OrderStatusBadge status={o.status} /></Td>
                    <Td align="right">{formatPrice(o.total)}</Td>
                    <Td className="text-gray-500">{formatDateTime(o.placedAt)}</Td>
                  </tr>
                ))}
              </Table>
            </section>
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-semibold text-gray-900">Low stock</h2>
                <Link to="/admin/inventory" className="text-sm font-medium text-brand-700 hover:underline">Manage inventory</Link>
              </div>
              <Card padded={false}>
                {data.lowStock.length === 0 ? <p className="p-5 text-sm text-gray-500">Nothing is running low.</p> : (
                  <ul className="divide-y divide-gray-100">
                    {data.lowStock.map((p) => (
                      <li key={p.productId} className="flex items-center justify-between gap-3 px-5 py-3 text-sm">
                        <div className="min-w-0">
                          <Link to={`/admin/products/${p.productId}/edit`} className="block truncate font-medium text-gray-900 hover:text-brand-700">{p.name}</Link>
                          <p className="text-xs text-gray-500">{p.sku}</p>
                        </div>
                        <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${
                          p.availableQuantity === 0 ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'}`}>
                          <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                          {p.availableQuantity === 0 ? 'Out of stock' : `${p.availableQuantity} left`}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </section>
          </div>
        </div>
      )}
    </>
  );
}
