import { Navigate } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import LoginPage from '../customer/LoginPage';

/** /admin/login: the same login form, framed for staff. Admins are sent to /admin afterwards. */
export default function AdminLoginPage() {
  const { isAdmin, isAuthenticated } = useAuth();
  if (isAdmin) return <Navigate to="/admin" replace />;
  return (
    <div className="min-h-screen bg-gray-50 px-4 py-16">
      <LoginPage variant="admin" />
      {isAuthenticated && (
        <p className="mt-4 text-center text-sm text-amber-700">You are logged in as a customer. Log in with an admin account.</p>
      )}
    </div>
  );
}
