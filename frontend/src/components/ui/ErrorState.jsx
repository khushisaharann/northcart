import { AlertTriangle } from 'lucide-react';
import { getErrorMessage } from '../../utils/errors';
import { Button } from './Button';

export function ErrorState({ error, onRetry, title = "We couldn't load this" }) {
  return (
    <div role="alert" className="flex flex-col items-center rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center">
      <AlertTriangle className="mb-3 h-8 w-8 text-red-500" aria-hidden="true" />
      <h2 className="text-base font-semibold text-red-900">{title}</h2>
      <p className="mt-1 text-sm text-red-700">{getErrorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
