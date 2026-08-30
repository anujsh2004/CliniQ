import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiRequestError } from '@/api/client';
import { auth } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Field } from '@/components/Field';
import { useToast } from '@/components/Toast';
import { useAuth } from '@/context/AuthContext';

export function RegisterPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { login } = useAuth();

  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);

    try {
      // Self-registration creates patients only. Doctor and admin accounts are
      // set up by the clinic, so the role is not a choice on this form.
      await auth.register({ ...form, role: 'PATIENT' });
      await login(form.email, form.password);
      toast.success('Account created');
      navigate('/doctors', { replace: true });
    } catch (error) {
      if (error instanceof ApiRequestError) {
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
        <h1 className="text-section text-text-primary">Create your account</h1>
        <p className="mt-1 text-body text-text-secondary">
          Book appointments without calling the clinic.
        </p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <Field label="Full name" value={form.name} onChange={update('name')} error={fieldErrors.name} />
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={update('email')}
            error={fieldErrors.email}
          />
          <Field
            label="Phone"
            type="tel"
            autoComplete="tel"
            placeholder="+919876543210"
            value={form.phone}
            onChange={update('phone')}
            error={fieldErrors.phone}
          />
          <Field
            label="Password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={update('password')}
            error={fieldErrors.password}
          />

          {formError && (
            <p role="alert" className="text-meta text-danger">
              {formError}
            </p>
          )}

          <Button type="submit" loading={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>

        <p className="mt-4 text-meta text-text-secondary">
          Already have an account?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Sign in
          </Link>
        </p>
      </Card>
    </div>
  );
}
