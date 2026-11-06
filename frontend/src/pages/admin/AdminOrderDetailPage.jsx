import { useState } from 'react';
import { Link, useParams } from 'react-router';
import toast from 'react-hot-toast';
import { ArrowLeft, CheckCircle2, Truck, XCircle } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { Table, Td } from '../../components/admin/Table';
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge';
import { OrderTimeline } from '../../components/order/OrderTimeline';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Modal } from '../../components/ui/Modal';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage } from '../../utils/errors';
import { formatDateTime, formatPrice, titleCase } from '../../utils/format';

/** Buttons come from the server's allowedTransitions, so the UI never offers an illegal move. */
export const ACTIONS = {
  SHIPPED: { label: 'Mark as shipped', icon: Truck, variant: 'primary' },
  DELIVERED: { label: 'Mark as delivered', icon: CheckCircle2, variant: 'primary' },
  CANCELLED: { label: 'Cancel order', icon: XCircle, variant: 'danger', confirm: true },
};

export function StatusActions({ allowed, busy, onRun }) {
  return (
    <div className="flex flex-wrap gap-2">
      {allowed.map((status) => {
        const a = ACTIONS[status];
        if (!a) return null;
        const Icon = a.icon;
        return (
          <Button key={status} variant={a.variant} loading={busy === status} disabled={Boolean(busy)} onClick={() => onRun(status)}>
            <Icon className="h-4 w-4" aria-hidden="true" /> {a.label}
          </Button>
        );
      })}
    </div>
  );
}

export default function AdminOrderDetailPage() {
  const { orderNumber } = useParams();
  const { data, loading, error, reload, setData } = useAsync(() => adminApi.order(orderNumber), [orderNumber]);
  const [busy, setBusy] = useState(null);
  const [confirming, setConfirming] = useState(null);

  if (loading) return <Spinner />;
  if (error?.response?.status === 404) return <EmptyState title="Order not found" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const { order, customer, payments, allowedTransitions } = data;
  const paid = payments.some((p) => p.status === 'SUCCESS');

  const run = async (status) => {
    if (ACTIONS[status].confirm && confirming !== status) {
      setConfirming(status);
      return;
    }
    setConfirming(null);
    setBusy(status);
    try {
      setData(await adminApi.setOrderStatus(order.orderNumber, status));
      toast.success(`Order ${titleCase(status).toLowerCase()}. The customer has been emailed.`);
    } catch (err) {
      toast.error(getErrorMessage(err));
      reload();
    } finally {
      setBusy(null);
    }
  };

  const a = order.shippingAddress;
  return (
    <>
      <Link to="/admin/orders" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-900">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Orders
      </Link>
      <div className="mb-6 mt-3 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">{order.orderNumber}</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="mt-1 text-sm text-gray-500">Placed {formatDateTime(order.placedAt)}</p>
        </div>
        <StatusActions allowed={allowedTransitions} busy={busy} onRun={run} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-6">
          <Table head={['Product', 'SKU', { label: 'Qty', align: 'right' }, { label: 'Unit price', align: 'right' }, { label: 'Total', align: 'right' }]} caption="Items">
            {order.items.map((i) => (
              <tr key={i.productId}>
                <Td className="font-medium text-gray-900">{i.productName}</Td>
                <Td className="text-gray-500">{i.sku}</Td>
                <Td align="right">{i.quantity}</Td>
                <Td align="right">{formatPrice(i.unitPrice)}</Td>
                <Td align="right">{formatPrice(i.lineTotal)}</Td>
              </tr>
            ))}
            <tr className="bg-gray-50 text-sm">
              <Td className="text-gray-600">Subtotal {formatPrice(order.subtotal)} · Shipping {Number(order.shippingFee) === 0 ? 'free' : formatPrice(order.shippingFee)}</Td>
              <Td /><Td /><Td align="right" className="font-semibold">Total</Td><Td align="right" className="font-semibold">{formatPrice(order.total)}</Td>
            </tr>
          </Table>

          <section>
            <h2 className="mb-3 font-semibold text-gray-900">Payments</h2>
            {payments.length === 0 ? <Card><p className="text-sm text-gray-500">No payment started yet.</p></Card> : (
              <Table head={['Status', 'Gateway', 'Payment id', { label: 'Amount', align: 'right' }, 'When']} caption="Payments">
                {payments.map((p) => (
                  <tr key={p.providerOrderId}>
                    <Td><span className="font-medium">{titleCase(p.status)}</span>
                      {p.failureReason && <p className="max-w-56 truncate text-xs text-gray-500" title={p.failureReason}>{p.failureReason}</p>}</Td>
                    <Td className="text-gray-500">{titleCase(p.provider)}</Td>
                    <Td className="font-mono text-xs text-gray-600">{p.providerPaymentId ?? p.providerOrderId}</Td>
                    <Td align="right">{formatPrice(p.amount)}</Td>
                    <Td className="text-gray-500">{formatDateTime(p.refundedAt ?? p.paidAt ?? p.createdAt)}</Td>
                  </tr>
                ))}
              </Table>
            )}
          </section>
        </div>

        <div className="space-y-6">
          <Card>
            <h2 className="font-semibold text-gray-900">Customer</h2>
            <p className="mt-2 text-sm font-medium text-gray-900">{customer.fullName}</p>
            <p className="text-sm text-gray-600">{customer.email}</p>
            {customer.phone && <p className="text-sm text-gray-600">{customer.phone}</p>}
            <h3 className="mt-4 text-sm font-semibold text-gray-900">Ship to</h3>
            <address className="mt-1 text-sm not-italic leading-relaxed text-gray-600">
              {a.fullName} · {a.phone}<br />{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.postalCode}, {a.country}
            </address>
          </Card>
          <Card>
            <h2 className="mb-4 font-semibold text-gray-900">Timeline</h2>
            <OrderTimeline order={order} />
          </Card>
        </div>
      </div>

      <Modal open={confirming === 'CANCELLED'} title={`Cancel ${order.orderNumber}?`} onClose={() => setConfirming(null)}>
        <p className="text-sm text-gray-600">
          The stock is returned to inventory and the customer is emailed.
          {paid && ' The payment will be refunded in full.'}
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirming(null)}>Keep order</Button>
          <Button variant="danger" onClick={() => run('CANCELLED')}>Cancel order</Button>
        </div>
      </Modal>
    </>
  );
}
