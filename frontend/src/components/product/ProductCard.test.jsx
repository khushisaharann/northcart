import { screen } from '@testing-library/react';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ProductCard } from './ProductCard';

const product = {
  id: 1, name: 'Wireless Mouse', slug: 'wireless-mouse', price: 799, thumbnailUrl: null,
  category: { id: 1, name: 'Electronics', slug: 'electronics' }, inStock: true,
};

describe('ProductCard', () => {
  it('shows name, category, price and links to the product page', () => {
    renderWithProviders(<ProductCard product={product} />);
    expect(screen.getByText('Wireless Mouse')).toBeInTheDocument();
    expect(screen.getByText('Electronics')).toBeInTheDocument();
    expect(screen.getByText('₹799.00')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/products/wireless-mouse');
    expect(screen.queryByText('Out of stock')).not.toBeInTheDocument();
  });

  it('flags out-of-stock products', () => {
    renderWithProviders(<ProductCard product={{ ...product, inStock: false }} />);
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
  });
});
