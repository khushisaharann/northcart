import { useState } from 'react';
import { userApi } from '../../api/endpoints';
import { getErrorMessage, getFieldErrors } from '../../utils/errors';
import { required, validate, validatePhone } from '../../utils/validation';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';

const EMPTY = { fullName: '', phone: '', line1: '', line2: '', city: '', state: '', postalCode: '', country: 'India' };

const RULES = {
  fullName: (v) => required(v, 'Full name'),
  phone: (v) => validatePhone(v, { required: true }),
  line1: (v) => required(v, 'Address line 1'),
  city: (v) => required(v, 'City'),
  state: (v) => required(v, 'State'),
  postalCode: (v) => required(v, 'PIN code'),
};

/** Used on Checkout and My account. Calls onSaved(address) after the server accepted it. */
export function AddressForm({ defaultName = '', onSaved, onCancel, allowDefaultToggle = true }) {
  const [values, setValues] = useState({ ...EMPTY, fullName: defaultName, makeDefault: false });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [saving, setSaving] = useState(false);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const clientErrors = validate(values, RULES);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length) return;
    setSaving(true);
    setFormError(null);
    try {
      const saved = await userApi.addAddress({ ...values, line2: values.line2 || null });
      onSaved(saved);
    } catch (err) {
      setErrors(getFieldErrors(err));
      setFormError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Full name" value={values.fullName} onChange={set('fullName')} error={errors.fullName} autoComplete="name" />
      <TextField label="Phone" value={values.phone} onChange={set('phone')} error={errors.phone} autoComplete="tel" inputMode="tel" />
      <TextField className="sm:col-span-2" label="Address line 1" value={values.line1} onChange={set('line1')} error={errors.line1} autoComplete="address-line1" />
      <TextField className="sm:col-span-2" label="Address line 2 (optional)" value={values.line2} onChange={set('line2')} error={errors.line2} autoComplete="address-line2" />
      <TextField label="City" value={values.city} onChange={set('city')} error={errors.city} autoComplete="address-level2" />
      <TextField label="State" value={values.state} onChange={set('state')} error={errors.state} autoComplete="address-level1" />
      <TextField label="PIN code" value={values.postalCode} onChange={set('postalCode')} error={errors.postalCode} autoComplete="postal-code" inputMode="numeric" />
      <TextField label="Country" value={values.country} onChange={set('country')} error={errors.country} autoComplete="country-name" />
      {allowDefaultToggle && (
        <label className="flex items-center gap-2 text-sm text-gray-700 sm:col-span-2">
          <input type="checkbox" checked={values.makeDefault} onChange={set('makeDefault')} className="h-4 w-4 rounded border-gray-300" />
          Make this my default address
        </label>
      )}
      {formError && <p role="alert" className="text-sm text-red-600 sm:col-span-2">{formError}</p>}
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" loading={saving}>Save address</Button>
        {onCancel && <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  );
}

export function AddressText({ address }) {
  return (
    <address className="text-sm not-italic leading-relaxed text-gray-600">
      <span className="font-medium text-gray-900">{address.fullName}</span> · {address.phone}<br />
      {address.line1}{address.line2 ? `, ${address.line2}` : ''}<br />
      {address.city}, {address.state} {address.postalCode}, {address.country}
    </address>
  );
}
