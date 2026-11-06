const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });

/** 1598.5 -> "₹1,598.50" */
export function formatPrice(amount) {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) return '';
  return inr.format(Number(amount));
}

const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' });

export function formatDateTime(iso) {
  return iso ? dateTime.format(new Date(iso)) : '';
}

export function formatDate(iso) {
  return iso ? dateOnly.format(new Date(iso)) : '';
}

/** "CANCELLED" -> "Cancelled" */
export function titleCase(value) {
  if (!value) return '';
  return value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ');
}

const inrCompact = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1,
});

/** Axis labels: 12000 -> "₹12K", 250000 -> "₹2.5L" (Indian lakh/crore notation). */
export function formatPriceCompact(amount) {
  return inrCompact.format(Number(amount));
}

const shortDate = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });

/** "2026-11-06" (a LocalDate from the API) -> "6 Nov". Parsed as a local date, no timezone shift. */
export function formatShortDate(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return shortDate.format(new Date(y, m - 1, d));
}

export function formatNumber(value) {
  return new Intl.NumberFormat('en-IN').format(value);
}
