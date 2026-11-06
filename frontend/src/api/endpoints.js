import { api } from './client';

/**
 * Every backend call in one place. Components never build URLs themselves, so an API
 * change is fixed here once. Each function returns the response body (data).
 */
const data = (promise) => promise.then((res) => res.data);

export const authApi = {
  register: (body) => data(api.post('/auth/register', body)),
  login: (body) => data(api.post('/auth/login', body)),
  forgotPassword: (email) => data(api.post('/auth/forgot-password', { email })),
  resetPassword: (token, newPassword) => data(api.post('/auth/reset-password', { token, newPassword })),
};

export const userApi = {
  me: () => data(api.get('/users/me')),
  updateProfile: (body) => data(api.put('/users/me', body)),
  addAddress: (body) => data(api.post('/users/me/addresses', body)),
};

export const catalogApi = {
  /** params: { q, category, minPrice, maxPrice, inStock, sort, page, size } */
  searchProducts: (params) => data(api.get('/products', { params })),
  getProduct: (slug) => data(api.get(`/products/${encodeURIComponent(slug)}`)),
  categories: () => data(api.get('/categories')),
};

export const cartApi = {
  get: () => data(api.get('/cart')),
  addItem: (productId, quantity) => data(api.post('/cart/items', { productId, quantity })),
  updateItem: (itemId, quantity) => data(api.patch(`/cart/items/${itemId}`, { quantity })),
  removeItem: (itemId) => data(api.delete(`/cart/items/${itemId}`)),
};

export const orderApi = {
  checkout: (addressId, idempotencyKey) =>
    data(api.post('/orders', { addressId }, { headers: { 'Idempotency-Key': idempotencyKey } })),
  list: (page = 0, size = 10) => data(api.get('/orders', { params: { page, size } })),
  get: (orderNumber) => data(api.get(`/orders/${encodeURIComponent(orderNumber)}`)),
  cancel: (orderNumber) => data(api.patch(`/orders/${encodeURIComponent(orderNumber)}/cancel`)),
};

export const paymentApi = {
  initiate: (orderNumber) => data(api.post(`/payments/${encodeURIComponent(orderNumber)}/initiate`)),
  verify: (body) => data(api.post('/payments/verify', body)),
  /** Fake gateway only (local development without Razorpay keys). */
  fakePay: (providerOrderId) => data(api.post('/payments/fake/pay', { providerOrderId })),
};

/** Admin endpoints (ROLE_ADMIN only; the backend enforces it). */
export const adminApi = {
  dashboard: (days = 30) => data(api.get('/admin/dashboard', { params: { days } })),

  products: (params) => data(api.get('/admin/products', { params })),
  product: (id) => data(api.get(`/admin/products/${id}`)),
  createProduct: (body) => data(api.post('/admin/products', body)),
  updateProduct: (id, body) => data(api.put(`/admin/products/${id}`, body)),
  adjustStock: (productId, delta, reason) => data(api.patch(`/admin/inventory/${productId}`, { delta, reason })),

  createCategory: (body) => data(api.post('/admin/categories', body)),

  orders: (params) => data(api.get('/admin/orders', { params })),
  order: (orderNumber) => data(api.get(`/admin/orders/${encodeURIComponent(orderNumber)}`)),
  setOrderStatus: (orderNumber, status) =>
    data(api.patch(`/admin/orders/${encodeURIComponent(orderNumber)}/status`, { status })),

  customers: (params) => data(api.get('/admin/customers', { params })),
  setCustomerEnabled: (id, enabled) => data(api.patch(`/admin/customers/${id}/status`, { enabled })),
};
