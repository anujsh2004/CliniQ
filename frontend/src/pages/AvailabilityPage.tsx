import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { doctors } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';
import { useAuth } from '@/context/AuthContext';
import { formatTime } from '@/lib/format';

const DAYS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

const DURATIONS = [15, 20, 30, 45, 60];

interface FormState {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
}

const INITIAL: FormState = {
  dayOfWeek: 'MONDAY',
  startTime: '09:00',
  endTime: '17:00',
  slotDurationMinutes: 30,
};

/**
 * design.md 4.4: the doctor defines a recurring weekly window, and the backend
 * turns it into concrete slots. The ordering rule is checked here as well as on
 * the server, so an obvious mistake does not cost a round trip.
 */
export function AvailabilityPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const [form, setForm] = useState<FormState>(INITIAL);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // The doctor's own profile id, resolved from the doctor list. An admin
  // arriving here has no single profile to edit, which is why the page asks
  // them to pick a doctor first.
  const profile = useQuery({
    queryKey: ['doctors', 'self', user?.userId],
    queryFn: async () => {
      const page = await doctors.list(0, 50);
      return page.content.find((doctor) => doctor.name === user?.name) ?? null;
    },
    enabled: Boolean(user),
  });

  const doctorId = profile.data?.doctorId;

  const slotsPreview = useQuery({
    queryKey: ['availability-preview', doctorId],
    queryFn: () => doctors.slots(doctorId!, new Date().toISOString().slice(0, 10)),
    enabled: Boolean(doctorId),
  });

  const create = useMutation({
    mutationFn: () =>
      doctors.addAvailability(doctorId!, {
        dayOfWeek: form.dayOfWeek,
        startTime: `${form.startTime}:00`,
        endTime: `${form.endTime}:00`,
        slotDurationMinutes: form.slotDurationMinutes,
      }),
    onSuccess: () => {
      toast.success('Availability saved. Slots are bookable now.');
      setFieldErrors({});
      void queryClient.invalidateQueries({ queryKey: ['slots'] });
      void queryClient.invalidateQueries({ queryKey: ['availability-preview'] });
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
    // Mirrors the rule the backend enforces (design.md 4.4).
    if (form.startTime >= form.endTime) {
      setFieldErrors({ endTime: 'End time must be after start time' });
      return;
    }
    setFieldErrors({});
    create.mutate();
  };

  if (profile.isPending) {
    return <SkeletonRows rows={3} />;
  }

  if (profile.isError) {
    return (
      <ErrorState message="We could not load your profile." onRetry={() => void profile.refetch()} />
    );
  }

  if (!doctorId) {
    return (
      <>
        <PageHeader title="Availability" />
        <EmptyState
          title="No doctor profile linked to this account"
          description="Availability is defined per doctor. Ask your clinic admin to link this account to a doctor profile."
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Availability"
        description="Set the hours you work each week. Slots are generated from them straight away."
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="text-cardTitle text-text-primary">Add a weekly window</h2>
          <form className="mt-4 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-1">
              <label htmlFor="dayOfWeek" className="text-meta font-medium text-text-secondary">
                Day
              </label>
              <select
                id="dayOfWeek"
                value={form.dayOfWeek}
                onChange={(event) => setForm({ ...form, dayOfWeek: event.target.value })}
                className="rounded-card border border-border bg-surface px-3 py-2 text-body"
              >
                {DAYS.map((day) => (
                  <option key={day} value={day}>
                    {day.charAt(0) + day.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1">
                <label htmlFor="startTime" className="text-meta font-medium text-text-secondary">
                  Start
                </label>
                <input
                  id="startTime"
                  type="time"
                  value={form.startTime}
                  onChange={(event) => setForm({ ...form, startTime: event.target.value })}
                  className="rounded-card border border-border bg-surface px-3 py-2 text-body"
                />
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor="endTime" className="text-meta font-medium text-text-secondary">
                  End
                </label>
                <input
                  id="endTime"
                  type="time"
                  value={form.endTime}
                  onChange={(event) => setForm({ ...form, endTime: event.target.value })}
                  aria-invalid={fieldErrors.endTime ? true : undefined}
                  className={`rounded-card border bg-surface px-3 py-2 text-body ${
                    fieldErrors.endTime ? 'border-danger' : 'border-border'
                  }`}
                />
                {fieldErrors.endTime && (
                  <p className="text-meta text-danger">{fieldErrors.endTime}</p>
                )}
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label
                htmlFor="slotDurationMinutes"
                className="text-meta font-medium text-text-secondary"
              >
                Appointment length
              </label>
              <select
                id="slotDurationMinutes"
                value={form.slotDurationMinutes}
                onChange={(event) =>
                  setForm({ ...form, slotDurationMinutes: Number(event.target.value) })
                }
                className="rounded-card border border-border bg-surface px-3 py-2 text-body"
              >
                {DURATIONS.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutes} minutes
                  </option>
                ))}
              </select>
              {fieldErrors.slotDurationMinutes && (
                <p className="text-meta text-danger">{fieldErrors.slotDurationMinutes}</p>
              )}
            </div>

            {fieldErrors.startTime && (
              <p role="alert" className="text-meta text-danger">
                {fieldErrors.startTime}
              </p>
            )}

            <div className="flex justify-end">
              <Button type="submit" loading={create.isPending}>
                {create.isPending ? 'Saving…' : 'Save availability'}
              </Button>
            </div>
          </form>
        </Card>

        <Card>
          <h2 className="text-cardTitle text-text-primary">Today's slots</h2>
          <p className="mt-1 text-meta text-text-secondary">
            What patients can see for today right now.
          </p>
          <div className="mt-4">
            {slotsPreview.isPending && <SkeletonRows rows={3} />}
            {slotsPreview.isSuccess &&
              (slotsPreview.data.slots.length === 0 ? (
                <EmptyState
                  title="No slots today"
                  description="Add a window for today and slots appear immediately."
                />
              ) : (
                <ul className="flex flex-col gap-2">
                  {slotsPreview.data.slots.map((slot) => (
                    <li
                      key={slot.slotId}
                      className="flex items-center justify-between rounded-card border border-border px-3 py-2"
                    >
                      <span className="tabular text-body">
                        {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
                      </span>
                      <span className="text-meta text-text-secondary">{slot.status}</span>
                    </li>
                  ))}
                </ul>
              ))}
          </div>
        </Card>
      </div>
    </>
  );
}
