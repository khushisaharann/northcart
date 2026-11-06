import { formatPrice, titleCase } from './format';

describe('formatPrice', () => {
  it('formats rupees with Indian digit grouping', () => {
    expect(formatPrice(159850.5)).toBe('₹1,59,850.50');
    expect(formatPrice('799.00')).toBe('₹799.00');
  });
  it('returns an empty string for missing values', () => {
    expect(formatPrice(null)).toBe('');
    expect(formatPrice(undefined)).toBe('');
  });
});

describe('titleCase', () => {
  it('turns enum names into labels', () => {
    expect(titleCase('CANCELLED')).toBe('Cancelled');
    expect(titleCase('OUT_OF_STOCK')).toBe('Out of stock');
  });
});
