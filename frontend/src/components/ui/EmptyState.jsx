/** Friendly "nothing here" block with an optional action (e.g. "Browse products"). */
export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-300 bg-white px-6 py-14 text-center">
      {Icon && <Icon className="mb-3 h-10 w-10 text-gray-400" aria-hidden="true" />}
      <h2 className="text-base font-semibold text-gray-900">{title}</h2>
      {description && <p className="mt-1 max-w-sm text-sm text-gray-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
