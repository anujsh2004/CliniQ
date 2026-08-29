import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { patients } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Field } from '@/components/Field';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';

export function ProfilePage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ name: '', phone: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const query = useQuery({ queryKey: ['patient', 'me'], queryFn: () => patients.me() });

  useEffect(() => {
    if (query.data) {
      setForm({ name: query.data.name, phone: query.data.phone });
    }
  }, [query.data]);

  const save = useMutation({
    mutationFn: () => patients.update(form),
    onSuccess: () => {
      toast.success('Profile updated');
      void queryClient.invalidateQueries({ queryKey: ['patient', 'me'] });
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])));
        return;
      }
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not reach the server.');
    },
  });

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    setFieldErrors({});
    save.mutate();
  };

  return (
    <>
      <PageHeader title="Profile" description="The details the clinic uses to reach you." />

      {query.isPending && <SkeletonRows rows={3} />}
      {query.isError && (
        <ErrorState message="We could not load your profile." onRetry={() => void query.refetch()} />
      )}

      {query.isSuccess && (
        <Card className="max-w-lg">
          <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
            <Field
              label="Full name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              error={fieldErrors.name}
            />
            <Field
              label="Phone"
              type="tel"
              value={form.phone}
              onChange={(event) => setForm({ ...form, phone: event.target.value })}
              error={fieldErrors.phone}
            />
            <div className="flex flex-col gap-1">
              <span className="text-meta font-medium text-text-secondary">Email</span>
              {/* Email is the login identifier and is not editable here
                  (API contract 10 has no email field on the update payload). */}
              <p className="text-body text-text-secondary">{query.data.email}</p>
            </div>
            <div className="flex justify-end">
              <Button type="submit" loading={save.isPending}>
                {save.isPending ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          </form>
        </Card>
      )}
    </>
  );
}
