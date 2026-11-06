import { useState } from 'react';
import { ImageOff } from 'lucide-react';

/** Image with a neutral placeholder when the URL is missing or broken. */
export function ProductImage({ src, alt, className = '' }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) {
    return (
      <div className={`flex items-center justify-center bg-gray-100 text-gray-400 ${className}`} role="img" aria-label={alt}>
        <ImageOff className="h-8 w-8" aria-hidden="true" />
      </div>
    );
  }
  return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />;
}
