import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import toast from 'react-hot-toast';
import { authApi } from '../../api/endpoints';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { getErrorMessage } from '../../utils/errors';
import { PASSWORD_RULE, validatePassword } from '../../utils/validation';
import { AuthLayout, AuthLink } from './AuthLayout';

/** Opened from the email link: /reset-password?token=... */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (!token) {
    return (
      <AuthLayout title="Invalid link" footer={<AuthLink to="/forgot-password">Request a new link</AuthLink>}>
        <p className="text-sm text-gray-600">This password reset link is incomplete. Please use the link from your email.</p>
      </AuthLayout>
    );
  }

  const submit = async (e) => {
    e.preventDefault();
    const next = { password: validatePassword(password), confirm: password === confirm ? null : 'Passwords do not match' };
    setErrors(next);
    if (next.password || next.confirm) return;
    setSubmitting(true);
    setFormError(null);
    try {
      await authApi.resetPassword(token, password);
      toast.success('Password updated. Please log in.');
      navigate('/login', { replace: true });
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Choose a new password" footer={<AuthLink to="/login">Back to log in</AuthLink>}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField label="New password" type="password" autoComplete="new-password" value={password}
                   onChange={(e) => setPassword(e.target.value)} error={errors.password} hint={PASSWORD_RULE} />
        <TextField label="Confirm password" type="password" autoComplete="new-password" value={confirm}
                   onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
        {formError && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError} <AuthLink to="/forgot-password">Request a new link</AuthLink>
          </p>
        )}
        <Button type="submit" className="w-full" loading={submitting}>Update password</Button>
      </form>
    </AuthLayout>
  );
}
