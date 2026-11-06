import { useCallback, useEffect, useState } from 'react';

/**
 * Loads data when the dependencies change and exposes { data, loading, error, reload }.
 * Ignores responses that arrive after the component changed its inputs (no stale
 * results flashing in when the user types quickly or clicks through pages).
 */
export function useAsync(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then((data) => active && setState({ data, loading: false, error: null }))
      .catch((error) => active && setState({ data: null, loading: false, error }));
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { ...state, reload, setData: (data) => setState((s) => ({ ...s, data })) };
}
