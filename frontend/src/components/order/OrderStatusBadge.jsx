import { titleCase } from '../../utils/format';

const STYLES = {
  PLACED: 'bg-amber-50 text-amber-700 ring-amber-200',
  CONFIRMED: 'bg-blue-50 text-blue-700 ring-blue-200',
  SHIPPED: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  DELIVERED: 'bg-green-50 text-green-700 ring-green-200',
  CANCELLED: 'bg-gray-100 text-gray-600 ring-gray-200',
};

/** PLACED is shown as "Awaiting payment": that's what it means to a customer. */
export function OrderStatusBadge({ status }) {
  const label = status === 'PLACED' ? 'Awaiting payment' : titleCase(status);
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[status] ?? STYLES.CANCELLED}`}>
      {label}
    </span>
  );
}
