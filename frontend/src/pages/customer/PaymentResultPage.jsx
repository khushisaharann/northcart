import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import { FakePaymentDialog } from '../../components/order/FakePaymentDialog';
import { Button } from '../../components/ui/Button';
import { usePayment } from '../../hooks/usePayment';
import { getErrorCode, getErrorMessage } from '../../utils/errors';

/** /payment/result?order=NC-...&status=success|failed|cancelled|error */
export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const { state } = useLocation();
  const navigate = useNavigate();
  const orderNumber = params.get('order');
  const status = params.get('status');
  const { pay, processing, fakeCheckout } = usePayment();

  const retry = async () => {
    try {
      const result = await pay(orderNumber);
      navigate(`/payment/result?order=${orderNumber}&status=${result.status}`, { replace: true, state: result });
    } catch (err) {
      navigate(`/payment/result?order=${orderNumber}&status=error`, {
        replace: true, state: { message: getErrorMessage(err), code: getErrorCode(err) },
      });
    }
  };

  const success = status === 'success';
  const late = state?.code === 'PAYMENT_LATE';
  const Icon = success ? CheckCircle2 : status === 'cancelled' ? Clock : XCircle;
  const title = success ? 'Payment successful'
    : late ? 'Order was already cancelled'
      : status === 'cancelled' ? 'Payment not completed' : 'Payment failed';
  const message = success
    ? `Thank you! Order ${orderNumber} is confirmed. A receipt is on its way to your inbox.`
    : state?.message ?? 'Your order is saved and your items stay reserved for 30 minutes. You can try again.';

  return (
    <div className="mx-auto max-w-lg py-10 text-center">
      <Icon className={`mx-auto h-16 w-16 ${success ? 'text-green-600' : status === 'cancelled' ? 'text-amber-500' : 'text-red-500'}`} aria-hidden="true" />
      <h1 className="mt-4 text-2xl font-bold text-gray-900">{title}</h1>
      <p className="mt-2 text-gray-600" role="status">{message}</p>
      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        {!success && !late && <Button onClick={retry} loading={processing}>Try payment again</Button>}
        <Link to={`/orders/${orderNumber}`}
              className="inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium ring-1 ring-gray-300 hover:bg-gray-50">
          View order
        </Link>
        {success && (
          <Link to="/products" className="inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium text-brand-700 hover:bg-brand-50">
            Continue shopping
          </Link>
        )}
      </div>
      <FakePaymentDialog checkout={fakeCheckout} />
    </div>
  );
}
