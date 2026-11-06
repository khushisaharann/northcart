import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import toast from 'react-hot-toast';
import { AlertCircle, ShoppingBag, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { ProductImage } from '../../components/ui/ProductImage';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { Spinner } from '../../components/ui/Spinner';
import { useCart } from '../../hooks/useCart';
import { getErrorMessage } from '../../utils/errors';
import { formatPrice } from '../../utils/format';

const FREE_SHIPPING_FROM = 999;

function issueText(item) {
  switch (item.issue) {
    case 'UNAVAILABLE': return 'No longer available. Please remove it.';
    case 'OUT_OF_STOCK': return 'Out of stock. Please remove it.';
    case 'INSUFFICIENT_STOCK': return `Only ${item.availableQuantity} left. Reduce the quantity to continue.`;
    default: return null;
  }
}

function CartLine({ item }) {
  const { updateItem, removeItem } = useCart();
  const [busy, setBusy] = useState(false);

  const run = async (action) => {
    setBusy(true);
    try {
      await action();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const problem = issueText(item);
  return (
    <li className={`flex gap-4 py-5 ${busy ? 'opacity-60' : ''}`}>
      <Link to={`/products/${item.slug}`} className="h-24 w-24 shrink-0 overflow-hidden rounded-lg border border-gray-200">
        <ProductImage src={item.thumbnailUrl} alt={item.name} className="h-full w-full" />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex justify-between gap-4">
          <div className="min-w-0">
            <Link to={`/products/${item.slug}`} className="line-clamp-2 font-medium text-gray-900 hover:text-brand-700">{item.name}</Link>
            <p className="mt-0.5 text-sm text-gray-500">{formatPrice(item.unitPrice)} each</p>
          </div>
          <p className="shrink-0 font-semibold text-gray-900">{formatPrice(item.lineTotal)}</p>
        </div>
        {problem && (
          <p className="mt-2 inline-flex items-center gap-1.5 text-sm text-amber-700">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" /> {problem}
          </p>
        )}
        <div className="mt-auto flex items-center justify-between pt-3">
          {item.issue === 'UNAVAILABLE' || item.issue === 'OUT_OF_STOCK' ? <span /> : (
            <QuantityStepper value={item.quantity} max={Math.min(Math.max(item.availableQuantity, item.quantity), 10)}
                             disabled={busy} onChange={(q) => run(() => updateItem(item.itemId, q))}
                             label={`Quantity of ${item.name}`} />
          )}
          <button onClick={() => run(() => removeItem(item.itemId))} disabled={busy}
                  className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-red-600" aria-label={`Remove ${item.name}`}>
            <Trash2 className="h-4 w-4" aria-hidden="true" /> Remove
          </button>
        </div>
      </div>
    </li>
  );
}

export default function CartPage() {
  const { cart, loading } = useCart();
  const navigate = useNavigate();

  if (loading && cart.items.length === 0) return <Spinner label="Loading your cart…" />;
  if (cart.items.length === 0) {
    return (
      <>
        <PageHeader title="Your cart" />
        <EmptyState icon={ShoppingBag} title="Your cart is empty" description="Find something you like and add it here."
                    action={<Button onClick={() => navigate('/products')}>Browse products</Button>} />
      </>
    );
  }

  const remainingForFree = FREE_SHIPPING_FROM - Number(cart.subtotal);
  return (
    <>
      <PageHeader title="Your cart" subtitle={`${cart.totalQuantity} ${cart.totalQuantity === 1 ? 'item' : 'items'}`} />
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card padded={false} className="px-5">
          <ul className="divide-y divide-gray-100">
            {cart.items.map((item) => <CartLine key={item.itemId} item={item} />)}
          </ul>
        </Card>

        <Card className="h-fit lg:sticky lg:top-24">
          <h2 className="text-lg font-semibold text-gray-900">Order summary</h2>
          <dl className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-gray-600">Subtotal</dt><dd>{formatPrice(cart.subtotal)}</dd></div>
            <div className="flex justify-between">
              <dt className="text-gray-600">Shipping</dt>
              <dd>{Number(cart.shippingFee) === 0 ? <span className="text-green-700">Free</span> : formatPrice(cart.shippingFee)}</dd>
            </div>
            <div className="flex justify-between border-t border-gray-200 pt-3 text-base font-semibold">
              <dt>Total</dt><dd>{formatPrice(cart.total)}</dd>
            </div>
          </dl>
          {remainingForFree > 0 && (
            <p className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-800">
              Add {formatPrice(remainingForFree)} more for free shipping.
            </p>
          )}
          <Button size="lg" className="mt-5 w-full" disabled={!cart.checkoutReady} onClick={() => navigate('/checkout')}>
            Proceed to checkout
          </Button>
          {!cart.checkoutReady && (
            <p className="mt-2 text-center text-xs text-amber-700">Fix the highlighted items to continue.</p>
          )}
        </Card>
      </div>
    </>
  );
}
