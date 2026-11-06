import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../context/AuthContext';
import { CartContext } from '../context/CartContext';

/**
 * Test helper: render a component with fake auth/cart state and an in-memory router.
 * Pass ui = null and `extraRoutes` to test routing itself.
 */
export function renderWithProviders(ui, { auth = {}, cart = {}, route = '/', path = '*', extraRoutes = null } = {}) {
  const authValue = {
    user: null, initializing: false, isAuthenticated: false, isAdmin: false, isCustomer: false,
    login: async () => {}, register: async () => {}, logout: () => {}, setUser: () => {},
    ...auth,
  };
  const cartValue = {
    cart: { items: [], totalQuantity: 0, subtotal: 0, shippingFee: 0, total: 0, checkoutReady: false },
    loading: false, itemCount: 0,
    refresh: async () => {}, addItem: async () => {}, updateItem: async () => {}, removeItem: async () => {}, clearLocal: () => {},
    ...cart,
  };
  return render(
    <AuthContext.Provider value={authValue}>
      <CartContext.Provider value={cartValue}>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            {ui && <Route path={path} element={ui} />}
            {extraRoutes}
          </Routes>
        </MemoryRouter>
      </CartContext.Provider>
    </AuthContext.Provider>,
  );
}

export const customer = { id: 1, email: 'khushi@test.com', fullName: 'Khushi Kumar', roles: ['CUSTOMER'] };
