import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import toast from 'react-hot-toast';
import { ArrowLeft, CreditCard } from 'lucide-react';
import { orderApi } from '../../api/endpoints';
import { FakePaymentDialog } from '../../components/order/FakePaymentDialog';
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge';
import { OrderTimeline } from '../../components/order/OrderTimeline';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Modal } from '../../components/ui/Modal';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { usePayment } from '../../hooks/usePayment';
import { getErrorCode, getErrorMessage } from '../../utils/errors';
import { formatDateTime, formatPrice } from '../../utils/format';

/** Order details + tracking. PLACED orders can be paid from here; PLACED/CONFIRMED can be cancelled. */
export default function OrderDetailPage() {
  const { orderNumber } = useParams();
  const navigate = useNavigate();
  const { data: order, loading, error, reload, setData } = useAsync(() => orderApi.get(orderNumber), [orderNumber]);
  const { pay, processing, fakeCheckout } = usePayment();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  if (loading) return <Spinner />;
  if (error?.response?.status === 404) return <EmptyState title="Order not found" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const payNow = async () => {
    try {
      const result = await pay(order.orderNumber);
      navigate(`/payment/result?order=${order.orderNumber}&status=${result.status}`, { state: result });
    } catch (err) {
      navigate(`/payment/result?order=${order.orderNumber}&status=error`, {
        state: { message: getErrorMessage(err), code: getErrorCode(err) },
      });
    }
  };

  const cancel = async () => {
    setCancelling(true);
    try {
      const updated = await orderApi.cancel(order.orderNumber);
      setData(updated);
      toast.success(updated.confirmedAt ? 'Order cancelled. Your refund is on its way.' : 'Order cancelled.');
    } catch (err) {
      toast.error(getErrorMessage(err));
      reload();
    } finally {
      setCancelling(false);
      setConfirmCancel(false);
    }
  };

  const a = order.shippingAddress;
  return (
    <>
      <Link to="/orders" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-900">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> All orders
      </Link>
      <div className="mt-3 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">{order.orderNumber}</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="mt-1 text-sm text-gray-500">Placed {formatDateTime(order.placedAt)}</p>
        </div>
        <div className="flex gap-2">
          {order.cancellable && <Button variant="danger" onClick={() => setConfirmCancel(true)}>Cancel order</Button>}
          {order.status === 'PLACED' && (
            <Button onClick={payNow} loading={processing}>
              <CreditCard className="h-4 w-4" aria-hidden="true" /> Pay {formatPrice(order.total)}
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-6">
          <Card>
            <h2 className="font-semibold text-gray-900">Items</h2>
            <ul className="mt-3 divide-y divide-gray-100">
              {order.items.map((item) => (
                <li key={item.productId} className="flex justify-between gap-4 py-3 text-sm">
                  <div>
                    <Link to={`/products/${item.productSlug}`} className="font-medium text-gray-900 hover:text-brand-700">{item.productName}</Link>
                    <p className="text-gray-500">{item.quantity} × {formatPrice(item.unitPrice)}</p>
                  </div>
                  <p className="font-medium">{formatPrice(item.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <dl className="mt-3 space-y-1 border-t border-gray-200 pt-3 text-sm">
              <div className="flex justify-between"><dt className="text-gray-600">Subtotal</dt><dd>{formatPrice(order.subtotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-gray-600">Shipping</dt>
                <dd>{Number(order.shippingFee) === 0 ? 'Free' : formatPrice(order.shippingFee)}</dd></div>
              <div className="flex justify-between pt-1 text-base font-semibold"><dt>Total</dt><dd>{formatPrice(order.total)}</dd></div>
            </dl>
          </Card>
          <Card>
            <h2 className="font-semibold text-gray-900">Delivery address</h2>
            <address className="mt-2 text-sm not-italic leading-relaxed text-gray-600">
              <span className="font-medium text-gray-900">{a.fullName}</span> · {a.phone}<br />
              {a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.postalCode}, {a.country}
            </address>
          </Card>
        </div>
        <Card className="h-fit">
          <h2 className="mb-4 font-semibold text-gray-900">Tracking</h2>
          <OrderTimeline order={order} />
        </Card>
      </div>

      <Modal open={confirmCancel} title="Cancel this order?" onClose={() => !cancelling && setConfirmCancel(false)}>
        <p className="text-sm text-gray-600">
          {order.confirmedAt ? 'You have already paid; the full amount will be refunded.' : 'Your reserved items will be released.'}
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmCancel(false)} disabled={cancelling}>Keep order</Button>
          <Button variant="danger" onClick={cancel} loading={cancelling}>Cancel order</Button>
        </div>
      </Modal>
      <FakePaymentDialog checkout={fakeCheckout} />
    </>
  );
}
