import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { Boxes, FolderTree, LayoutDashboard, LogOut, Menu, Package, ShoppingBag, Store, Users, X } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/orders', label: 'Orders', icon: ShoppingBag },
  { to: '/admin/products', label: 'Products', icon: Package },
  { to: '/admin/inventory', label: 'Inventory', icon: Boxes },
  { to: '/admin/categories', label: 'Categories', icon: FolderTree },
  { to: '/admin/customers', label: 'Customers', icon: Users },
];

function NavItems({ onNavigate }) {
  return (
    <ul className="space-y-1">
      {NAV.map(({ to, label, icon: Icon, end }) => (
        <li key={to}>
          <NavLink to={to} end={end} onClick={onNavigate}
                   className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium ${
                     isActive ? 'bg-brand-50 text-brand-700' : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'}`}>
            <Icon className="h-4 w-4" aria-hidden="true" /> {label}
          </NavLink>
        </li>
      ))}
    </ul>
  );
}

/** Separate shell for /admin: sidebar navigation instead of the shop's navbar and footer. */
export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    setOpen(false);
    window.scrollTo(0, 0);
  }, [pathname]);

  const handleLogout = () => {
    logout();
    navigate('/admin/login');
  };

  return (
    <div className="min-h-screen bg-gray-50 lg:grid lg:grid-cols-[240px_1fr]">
      <aside className="hidden border-r border-gray-200 bg-white lg:flex lg:flex-col">
        <Link to="/admin" className="flex h-16 items-center gap-2 px-5 font-bold text-gray-900">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-700 text-sm text-white">N</span>
          NorthCart <span className="text-xs font-medium text-gray-400">Admin</span>
        </Link>
        <nav className="flex-1 px-3 py-4" aria-label="Admin"><NavItems /></nav>
        <div className="border-t border-gray-200 p-3 text-sm">
          <p className="truncate px-3 text-gray-500">{user?.email}</p>
          <Link to="/" className="mt-2 flex items-center gap-2 rounded-lg px-3 py-2 text-gray-600 hover:bg-gray-100">
            <Store className="h-4 w-4" aria-hidden="true" /> View store
          </Link>
          <button onClick={handleLogout} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-red-600 hover:bg-red-50">
            <LogOut className="h-4 w-4" aria-hidden="true" /> Log out
          </button>
        </div>
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-gray-200 bg-white px-4 lg:hidden">
          <Link to="/admin" className="font-bold text-gray-900">NorthCart Admin</Link>
          <button onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-label="Menu"
                  className="flex h-10 w-10 items-center justify-center rounded-lg hover:bg-gray-100">
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </header>
        {open && (
          <nav className="border-b border-gray-200 bg-white p-3 lg:hidden" aria-label="Admin">
            <NavItems onNavigate={() => setOpen(false)} />
            <button onClick={handleLogout} className="mt-2 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-red-600">
              <LogOut className="h-4 w-4" /> Log out
            </button>
          </nav>
        )}
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
