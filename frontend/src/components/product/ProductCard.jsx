import { Link } from 'react-router';
import { formatPrice } from '../../utils/format';
import { ProductImage } from '../ui/ProductImage';

export function ProductCard({ product }) {
  return (
    <Link to={`/products/${product.slug}`}
          className="group flex flex-col overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="relative aspect-square overflow-hidden bg-gray-100">
        <ProductImage src={product.thumbnailUrl} alt={product.name}
                      className="h-full w-full transition duration-300 group-hover:scale-105" />
        {!product.inStock && (
          <span className="absolute left-3 top-3 rounded-full bg-gray-900/80 px-2.5 py-1 text-xs font-medium text-white">
            Out of stock
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col p-4">
        <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{product.category?.name}</p>
        <h3 className="mt-1 line-clamp-2 text-sm font-semibold text-gray-900 group-hover:text-brand-700">{product.name}</h3>
        <p className="mt-auto pt-3 text-base font-bold text-gray-900">{formatPrice(product.price)}</p>
      </div>
    </Link>
  );
}

export function ProductGrid({ products }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
      {products.map((p) => <ProductCard key={p.id} product={p} />)}
    </div>
  );
}

export function ProductGridSkeleton({ count = 8 }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="animate-pulse overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="aspect-square bg-gray-200" />
          <div className="space-y-2 p-4">
            <div className="h-3 w-1/3 rounded bg-gray-200" />
            <div className="h-4 w-3/4 rounded bg-gray-200" />
            <div className="h-5 w-1/4 rounded bg-gray-200" />
          </div>
        </div>
      ))}
    </div>
  );
}
