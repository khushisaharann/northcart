import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import toast from 'react-hot-toast';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { useAuth } from '../../hooks/useAuth';
import { getErrorMessage } from '../../utils/errors';
import { validate, validateEmail } from '../../utils/validation';
import { AuthLayout, AuthLink } from './AuthLayout';

export default function LoginPage({ variant = 'customer' }) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const clientErrors = validate(values, {
      email: validateEmail,
      password: (v) => (v ? null : 'Password is required'),
    });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length) return;

    setSubmitting(true);
    setFormError(null);
    try {
      const user = await login(values.email.trim(), values.password);
      toast.success(`Welcome back, ${user.fullName.split(' ')[0]}!`);
      const fallback = user.roles.includes('ADMIN') ? '/admin' : '/';
      navigate(location.state?.from?.pathname ?? fallback, { replace: true });
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title={variant === 'admin' ? 'NorthCart Admin' : 'Log in'}
                subtitle={variant === 'admin' ? 'Sign in with your staff account' : 'Welcome back to NorthCart'}
                footer={variant === 'admin' ? <AuthLink to="/">Back to the store</AuthLink>
                  : <>New here? <AuthLink to="/register">Create an account</AuthLink></>}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField label="Email" type="email" autoComplete="email" value={values.email}
                   onChange={(e) => setValues({ ...values, email: e.target.value })} error={errors.email} />
        <TextField label="Password" type="password" autoComplete="current-password" value={values.password}
                   onChange={(e) => setValues({ ...values, password: e.target.value })} error={errors.password} />
        <div className="text-right text-sm">
          <AuthLink to="/forgot-password">Forgot password?</AuthLink>
        </div>
        {formError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>}
        <Button type="submit" className="w-full" loading={submitting}>Log in</Button>
      </form>
    </AuthLayout>
  );
}
