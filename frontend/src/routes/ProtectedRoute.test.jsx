import { screen } from '@testing-library/react';
import { Route } from 'react-router';
import { customer, renderWithProviders } from '../test/renderWithProviders';
import { ProtectedRoute } from './ProtectedRoute';

function renderGuarded(auth, role) {
  return renderWithProviders(null, {
    auth,
    route: '/orders',
    extraRoutes: (
      <>
        <Route element={<ProtectedRoute role={role} />}>
          <Route path="/orders" element={<p>Secret orders page</p>} />
        </Route>
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/" element={<p>Home page</p>} />
      </>
    ),
  });
}

describe('ProtectedRoute', () => {
  it('sends anonymous visitors to the login page', () => {
    renderGuarded({ isAuthenticated: false }, 'CUSTOMER');
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('sends users without the required role home', () => {
    const admin = { ...customer, roles: ['ADMIN'] };
    renderGuarded({ isAuthenticated: true, user: admin }, 'CUSTOMER');
    expect(screen.getByText('Home page')).toBeInTheDocument();
  });

  it('renders the page for the right role', () => {
    renderGuarded({ isAuthenticated: true, user: customer, isCustomer: true }, 'CUSTOMER');
    expect(screen.getByText('Secret orders page')).toBeInTheDocument();
  });
});
