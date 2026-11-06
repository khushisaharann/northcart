/**
 * Client-side checks mirror the server's Bean Validation rules for instant feedback.
 * The server still validates everything: these only improve UX, they don't protect anything.
 */
export const PASSWORD_RULE = 'At least 8 characters, with a letter and a digit';

export function validateEmail(value) {
  if (!value?.trim()) return 'Email is required';
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()) ? null : 'Enter a valid email address';
}

export function validatePassword(value) {
  if (!value) return 'Password is required';
  if (value.length < 8 || value.length > 72) return 'Password must be 8-72 characters';
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) return 'Password must contain a letter and a digit';
  return null;
}

export function validatePhone(value, { required = false } = {}) {
  if (!value?.trim()) return required ? 'Phone is required' : null;
  return /^[0-9+\- ]{7,20}$/.test(value.trim()) ? null : 'Enter a valid phone number';
}

export function required(value, label) {
  return value?.toString().trim() ? null : `${label} is required`;
}

/** Runs { field: validatorFn } over values; returns only the failing fields. */
export function validate(values, rules) {
  const errors = {};
  for (const [field, rule] of Object.entries(rules)) {
    const message = rule(values[field], values);
    if (message) errors[field] = message;
  }
  return errors;
}
