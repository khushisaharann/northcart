import { useState } from 'react';
import { FlaskConical } from 'lucide-react';
import { paymentApi } from '../../api/endpoints';
import { formatPrice } from '../../utils/format';
import { getErrorMessage } from '../../utils/errors';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

/**
 * Shown instead of the Razorpay popup when the backend runs the FAKE gateway
 * (local development without Razorpay keys). "Pay" asks the backend to sign a test
 * payment, exactly like Razorpay would; verification then runs the normal path.
 */
export function FakePaymentDialog({ checkout }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  if (!checkout) return null;
  const { init, resolve, reject } = checkout;

  const pay = async () => {
    setBusy(true);
    setError(null);
    try {
      resolve(await paymentApi.fakePay(init.providerOrderId));
    } catch (e) {
      setError(getErrorMessage(e));
      setBusy(false);
    }
  };

  return (
    <Modal open title="Test payment" onClose={() => !busy && reject({ cancelled: true })}>
      <div className="flex gap-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
        <FlaskConical className="h-5 w-5 shrink-0" aria-hidden="true" />
        <p>The server uses the built-in test gateway. No real money is involved.</p>
      </div>
      <dl className="mt-4 space-y-1 text-sm">
        <div className="flex justify-between"><dt className="text-gray-500">Order</dt><dd className="font-medium">{init.orderNumber}</dd></div>
        <div className="flex justify-between"><dt className="text-gray-500">Amount</dt><dd className="font-semibold">{formatPrice(init.amount / 100)}</dd></div>
      </dl>
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" disabled={busy} onClick={() => reject({ cancelled: false, message: 'Test payment declined' })}>
          Simulate failure
        </Button>
        <Button loading={busy} onClick={pay}>Pay {formatPrice(init.amount / 100)}</Button>
      </div>
    </Modal>
  );
}
