import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { cartApi } from '../api/endpoints';
import { useAuth } from '../hooks/useAuth';

/**
 * The server is the single source of truth for the cart: every action sends a request
 * and REPLACES local state with the full cart the server returns. The UI never
 * calculates totals or shipping itself.
 */
export const CartContext = createContext(null);

const EMPTY_CART = { items: [], totalQuantity: 0, subtotal: 0, shippingFee: 0, total: 0, checkoutReady: false };

export function CartProvider({ children }) {
  const { isCustomer } = useAuth();
  const [cart, setCart] = useState(EMPTY_CART);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setCart(EMPTY_CART);
      return EMPTY_CART;
    }
    setLoading(true);
    try {
      const fresh = await cartApi.get();
      setCart(fresh);
      return fresh;
    } finally {
      setLoading(false);
    }
  }, [isCustomer]);

  // Load on login, clear on logout.
  useEffect(() => {
    refresh().catch(() => setCart(EMPTY_CART));
  }, [refresh]);

  const run = useCallback(async (request) => {
    const updated = await request;
    setCart(updated);
    return updated;
  }, []);

  const value = useMemo(
    () => ({
      cart,
      loading,
      itemCount: cart.totalQuantity,
      refresh,
      addItem: (productId, quantity = 1) => run(cartApi.addItem(productId, quantity)),
      updateItem: (itemId, quantity) => run(cartApi.updateItem(itemId, quantity)),
      removeItem: (itemId) => run(cartApi.removeItem(itemId)),
      clearLocal: () => setCart(EMPTY_CART), // after checkout the server cart is already empty
    }),
    [cart, loading, refresh, run],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
