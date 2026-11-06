/** Consistent admin table styling. Horizontal scroll on small screens instead of squashing columns. */
export function Table({ head, children, caption }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead className="bg-gray-50">
          <tr>
            {head.map((h) => (
              <th key={h.label ?? h} scope="col"
                  className={`whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 ${h.align === 'right' ? 'text-right' : ''}`}>
                {h.label ?? h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">{children}</tbody>
      </table>
    </div>
  );
}

export function Td({ children, align, className = '' }) {
  return <td className={`whitespace-nowrap px-4 py-3 ${align === 'right' ? 'text-right tabular-nums' : ''} ${className}`}>{children}</td>;
}
