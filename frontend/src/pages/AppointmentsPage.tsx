import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { appointments } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';
import { formatDate, formatTime } from '@/lib/format';
import type { AppointmentListItem } from '@/types/api';

/** Cancelling and rescheduling only make sense while an appointment is live. */
function isActionable(status: AppointmentListItem['status']): boolean {
  return status === 'PENDING_PAYMENT' || status === 'CONFIRMED';
}

export function AppointmentsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState<AppointmentListItem | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['appointments', 'mine'],
    queryFn: () => appointments.mine(),
  });

  const cancel = useMutation({
    mutationFn: ({ id, why }: { id: string; why: string }) => appointments.cancel(id, why),
    onSuccess: () => {
      toast.success('Appointment cancelled');
      closeDialog();
      void queryClient.invalidateQueries({ queryKey: ['appointments'] });
      // The released slot is now free again, so any cached grid is stale.
      void queryClient.invalidateQueries({ queryKey: ['slots'] });
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setReasonError(error.fieldErrors[0].message);
        return;
      }
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not reach the server.');
    },
  });

  const closeDialog = () => {
    setCancelling(null);
    setReason('');
    setReasonError(null);
  };

  const confirmCancel = () => {
    if (!cancelling) return;
    // The contract makes the reason required, so check before the round trip.
    if (reason.trim().length === 0) {
      setReasonError('Please tell the clinic why you are cancelling.');
      return;
    }
    cancel.mutate({ id: cancelling.appointmentId, why: reason.trim() });
  };

  return (
    <>
      <PageHeader title="My appointments" description="Your bookings at this clinic." />

      {query.isPending && <SkeletonRows rows={3} />}

      {query.isError && (
        <ErrorState
          message="We could not load your appointments."
          onRetry={() => void query.refetch()}
        />
      )}

      {query.isSuccess &&
        (query.data.content.length === 0 ? (
          <EmptyState
            title="No appointments yet"
            description="Pick a doctor to book your first visit."
          />
        ) : (
          <div className="flex flex-col gap-3">
            {query.data.content.map((appointment) => (
              <Card
                key={appointment.appointmentId}
                // design.md 4.3: closed appointments are visually de-emphasised.
                className={`flex flex-wrap items-center justify-between gap-4 ${
                  isActionable(appointment.status) ? '' : 'opacity-70'
                }`}
              >
                <div>
                  <p className="text-cardTitle text-text-primary">{appointment.doctorName}</p>
                  <p className="tabular text-body text-text-secondary">
                    {formatDate(appointment.date)} · {formatTime(appointment.startTime)}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge status={appointment.status} />
                  <StatusBadge status={appointment.paymentStatus} />
                  {/* design.md 4.3: actions render only where they are valid. */}
                  {isActionable(appointment.status) && (
                    <Button variant="secondary" onClick={() => setCancelling(appointment)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
        ))}

      {cancelling && (
        <ConfirmDialog
          title="Cancel this appointment?"
          confirmLabel={cancel.isPending ? 'Cancelling…' : 'Cancel appointment'}
          destructive
          loading={cancel.isPending}
          onCancel={closeDialog}
          onConfirm={confirmCancel}
        >
          <p>
            {cancelling.doctorName} · {formatDate(cancelling.date)} ·{' '}
            {formatTime(cancelling.startTime)}
          </p>
          <div className="flex flex-col gap-1">
            <label htmlFor="cancel-reason" className="text-meta font-medium text-text-secondary">
              Reason
            </label>
            <input
              id="cancel-reason"
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                setReasonError(null);
              }}
              aria-invalid={reasonError ? true : undefined}
              className={`rounded-card border bg-surface px-3 py-2 text-body ${
                reasonError ? 'border-danger' : 'border-border'
              }`}
            />
            {reasonError && <p className="text-meta text-danger">{reasonError}</p>}
          </div>
        </ConfirmDialog>
      )}
    </>
  );
}
