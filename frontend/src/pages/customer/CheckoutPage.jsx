import { useEffect, useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router';
import toast from 'react-hot-toast';
import { Lock, Plus } from 'lucide-react';
import { orderApi, userApi } from '../../api/endpoints';
import { AddressForm, AddressText } from '../../components/address/AddressForm';
import { FakePaymentDialog } from '../../components/order/FakePaymentDialog';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { useAuth } from '../../hooks/useAuth';
import { useCart } from '../../hooks/useCart';
import { usePayment } from '../../hooks/usePayment';
import { getErrorCode, getErrorMessage } from '../../utils/errors';
import { formatPrice } from '../../utils/format';

/**
 * 1. choose (or add) an address   2. place the order (stock reserved)   3. pay
 * One Idempotency-Key per visit to this page: a double click or a retry after a network
 * error returns the SAME order instead of creating a second one.
 */
export default function CheckoutPage() {
  const { user } = useAuth();
  const { cart, loading: cartLoading, clearLocal, refresh } = useCart();
  const navigate = useNavigate();
  const profile = useAsync(() => userApi.me(), []);
  const [addressId, setAddressId] = useState(null);
  const [addingAddress, setAddingAddress] = useState(false);
  const [placing, setPlacing] = useState(false);
  const idempotencyKey = useRef(crypto.randomUUID());
  const { pay, fakeCheckout } = usePayment();

  const addresses = profile.data?.addresses ?? [];
  useEffect(() => {
    if (!addressId && addresses.length) setAddressId(addresses[0].id); // default address comes first
    if (profile.data && addresses.length === 0) setAddingAddress(true);
  }, [profile.data]); // eslint-disable-line react-hooks/exhaustive-deps

  if (cartLoading || profile.loading) return <Spinner />;
  if (profile.error) return <ErrorState error={profile.error} onRetry={profile.reload} />;
  if (!placing && cart.items.length === 0) return <Navigate to="/cart" replace />;

  const placeOrder = async () => {
    setPlacing(true);
    let order;
    try {
      order = await orderApi.checkout(addressId, idempotencyKey.current);
      clearLocal(); // the server emptied the cart
    } catch (err) {
      toast.error(getErrorMessage(err));
      if (['INSUFFICIENT_STOCK', 'INVALID_STATE'].includes(getErrorCode(err))) {
        await refresh();
        navigate('/cart');
      }
      setPlacing(false);
      return;
    }

    try {
      const result = await pay(order.orderNumber);
      navigate(`/payment/result?order=${order.orderNumber}&status=${result.status}`, { replace: true, state: result });
    } catch (err) {
      navigate(`/payment/result?order=${order.orderNumber}&status=error`, {
        replace: true, state: { message: getErrorMessage(err), code: getErrorCode(err) },
      });
    }
  };

  return (
    <>
      <PageHeader title="Checkout" />
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Delivery address</h2>
            {!addingAddress && (
              <Button variant="ghost" size="sm" onClick={() => setAddingAddress(true)}>
                <Plus className="h-4 w-4" aria-hidden="true" /> New address
              </Button>
            )}
          </div>

          {addingAddress ? (
            <div className="mt-4">
              <AddressForm defaultName={user.fullName}
                           onCancel={addresses.length ? () => setAddingAddress(false) : undefined}
                           onSaved={(saved) => {
                             profile.setData({ ...profile.data, addresses: [saved, ...addresses] });
                             setAddressId(saved.id);
                             setAddingAddress(false);
                           }} />
            </div>
          ) : (
            <fieldset className="mt-4 space-y-3">
              <legend className="sr-only">Choose a delivery address</legend>
              {addresses.map((a) => (
                <label key={a.id} className={`flex cursor-pointer gap-3 rounded-lg border p-4 ${
                  addressId === a.id ? 'border-brand-500 bg-brand-50/40 ring-1 ring-brand-500' : 'border-gray-200 hover:border-gray-300'}`}>
                  <input type="radio" name="address" className="mt-1" checked={addressId === a.id} onChange={() => setAddressId(a.id)} />
                  <AddressText address={a} />
                </label>
              ))}
            </fieldset>
          )}
        </Card>

        <Card className="h-fit lg:sticky lg:top-24">
          <h2 className="text-lg font-semibold text-gray-900">Your order</h2>
          <ul className="mt-4 space-y-2 text-sm">
            {cart.items.map((item) => (
              <li key={item.itemId} className="flex justify-between gap-3">
                <span className="text-gray-600">{item.name} × {item.quantity}</span>
                <span>{formatPrice(item.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 space-y-2 border-t border-gray-200 pt-4 text-sm">
            <div className="flex justify-between"><dt className="text-gray-600">Subtotal</dt><dd>{formatPrice(cart.subtotal)}</dd></div>
            <div className="flex justify-between">
              <dt className="text-gray-600">Shipping</dt>
              <dd>{Number(cart.shippingFee) === 0 ? 'Free' : formatPrice(cart.shippingFee)}</dd>
            </div>
            <div className="flex justify-between pt-2 text-base font-semibold"><dt>Total</dt><dd>{formatPrice(cart.total)}</dd></div>
          </dl>
          <Button size="lg" className="mt-5 w-full" onClick={placeOrder} loading={placing}
                  disabled={!addressId || addingAddress || !cart.checkoutReady}>
            <Lock className="h-4 w-4" aria-hidden="true" /> Place order & pay
          </Button>
          <p className="mt-2 text-center text-xs text-gray-500">Your items are reserved for 30 minutes while you pay.</p>
        </Card>
      </div>
      <FakePaymentDialog checkout={fakeCheckout} />
    </>
  );
}
