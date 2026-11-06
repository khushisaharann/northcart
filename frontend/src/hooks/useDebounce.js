import { useEffect, useState } from 'react';

/** Returns `value` only after it stopped changing for `delay` ms (e.g. price inputs). */
export function useDebounce(value, delay = 400) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}
