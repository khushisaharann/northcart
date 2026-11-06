import { useState } from 'react';
import { Link } from 'react-router';
import toast from 'react-hot-toast';
import { MapPin, Plus } from 'lucide-react';
import { userApi } from '../../api/endpoints';
import { AddressForm, AddressText } from '../../components/address/AddressForm';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Spinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useAsync } from '../../hooks/useAsync';
import { useAuth } from '../../hooks/useAuth';
import { getErrorMessage, getFieldErrors } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { required, validate, validatePhone } from '../../utils/validation';

function ProfileForm({ user, onSaved }) {
  const [values, setValues] = useState({ fullName: user.fullName, phone: user.phone ?? '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const clientErrors = validate(values, { fullName: (v) => required(v, 'Full name'), phone: (v) => validatePhone(v) });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length) return;
    setSaving(true);
    try {
      onSaved(await userApi.updateProfile({ fullName: values.fullName.trim(), phone: values.phone.trim() || null }));
      toast.success('Profile updated');
    } catch (err) {
      setErrors(getFieldErrors(err));
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Full name" value={values.fullName} error={errors.fullName}
                 onChange={(e) => setValues({ ...values, fullName: e.target.value })} />
      <TextField label="Phone" value={values.phone} error={errors.phone}
                 onChange={(e) => setValues({ ...values, phone: e.target.value })} />
      <TextField label="Email" value={user.email} disabled hint="Email can't be changed" />
      <div className="flex items-end">
        <Button type="submit" loading={saving}>Save changes</Button>
      </div>
    </form>
  );
}

export default function AccountPage() {
  const { setUser } = useAuth();
  const { data, loading, error, reload, setData } = useAsync(() => userApi.me(), []);
  const [adding, setAdding] = useState(false);

  if (loading) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <PageHeader title="My account" subtitle={`Member since ${formatDate(data.user.memberSince)}`}
                  actions={<Link to="/orders" className="text-sm font-medium text-brand-700 hover:underline">View my orders</Link>} />
      <div className="space-y-6">
        <Card>
          <h2 className="mb-4 font-semibold text-gray-900">Profile</h2>
          <ProfileForm user={data.user} onSaved={(updated) => {
            setData({ ...data, user: updated });
            setUser(updated); // keep the navbar name in sync
          }} />
        </Card>

        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold text-gray-900">Saved addresses</h2>
            {!adding && (
              <Button variant="ghost" size="sm" onClick={() => setAdding(true)}>
                <Plus className="h-4 w-4" aria-hidden="true" /> Add address
              </Button>
            )}
          </div>
          {adding && (
            <div className="mb-6 rounded-lg border border-gray-200 p-4">
              <AddressForm defaultName={data.user.fullName} onCancel={() => setAdding(false)} onSaved={() => {
                setAdding(false);
                toast.success('Address saved');
                reload(); // a new default changes the order and flags of the others
              }} />
            </div>
          )}
          {data.addresses.length === 0 && !adding ? (
            <p className="flex items-center gap-2 text-sm text-gray-500"><MapPin className="h-4 w-4" /> No saved addresses yet.</p>
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2">
              {data.addresses.map((a) => (
                <li key={a.id} className="rounded-lg border border-gray-200 p-4">
                  {a.defaultAddress && (
                    <span className="mb-2 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">Default</span>
                  )}
                  <AddressText address={a} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
