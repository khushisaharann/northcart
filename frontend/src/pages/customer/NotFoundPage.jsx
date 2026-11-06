import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <div className="py-20 text-center">
      <p className="text-sm font-semibold text-brand-700">404</p>
      <h1 className="mt-2 text-3xl font-bold text-gray-900">Page not found</h1>
      <p className="mt-2 text-gray-500">The page you are looking for doesn't exist.</p>
      <Link to="/" className="mt-6 inline-block text-sm font-medium text-brand-700 hover:underline">Go to the home page</Link>
    </div>
  );
}
