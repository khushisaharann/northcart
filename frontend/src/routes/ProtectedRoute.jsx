import { Navigate, Outlet, useLocation } from 'react-router';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from '../hooks/useAuth';

/**
 * Guards a group of routes. Not logged in -> /login (and back here afterwards).
 * Wrong role -> home. This is UX only: the backend enforces the same rules on every request.
 */
export function ProtectedRoute({ role, loginPath = '/login' }) {
  const { isAuthenticated, initializing, user } = useAuth();
  const location = useLocation();

  if (initializing) return <Spinner label="Checking your session…" />;
  if (!isAuthenticated) return <Navigate to={loginPath} replace state={{ from: location }} />;
  if (role && !user.roles.includes(role)) return <Navigate to="/" replace />;
  return <Outlet />;
}

/** Login/Register pages: already logged-in users go straight home. */
export function GuestRoute() {
  const { isAuthenticated, initializing } = useAuth();
  if (initializing) return <Spinner />;
  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}
