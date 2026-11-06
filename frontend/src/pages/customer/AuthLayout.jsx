import { Link } from 'react-router';

/** Centered card used by login, register and password pages. */
export function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="mx-auto max-w-md py-6">
      <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-gray-500">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <p className="mt-6 text-center text-sm text-gray-600">{footer}</p>}
    </div>
  );
}

export function AuthLink({ to, children }) {
  return <Link to={to} className="font-medium text-brand-700 hover:underline">{children}</Link>;
}
