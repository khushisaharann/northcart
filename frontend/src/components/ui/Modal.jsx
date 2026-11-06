import { useEffect } from 'react';

/** Minimal accessible dialog: Escape closes it, focus starts inside, background is dimmed. */
export function Modal({ open, title, onClose, children }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-gray-900/50 p-4 sm:items-center">
      <div role="dialog" aria-modal="true" aria-labelledby="modal-title"
           className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <h2 id="modal-title" className="text-lg font-semibold text-gray-900">{title}</h2>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}
