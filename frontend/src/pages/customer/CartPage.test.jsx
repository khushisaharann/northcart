import { screen } from '@testing-library/react';
import { customer, renderWithProviders } from '../../test/renderWithProviders';
import CartPage from './CartPage';

const line = (overrides) => ({
  itemId: 1, productId: 1, name: 'USB-C Hub', slug: 'usb-c-hub', thumbnailUrl: null,
  unitPrice: 2199, quantity: 3, lineTotal: 6597, availableQuantity: 10, issue: null, ...overrides,
});

const cartOf = (items, overrides = {}) => ({
  items, totalQuantity: items.reduce((n, i) => n + i.quantity, 0), subtotal: 6597, shippingFee: 0, total: 6597,
  checkoutReady: true, ...overrides,
});

describe('CartPage', () => {
  const auth = { isAuthenticated: true, isCustomer: true, user: customer };

  it('shows an empty state', () => {
    renderWithProviders(<CartPage />, { auth });
    expect(screen.getByText('Your cart is empty')).toBeInTheDocument();
  });

  it('shows totals and enables checkout when everything is available', () => {
    renderWithProviders(<CartPage />, { auth, cart: { cart: cartOf([line()]) } });
    expect(screen.getAllByText('₹6,597.00').length).toBeGreaterThan(0);
    expect(screen.getByText('Free')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Proceed to checkout' })).toBeEnabled();
  });

  it('explains stock problems and blocks checkout', () => {
    const cart = cartOf([line({ issue: 'INSUFFICIENT_STOCK', availableQuantity: 2 })], { checkoutReady: false });
    renderWithProviders(<CartPage />, { auth, cart: { cart } });
    expect(screen.getByText(/Only 2 left/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Proceed to checkout' })).toBeDisabled();
  });

  it('nudges towards free shipping', () => {
    const cart = cartOf([line({ quantity: 1, lineTotal: 300, unitPrice: 300 })], { subtotal: 300, shippingFee: 49, total: 349 });
    renderWithProviders(<CartPage />, { auth, cart: { cart } });
    expect(screen.getByText(/Add ₹699.00 more for free shipping/)).toBeInTheDocument();
  });
});
