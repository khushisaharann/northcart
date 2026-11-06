/**
 * Horizontal bars for comparing a few categories (best sellers, orders per status).
 * One hue, bars grow from a shared left baseline, 12px thick with a 4px rounded end,
 * value printed at the tip in text colour (never in the bar colour). Every row is
 * focusable and has an accessible name, so nothing depends on hovering.
 */
export function BarList({ items, formatValue = String, emptyText = 'No data yet' }) {
  if (!items.length) return <p className="py-6 text-center text-sm text-gray-500">{emptyText}</p>;
  const max = Math.max(...items.map((i) => Number(i.value)), 1);
  return (
    <ul className="space-y-3">
      {items.map((item) => {
        const pct = (Number(item.value) / max) * 100;
        return (
          <li key={item.key} tabIndex={0} aria-label={`${item.label}: ${formatValue(item.value)}${item.detail ? `, ${item.detail}` : ''}`}
              title={`${item.label}: ${formatValue(item.value)}${item.detail ? ` · ${item.detail}` : ''}`}
              className="group rounded-md outline-none focus-visible:ring-2 focus-visible:ring-brand-200">
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="truncate text-gray-700">{item.label}</span>
              {item.detail && <span className="shrink-0 text-xs text-gray-400">{item.detail}</span>}
            </div>
            <div className="mt-1 flex items-center gap-2">
              <div className="h-3 flex-1">
                {Number(item.value) > 0 && (
                  <div className="h-3 rounded-r bg-brand-600 transition group-hover:bg-brand-700 group-focus:bg-brand-700"
                       style={{ width: `max(${pct}%, 3px)` }} />
                )}
              </div>
              <span className="w-20 shrink-0 text-right text-sm font-medium tabular-nums text-gray-900">{formatValue(item.value)}</span>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
