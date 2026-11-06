import { Link } from 'react-router';

export function Footer() {
  return (
    <footer className="mt-16 border-t border-gray-200 bg-white">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 text-sm sm:grid-cols-3 sm:px-6">
        <div>
          <p className="font-semibold text-gray-900">NorthCart</p>
          <p className="mt-2 text-gray-500">Everyday essentials, delivered across India. Free shipping on orders above ₹999.</p>
        </div>
        <div>
          <p className="font-semibold text-gray-900">Shop</p>
          <ul className="mt-2 space-y-1 text-gray-500">
            <li><Link to="/products" className="hover:text-gray-900">All products</Link></li>
            <li><Link to="/products?sort=popular" className="hover:text-gray-900">Best sellers</Link></li>
            <li><Link to="/products?sort=newest" className="hover:text-gray-900">New arrivals</Link></li>
          </ul>
        </div>
        <div>
          <p className="font-semibold text-gray-900">Account</p>
          <ul className="mt-2 space-y-1 text-gray-500">
            <li><Link to="/orders" className="hover:text-gray-900">My orders</Link></li>
            <li><Link to="/account" className="hover:text-gray-900">Profile & addresses</Link></li>
            <li><Link to="/cart" className="hover:text-gray-900">Cart</Link></li>
          </ul>
        </div>
      </div>
      <p className="border-t border-gray-100 py-4 text-center text-xs text-gray-400">
        © {new Date().getFullYear()} NorthCart. A portfolio project.
      </p>
    </footer>
  );
}
