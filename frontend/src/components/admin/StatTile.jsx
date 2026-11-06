import { Link } from 'react-router';

/**
 * KPI tile: label, value, optional hint. `hero` makes it the one big number of the page.
 * A plain figure, not a chart: a single headline number reads better as text.
 */
export function StatTile({ label, value, hint, hero = false, to, tone = 'default', icon: Icon }) {
  const body = (
    <div className={`h-full rounded-xl border bg-white p-5 shadow-sm ${tone === 'warning' ? 'border-amber-200' : 'border-gray-200'} ${to ? 'transition hover:border-brand-200' : ''}`}>
      <p className="flex items-center gap-1.5 text-sm text-gray-500">
        {Icon && <Icon className={`h-4 w-4 ${tone === 'warning' ? 'text-amber-600' : 'text-gray-400'}`} aria-hidden="true" />}
        {label}
      </p>
      <p className={`mt-2 font-semibold tracking-tight text-gray-900 ${hero ? 'text-4xl sm:text-5xl' : 'text-2xl'}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
    </div>
  );
  return to ? <Link to={to} className="block h-full">{body}</Link> : body;
}
