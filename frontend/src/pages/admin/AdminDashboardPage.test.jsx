import { screen } from '@testing-library/react';
import { vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import AdminDashboardPage from './AdminDashboardPage';

vi.mock('../../api/endpoints', () => ({
  adminApi: {
    dashboard: vi.fn().mockResolvedValue({
      summary: { revenue: 125000, paidOrders: 40, totalOrders: 52, customers: 31, activeProducts: 12, lowStockProducts: 3, averageOrderValue: 3125 },
      ordersByStatus: { PLACED: 4, CONFIRMED: 6, SHIPPED: 10, DELIVERED: 24, CANCELLED: 8 },
      salesByDay: [{ date: '2026-11-05', revenue: 5000, orders: 2 }, { date: '2026-11-06', revenue: 7500, orders: 3 }],
      topProducts: [{ productId: 1, name: 'Clean Code', slug: 'clean-code', unitsSold: 41, revenue: 26609 }],
      lowStock: [{ productId: 5, name: 'Laptop Stand', sku: 'ELEC-005', availableQuantity: 0, lowStockThreshold: 5 }],
      recentOrders: [{ orderNumber: 'NC-20261106-7F3K9A', customerName: 'Khushi Kumar', status: 'CONFIRMED', total: 4797, placedAt: '2026-11-06T09:00:00Z' }],
    }),
  },
}));

describe('AdminDashboardPage', () => {
  it('shows KPIs, alerts and recent orders from the API', async () => {
    renderWithProviders(<AdminDashboardPage />);
    expect(await screen.findByText('₹1,25,000.00')).toBeInTheDocument();       // revenue (hero)
    expect(screen.getByText(/40 paid orders/)).toBeInTheDocument();
    expect(screen.getByText('Restock soon')).toBeInTheDocument();
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'NC-20261106-7F3K9A' })).toHaveAttribute('href', '/admin/orders/NC-20261106-7F3K9A');
    expect(screen.getByLabelText('Awaiting payment: 4')).toBeInTheDocument();
  });
});
