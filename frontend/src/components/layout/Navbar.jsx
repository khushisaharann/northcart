import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate, useSearchParams } from 'react-router';
import { LayoutDashboard, LogOut, Package, Search, ShoppingCart, Store, User, X } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useCart } from '../../hooks/useCart';

function SearchBar({ onSearch }) {
  const [params] = useSearchParams();
  const [value, setValue] = useState(params.get('q') ?? '');
  useEffect(() => {
    setValue(params.get('q') ?? '');
  }, [params]);

  return (
    <form
      role="search"
      className="relative w-full"
      onSubmit={(e) => {
        e.preventDefault();
        onSearch(value.trim());
      }}
    >
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search products"
        aria-label="Search products"
        className="h-10 w-full rounded-lg border border-gray-300 bg-gray-50 pl-9 pr-3 text-sm outline-none focus:border-brand-500 focus:bg-white focus:ring-2 focus:ring-brand-100"
      />
    </form>
  );
}

function UserMenu({ user, isAdmin, onLogout }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu"
              className="flex h-10 items-center gap-2 rounded-lg px-3 text-sm font-medium text-gray-700 hover:bg-gray-100">
        <User className="h-5 w-5" aria-hidden="true" />
        <span className="hidden max-w-32 truncate md:inline">{user.fullName.split(' ')[0]}</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-48 rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
          {isAdmin && (
            <Link role="menuitem" to="/admin" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-brand-700 hover:bg-gray-50">
              <LayoutDashboard className="h-4 w-4" /> Admin dashboard
            </Link>
          )}
          <Link role="menuitem" to="/account" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50">
            <User className="h-4 w-4" /> My account
          </Link>
          {!isAdmin && (
            <Link role="menuitem" to="/orders" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50">
              <Package className="h-4 w-4" /> My orders
            </Link>
          )}
          <button role="menuitem" onClick={onLogout} className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50">
            <LogOut className="h-4 w-4" /> Log out
          </button>
        </div>
      )}
    </div>
  );
}

export function Navbar() {
  const { user, isCustomer, isAdmin, logout } = useAuth();
  const { itemCount } = useCart();
  const navigate = useNavigate();
  const [mobileSearch, setMobileSearch] = useState(false);

  const search = (q) => {
    setMobileSearch(false);
    navigate(q ? `/products?q=${encodeURIComponent(q)}` : '/products');
  };
  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="sticky top-0 z-30 border-b border-gray-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Link to="/" className="flex shrink-0 items-center gap-2 text-lg font-bold tracking-tight text-gray-900">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-700 text-sm text-white">N</span>
          NorthCart
        </Link>
        <NavLink to="/products" className={({ isActive }) =>
          `hidden text-sm font-medium sm:block ${isActive ? 'text-brand-700' : 'text-gray-600 hover:text-gray-900'}`}>
          Shop
        </NavLink>

        <div className="mx-auto hidden w-full max-w-md md:block">
          <SearchBar onSearch={search} />
        </div>

        <div className="ml-auto flex items-center gap-1 md:ml-0">
          <button className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100 md:hidden"
                  onClick={() => setMobileSearch((s) => !s)} aria-label="Search">
            {mobileSearch ? <X className="h-5 w-5" /> : <Search className="h-5 w-5" />}
          </button>
          {(!user || isCustomer) && (
            <Link to="/cart" className="relative flex h-10 w-10 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100"
                  aria-label={`Cart, ${itemCount} items`}>
              <ShoppingCart className="h-5 w-5" aria-hidden="true" />
              {itemCount > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1 text-[11px] font-semibold text-white">
                  {itemCount > 99 ? '99+' : itemCount}
                </span>
              )}
            </Link>
          )}
          {user ? (
            <UserMenu user={user} isAdmin={isAdmin} onLogout={handleLogout} />
          ) : (
            <>
              <Link to="/login" className="hidden h-10 items-center rounded-lg px-3 text-sm font-medium text-gray-700 hover:bg-gray-100 sm:flex">
                Log in
              </Link>
              <Link to="/register" className="flex h-10 items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700">
                Sign up
              </Link>
            </>
          )}
          <Link to="/products" className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100 sm:hidden" aria-label="Shop">
            <Store className="h-5 w-5" />
          </Link>
        </div>
      </div>
      {mobileSearch && (
        <div className="border-t border-gray-100 px-4 py-3 md:hidden">
          <SearchBar onSearch={search} />
        </div>
      )}
    </header>
  );
}
