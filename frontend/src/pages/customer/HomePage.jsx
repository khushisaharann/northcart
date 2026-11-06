import { Link } from 'react-router';
import { ArrowRight, BadgeCheck, RotateCcw, Truck } from 'lucide-react';
import { catalogApi } from '../../api/endpoints';
import { ProductGrid, ProductGridSkeleton } from '../../components/product/ProductCard';
import { ErrorState } from '../../components/ui/ErrorState';
import { useAsync } from '../../hooks/useAsync';

function Section({ title, link, children }) {
  return (
    <section className="mt-14">
      <div className="mb-5 flex items-end justify-between">
        <h2 className="text-xl font-bold tracking-tight text-gray-900">{title}</h2>
        {link && (
          <Link to={link} className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
            View all <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}

function ProductSection({ title, sort }) {
  const { data, loading, error, reload } = useAsync(() => catalogApi.searchProducts({ sort, size: 4 }), [sort]);
  return (
    <Section title={title} link={`/products?sort=${sort}`}>
      {loading ? <ProductGridSkeleton count={4} /> : error ? <ErrorState error={error} onRetry={reload} /> : <ProductGrid products={data.content} />}
    </Section>
  );
}

export default function HomePage() {
  const categories = useAsync(() => catalogApi.categories(), []);

  return (
    <>
      <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-brand-700 to-brand-500 px-6 py-14 text-white sm:px-12 sm:py-20">
        <p className="text-sm font-medium uppercase tracking-widest text-brand-100">New season</p>
        <h1 className="mt-3 max-w-xl text-3xl font-bold leading-tight sm:text-5xl">Everyday essentials, thoughtfully picked.</h1>
        <p className="mt-4 max-w-lg text-brand-100">Electronics, books, home and fitness gear. Free delivery on orders above ₹999.</p>
        <Link to="/products" className="mt-8 inline-flex h-12 items-center gap-2 rounded-lg bg-white px-6 font-semibold text-brand-700 hover:bg-brand-50">
          Start shopping <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </section>

      <ul className="mt-8 grid gap-4 sm:grid-cols-3">
        {[
          { icon: Truck, title: 'Free shipping', text: 'On every order above ₹999' },
          { icon: BadgeCheck, title: 'Secure payments', text: 'Verified by our server, every time' },
          { icon: RotateCcw, title: 'Easy cancellation', text: 'Cancel before shipping for a full refund' },
        ].map(({ icon: Icon, title, text }) => (
          <li key={title} className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4">
            <Icon className="h-6 w-6 text-brand-600" aria-hidden="true" />
            <div>
              <p className="text-sm font-semibold text-gray-900">{title}</p>
              <p className="text-xs text-gray-500">{text}</p>
            </div>
          </li>
        ))}
      </ul>

      <Section title="Shop by category">
        {categories.loading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {Array.from({ length: 4 }, (_, i) => <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-200" />)}
          </div>
        ) : categories.error ? (
          <ErrorState error={categories.error} onRetry={categories.reload} />
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {categories.data.map((c) => (
              <Link key={c.id} to={`/products?category=${c.slug}`}
                    className="rounded-xl border border-gray-200 bg-white p-5 transition hover:border-brand-200 hover:shadow-sm">
                <p className="font-semibold text-gray-900">{c.name}</p>
                <p className="mt-1 text-xs text-gray-500">{c.productCount} products</p>
              </Link>
            ))}
          </div>
        )}
      </Section>

      <ProductSection title="Best sellers" sort="popular" />
      <ProductSection title="New arrivals" sort="newest" />
    </>
  );
}
