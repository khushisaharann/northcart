import { render, screen } from '@testing-library/react';
import { BarList } from './BarList';

describe('BarList', () => {
  it('labels every bar for screen readers and prints the value as text', () => {
    render(<BarList items={[{ key: 1, label: 'Clean Code', value: 41, detail: '₹26,609.00' }, { key: 2, label: 'Yoga Mat', value: 0 }]} />);
    expect(screen.getByLabelText('Clean Code: 41, ₹26,609.00')).toBeInTheDocument();
    expect(screen.getByText('41')).toBeInTheDocument();
    expect(screen.getByLabelText('Yoga Mat: 0')).toBeInTheDocument();
  });

  it('shows an empty message', () => {
    render(<BarList items={[]} emptyText="No paid orders yet" />);
    expect(screen.getByText('No paid orders yet')).toBeInTheDocument();
  });
});
