import { validate, validateEmail, validatePassword, validatePhone } from './validation';

describe('validation (mirrors the backend rules)', () => {
  it('checks email format', () => {
    expect(validateEmail('')).toBe('Email is required');
    expect(validateEmail('not-an-email')).toBe('Enter a valid email address');
    expect(validateEmail('khushi@example.com')).toBeNull();
  });

  it('requires 8-72 characters with a letter and a digit', () => {
    expect(validatePassword('short1')).toMatch(/8-72/);
    expect(validatePassword('onlyletters')).toMatch(/letter and a digit/);
    expect(validatePassword('12345678')).toMatch(/letter and a digit/);
    expect(validatePassword('Secret123')).toBeNull();
  });

  it('accepts Indian phone formats and allows an optional phone', () => {
    expect(validatePhone('+91 98765 43210')).toBeNull();
    expect(validatePhone('')).toBeNull();
    expect(validatePhone('', { required: true })).toBe('Phone is required');
    expect(validatePhone('abc')).toBe('Enter a valid phone number');
  });

  it('collects only failing fields', () => {
    expect(validate({ email: 'x', password: 'Secret123' }, { email: validateEmail, password: validatePassword }))
      .toEqual({ email: 'Enter a valid email address' });
  });
});
