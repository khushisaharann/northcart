import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { StatusActions } from './AdminOrderDetailPage';

describe('StatusActions', () => {
  it('offers only the transitions the server allows', () => {
    render(<StatusActions allowed={['SHIPPED', 'CANCELLED']} onRun={() => {}} />);
    expect(screen.getByRole('button', { name: 'Mark as shipped' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel order' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as delivered' })).not.toBeInTheDocument();
  });

  it('renders nothing for a finished order', () => {
    const { container } = render(<StatusActions allowed={[]} onRun={() => {}} />);
    expect(container.querySelectorAll('button')).toHaveLength(0);
  });

  it('runs the chosen transition', async () => {
    const onRun = vi.fn();
    render(<StatusActions allowed={['DELIVERED']} onRun={onRun} />);
    await userEvent.click(screen.getByRole('button', { name: 'Mark as delivered' }));
    expect(onRun).toHaveBeenCalledWith('DELIVERED');
  });
});
