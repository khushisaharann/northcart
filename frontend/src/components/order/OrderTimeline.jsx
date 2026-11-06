import { Check, X } from 'lucide-react';
import { formatDateTime } from '../../utils/format';

/** Tracking timeline built from the order's status timestamps. */
export function OrderTimeline({ order }) {
  if (order.status === 'CANCELLED') {
    return (
      <ol className="space-y-4">
        <Step done label="Order placed" at={order.placedAt} />
        {order.confirmedAt && <Step done label="Payment confirmed" at={order.confirmedAt} />}
        <Step cancelled label="Cancelled" at={order.cancelledAt} last />
      </ol>
    );
  }
  const steps = [
    { label: 'Order placed', at: order.placedAt },
    { label: 'Payment confirmed', at: order.confirmedAt },
    { label: 'Shipped', at: order.shippedAt },
    { label: 'Delivered', at: order.deliveredAt },
  ];
  return (
    <ol className="space-y-4">
      {steps.map((s, i) => (
        <Step key={s.label} done={Boolean(s.at)} label={s.label} at={s.at} last={i === steps.length - 1} />
      ))}
    </ol>
  );
}

function Step({ done, cancelled, label, at, last }) {
  return (
    <li className="relative flex gap-3">
      {!last && <span className={`absolute left-[11px] top-6 h-full w-0.5 ${done ? 'bg-brand-600' : 'bg-gray-200'}`} aria-hidden="true" />}
      <span className={`relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${
        cancelled ? 'bg-red-100 text-red-600' : done ? 'bg-brand-600 text-white' : 'border-2 border-gray-300 bg-white'}`}>
        {cancelled ? <X className="h-3.5 w-3.5" /> : done ? <Check className="h-3.5 w-3.5" /> : null}
      </span>
      <div>
        <p className={`text-sm font-medium ${done || cancelled ? 'text-gray-900' : 'text-gray-400'}`}>{label}</p>
        {at && <p className="text-xs text-gray-500">{formatDateTime(at)}</p>}
      </div>
    </li>
  );
}
