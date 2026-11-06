import { render, screen } from '@testing-library/react';
import { OrderTimeline } from './OrderTimeline';

describe('OrderTimeline', () => {
  it('shows all four steps for an active order', () => {
    render(<OrderTimeline order={{ status: 'CONFIRMED', placedAt: '2026-11-06T10:00:00Z', confirmedAt: '2026-11-06T10:05:00Z' }} />);
    ['Order placed', 'Payment confirmed', 'Shipped', 'Delivered'].forEach((label) =>
      expect(screen.getByText(label)).toBeInTheDocument());
  });

  it('ends with "Cancelled" for a cancelled order', () => {
    render(<OrderTimeline order={{ status: 'CANCELLED', placedAt: '2026-11-06T10:00:00Z', cancelledAt: '2026-11-06T10:40:00Z' }} />);
    expect(screen.getByText('Cancelled')).toBeInTheDocument();
    expect(screen.queryByText('Shipped')).not.toBeInTheDocument();
  });
});
