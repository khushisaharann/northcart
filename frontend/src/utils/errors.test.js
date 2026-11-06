import { getErrorMessage, getFieldErrors } from './errors';

describe('API error helpers', () => {
  const apiError = {
    response: {
      status: 400,
      data: { code: 'VALIDATION_FAILED', message: 'Request validation failed',
        fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }] },
    },
  };

  it('uses the server message', () => {
    expect(getErrorMessage(apiError)).toBe('Request validation failed');
  });

  it('maps field errors by field name', () => {
    expect(getFieldErrors(apiError)).toEqual({ email: 'must be a well-formed email address' });
  });

  it('explains network failures', () => {
    expect(getErrorMessage({ request: {} })).toMatch(/Cannot reach the server/);
  });
});
