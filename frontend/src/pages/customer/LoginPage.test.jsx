import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import LoginPage from './LoginPage';

describe('LoginPage', () => {
  it('validates before calling the API', async () => {
    const login = vi.fn();
    renderWithProviders(<LoginPage />, { auth: { login } });

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("shows the server's error message on wrong credentials", async () => {
    const login = vi.fn().mockRejectedValue({
      response: { status: 401, data: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' } },
    });
    renderWithProviders(<LoginPage />, { auth: { login } });

    await userEvent.type(screen.getByLabelText('Email'), 'khushi@test.com');
    await userEvent.type(screen.getByLabelText('Password'), 'WrongPass1');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(login).toHaveBeenCalledWith('khushi@test.com', 'WrongPass1');
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });
});
