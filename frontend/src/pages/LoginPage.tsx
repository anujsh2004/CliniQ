import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiRequestError } from '@/api/client';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Field } from '@/components/Field';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/Toast';

/** Where each role lands after signing in. */
const HOME_BY_ROLE: Record<string, string> = {
  PATIENT: '/doctors',
  DOCTOR: '/schedule',
  ADMIN: '/doctors',
};

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);

    try {
      const user = await login(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? HOME_BY_ROLE[user.role] ?? '/doctors', { replace: true });
      toast.success(`Welcome back, ${user.name}`);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        // design.md 3.5: server-side 422s map back onto the specific inputs.
        setFieldErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])));
        setFormError(error.fieldErrors.length > 0 ? null : error.message);
      } else {
        setFormError('Could not reach the server. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col justify-center px-4 py-12">
      <p className="mb-6 text-center text-section font-semibold text-accent">Cliniva</p>
      <Card>
        <h1 className="text-section text-text-primary">Sign in</h1>
        <p className="mt-1 text-body text-text-secondary">
          Book appointments and manage your clinic day.
        </p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            error={fieldErrors.email}
          />
          <Field
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            error={fieldErrors.password}
          />

          {formError && (
            <p role="alert" className="text-meta text-danger">
              {formError}
            </p>
          )}

          <Button type="submit" loading={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>

        <p className="mt-4 text-meta text-text-secondary">
          New here?{' '}
          <Link to="/register" className="text-accent underline underline-offset-2">
            Create an account
          </Link>
        </p>
      </Card>
    </div>
  );
}
