/** White panel. `padded={false}` for content that brings its own padding (lists, tables). */
export function Card({ className = '', padded = true, children }) {
  return <div className={`rounded-xl border border-gray-200 bg-white shadow-sm ${padded ? 'p-5' : ''} ${className}`}>{children}</div>;
}
