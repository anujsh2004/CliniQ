import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { notifications } from '@/api/endpoints';
import { Button } from './Button';
import { useToast } from './Toast';

/**
 * Common choices, plus a free entry for anything else. Presets cover what most
 * people want without making them think in minutes.
 */
const PRESETS = [
  { label: '2 hours before', minutes: 120 },
  { label: '3 hours before', minutes: 180 },
  { label: '6 hours before', minutes: 360 },
  { label: '2 days before', minutes: 2880 },
];

/**
 * Lets a patient add their own reminder for an appointment (API contract 15,
 * v1.4).
 *
 * <p>Additional to the clinic's own reminders, never instead of them: someone
 * who wants a nudge six hours ahead still gets the day-before one.
 */
export function ReminderPicker({ appointmentId }: { appointmentId: string }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState('');

  const add = useMutation({
    mutationFn: (minutes: number) => notifications.addReminder(appointmentId, minutes),
    onSuccess: () => {
      toast.success('Reminder added. You will see it under Reminders.');
      setOpen(false);
      setCustom('');
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        toast.error(error.fieldErrors[0].message);
        return;
      }
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not add that reminder.');
    },
  });

  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)}>
        Remind me
      </Button>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {PRESETS.map((preset) => (
        <Button
          key={preset.minutes}
          variant="secondary"
          loading={add.isPending}
          onClick={() => add.mutate(preset.minutes)}
        >
          {preset.label}
        </Button>
      ))}
      <label className="sr-only" htmlFor={`custom-${appointmentId}`}>
        Minutes before the appointment
      </label>
      <input
        id={`custom-${appointmentId}`}
        type="number"
        min={15}
        placeholder="Minutes"
        value={custom}
        onChange={(event) => setCustom(event.target.value)}
        className="tabular w-24 rounded-card border border-border bg-surface px-2 py-1 text-meta"
      />
      <Button
        variant="secondary"
        disabled={!custom}
        loading={add.isPending}
        onClick={() => add.mutate(Number(custom))}
      >
        Add
      </Button>
      <Button variant="secondary" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </div>
  );
}
