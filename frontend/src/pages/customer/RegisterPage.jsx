import { useState } from 'react';
import { useNavigate } from 'react-router';
import toast from 'react-hot-toast';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { useAuth } from '../../hooks/useAuth';
import { getErrorMessage, getFieldErrors } from '../../utils/errors';
import { PASSWORD_RULE, required, validate, validateEmail, validatePassword, validatePhone } from '../../utils/validation';
import { AuthLayout, AuthLink } from './AuthLayout';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const set = (field) => (e) => setValues({ ...values, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const clientErrors = validate(values, {
      fullName: (v) => required(v, 'Full name'),
      email: validateEmail,
      phone: (v) => validatePhone(v),
      password: validatePassword,
    });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length) return;

    setSubmitting(true);
    setFormError(null);
    try {
      await register({ ...values, email: values.email.trim(), phone: values.phone.trim() || null });
      toast.success('Account created. Happy shopping!');
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(getFieldErrors(err)); // server-side field errors, e.g. a duplicate email
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Create your account" subtitle="It takes less than a minute"
                footer={<>Already have an account? <AuthLink to="/login">Log in</AuthLink></>}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField label="Full name" autoComplete="name" value={values.fullName} onChange={set('fullName')} error={errors.fullName} />
        <TextField label="Email" type="email" autoComplete="email" value={values.email} onChange={set('email')} error={errors.email} />
        <TextField label="Phone (optional)" type="tel" autoComplete="tel" value={values.phone} onChange={set('phone')} error={errors.phone} />
        <TextField label="Password" type="password" autoComplete="new-password" value={values.password}
                   onChange={set('password')} error={errors.password} hint={PASSWORD_RULE} />
        {formError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>}
        <Button type="submit" className="w-full" loading={submitting}>Create account</Button>
      </form>
    </AuthLayout>
  );
}
