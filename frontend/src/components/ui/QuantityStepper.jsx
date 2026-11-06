import { Minus, Plus } from 'lucide-react';

/** − 2 + control, clamped to [min, max]. */
export function QuantityStepper({ value, min = 1, max = 10, onChange, disabled = false, label = 'Quantity' }) {
  const button = 'flex h-9 w-9 items-center justify-center text-gray-600 hover:bg-gray-100 disabled:opacity-40';
  return (
    <div className="inline-flex items-center rounded-lg ring-1 ring-gray-300" role="group" aria-label={label}>
      <button type="button" className={`${button} rounded-l-lg`} onClick={() => onChange(value - 1)}
              disabled={disabled || value <= min} aria-label="Decrease quantity">
        <Minus className="h-4 w-4" />
      </button>
      <span className="w-10 text-center text-sm font-medium tabular-nums" aria-live="polite">{value}</span>
      <button type="button" className={`${button} rounded-r-lg`} onClick={() => onChange(value + 1)}
              disabled={disabled || value >= max} aria-label="Increase quantity">
        <Plus className="h-4 w-4" />
      </button>
    </div>
  );
}
