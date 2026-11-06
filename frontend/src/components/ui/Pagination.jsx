import { ChevronLeft, ChevronRight } from 'lucide-react';

/** Works with the backend's PageResponse { page (0-based), totalPages, first, last }. */
export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="mt-8 flex items-center justify-center gap-3">
      <button
        className="inline-flex h-9 items-center gap-1 rounded-lg px-3 text-sm text-gray-700 ring-1 ring-gray-300 hover:bg-gray-50 disabled:opacity-40"
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
      >
        <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Previous
      </button>
      <span className="text-sm text-gray-600">
        Page {page + 1} of {totalPages}
      </span>
      <button
        className="inline-flex h-9 items-center gap-1 rounded-lg px-3 text-sm text-gray-700 ring-1 ring-gray-300 hover:bg-gray-50 disabled:opacity-40"
        onClick={() => onChange(page + 1)}
        disabled={page + 1 >= totalPages}
      >
        Next <ChevronRight className="h-4 w-4" aria-hidden="true" />
      </button>
    </nav>
  );
}
