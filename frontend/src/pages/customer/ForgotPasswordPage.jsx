import { useState } from 'react';
import { MailCheck } from 'lucide-react';
import { authApi } from '../../api/endpoints';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { getErrorMessage } from '../../utils/errors';
import { validateEmail } from '../../utils/validation';
import { AuthLayout, AuthLink } from './AuthLayout';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState(null);
  const [sentMessage, setSentMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const invalid = validateEmail(email);
    setError(invalid);
    if (invalid) return;
    setSubmitting(true);
    try {
      const res = await authApi.forgotPassword(email.trim());
      setSentMessage(res.message); // same message whether or not the account exists
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Reset your password" subtitle="We'll email you a link to choose a new password."
                footer={<AuthLink to="/login">Back to log in</AuthLink>}>
      {sentMessage ? (
        <div className="flex gap-3 rounded-lg bg-green-50 p-4 text-sm text-green-800" role="status">
          <MailCheck className="h-5 w-5 shrink-0" aria-hidden="true" />
          <p>{sentMessage} The link is valid for 15 minutes.</p>
        </div>
      ) : (
        <form onSubmit={submit} noValidate className="space-y-4">
          <TextField label="Email" type="email" autoComplete="email" value={email}
                     onChange={(e) => setEmail(e.target.value)} error={error} />
          <Button type="submit" className="w-full" loading={submitting}>Send reset link</Button>
        </form>
      )}
    </AuthLayout>
  );
}
