import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { useDebounce } from '../../hooks/useDebounce';

/** Debounced search box: calls onChange 350ms after the admin stops typing. */
export function SearchInput({ value, onChange, placeholder = 'Search', label = 'Search' }) {
  const [text, setText] = useState(value ?? '');
  const debounced = useDebounce(text, 350);
  useEffect(() => {
    if ((value ?? '') !== debounced) onChange(debounced);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);
  useEffect(() => {
    setText(value ?? '');
  }, [value]);

  return (
    <div className="relative w-full max-w-xs">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
      <input type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} aria-label={label}
             className="h-10 w-full rounded-lg border border-gray-300 bg-white pl-9 pr-3 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" />
    </div>
  );
}
