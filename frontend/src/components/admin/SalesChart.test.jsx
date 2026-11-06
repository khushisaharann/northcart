import { fireEvent, render, screen } from '@testing-library/react';
import { niceMax, SalesChart } from './SalesChart';

describe('niceMax', () => {
  it('rounds the axis maximum up to a clean number', () => {
    expect(niceMax(13420)).toBe(20000); // ticks of 5,000
    expect(niceMax(3750)).toBe(4000);
    expect(niceMax(0)).toBe(1000);
  });
});

describe('SalesChart', () => {
  const data = [
    { date: '2026-11-04', revenue: 0, orders: 0 },
    { date: '2026-11-05', revenue: 3499, orders: 1 },
    { date: '2026-11-06', revenue: 1250.5, orders: 2 },
  ];

  it('is keyboard accessible: arrow keys read out each day', () => {
    render(<SalesChart data={data} />);
    const chart = screen.getByRole('img', { name: /Revenue per day, 4 Nov to 6 Nov/ });

    fireEvent.keyDown(chart, { key: 'ArrowRight' });
    expect(screen.getByRole('status')).toHaveTextContent('₹0.00');

    fireEvent.keyDown(chart, { key: 'ArrowRight' });
    expect(screen.getByRole('status')).toHaveTextContent('₹3,499.00');
    expect(screen.getByRole('status')).toHaveTextContent('5 Nov · 1 order');
  });
});
