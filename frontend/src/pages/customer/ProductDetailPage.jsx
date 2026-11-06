import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import toast from 'react-hot-toast';
import { CheckCircle2, ShoppingCart, XCircle } from 'lucide-react';
import { catalogApi } from '../../api/endpoints';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { ProductImage } from '../../components/ui/ProductImage';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { useAuth } from '../../hooks/useAuth';
import { useCart } from '../../hooks/useCart';
import { getErrorMessage } from '../../utils/errors';
import { formatPrice } from '../../utils/format';

const MAX_PER_ORDER = 10; // same limit as the backend cart

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { data: product, loading, error, reload } = useAsync(() => catalogApi.getProduct(slug), [slug]);
  const { isAuthenticated, isAdmin } = useAuth();
  const { addItem } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [imageIndex, setImageIndex] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);

  if (loading) return <Spinner />;
  if (error?.response?.status === 404) {
    return <EmptyState title="Product not found" description="It may have been removed."
                       action={<Link to="/products" className="text-sm font-medium text-brand-700">Browse products</Link>} />;
  }
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const maxQuantity = Math.min(product.availableQuantity, MAX_PER_ORDER);
  const image = product.images[imageIndex] ?? product.images[0];

  const handleAdd = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location } });
      return;
    }
    setAdding(true);
    try {
      await addItem(product.id, quantity);
      toast.success(`Added ${quantity} × ${product.name} to your cart`);
    } catch (err) {
      toast.error(getErrorMessage(err)); // e.g. "Only 3 unit(s) of ... are available"
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="grid gap-10 lg:grid-cols-2">
      <div>
        <div className="aspect-square overflow-hidden rounded-2xl border border-gray-200 bg-white">
          <ProductImage src={image?.url} alt={image?.altText ?? product.name} className="h-full w-full" />
        </div>
        {product.images.length > 1 && (
          <div className="mt-4 flex gap-3">
            {product.images.map((img, i) => (
              <button key={img.url} onClick={() => setImageIndex(i)} aria-label={`Show image ${i + 1}`} aria-pressed={i === imageIndex}
                      className={`h-20 w-20 overflow-hidden rounded-lg border-2 ${i === imageIndex ? 'border-brand-600' : 'border-transparent'}`}>
                <ProductImage src={img.url} alt={img.altText ?? ''} className="h-full w-full" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        <nav aria-label="Breadcrumb" className="text-sm text-gray-500">
          <Link to="/products" className="hover:text-gray-900">Shop</Link> /{' '}
          <Link to={`/products?category=${product.category.slug}`} className="hover:text-gray-900">{product.category.name}</Link>
        </nav>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-gray-900">{product.name}</h1>
        <p className="mt-1 text-xs text-gray-400">SKU {product.sku}</p>
        <p className="mt-4 text-3xl font-bold text-gray-900">{formatPrice(product.price)}</p>
        <p className="text-xs text-gray-500">Inclusive of all taxes</p>

        <div className="mt-4">
          {product.inStock ? (
            <p className="inline-flex items-center gap-1.5 text-sm font-medium text-green-700">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              {product.availableQuantity <= 5 ? `Only ${product.availableQuantity} left` : 'In stock'}
            </p>
          ) : (
            <p className="inline-flex items-center gap-1.5 text-sm font-medium text-red-600">
              <XCircle className="h-4 w-4" aria-hidden="true" /> Out of stock
            </p>
          )}
        </div>

        {!isAdmin && product.inStock && (
          <div className="mt-6 flex flex-wrap items-center gap-4">
            <QuantityStepper value={quantity} max={maxQuantity} onChange={setQuantity} />
            <Button size="lg" onClick={handleAdd} loading={adding} className="min-w-48">
              <ShoppingCart className="h-5 w-5" aria-hidden="true" /> Add to cart
            </Button>
          </div>
        )}

        {product.description && (
          <section className="mt-10 border-t border-gray-200 pt-6">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500">Description</h2>
            <p className="mt-3 whitespace-pre-line leading-relaxed text-gray-700">{product.description}</p>
          </section>
        )}
      </div>
    </div>
  );
}
