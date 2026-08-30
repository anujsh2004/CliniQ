import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { appointments } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { PaymentAction } from '@/components/PaymentAction';
import { StatusBadge } from '@/components/StatusBadge';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';
import { useAuth } from '@/context/AuthContext';
import { formatDate, formatTime } from '@/lib/format';
import type { AppointmentListItem } from '@/types/api';

/** Cancelling and rescheduling only make sense while an appointment is live. */
function isActionable(status: AppointmentListItem['status']): boolean {
  return status === 'PENDING_PAYMENT' || status === 'CONFIRMED';
}

/**
 * Payment states worth showing a patient: money that actually moved, or failed
 * to, or came back.
 *
 * <p>Deliberately an allowlist rather than "not PENDING". PENDING and CREATED
 * both mean the same thing to a patient - you have not paid yet - which the
 * status already says as "Pending payment". CREATED in particular is the
 * gateway's own word for "an order exists", which is an implementation detail
 * of Razorpay rather than anything a patient can act on.
 */
const INFORMATIVE_PAYMENT_STATES: ReadonlySet<AppointmentListItem['paymentStatus']> = new Set([
  'PAID',
  'FAILED',
  'REFUNDED',
]);

/**
 * Whether the payment badge tells the patient anything the appointment status
 * has not already said.
 *
 * <p>A cancelled appointment carries no payment obligation, so a payment badge
 * on one is noise at best and alarming at worst.
 */
function paymentBadgeIsInformative(appointment: AppointmentListItem): boolean {
  if (appointment.status === 'CANCELLED') {
    return false;
  }
  return INFORMATIVE_PAYMENT_STATES.has(appointment.paymentStatus);
}

/** An appointment the patient still owes money on. */
function awaitsPayment(appointment: AppointmentListItem): boolean {
  return (
    appointment.status === 'PENDING_PAYMENT' &&
    (appointment.paymentStatus === 'PENDING' ||
      appointment.paymentStatus === 'CREATED' ||
      appointment.paymentStatus === 'FAILED')
  );
}

export function AppointmentsPage() {
  const toast = useToast();
  const { user } = useAuth();
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
                  {paymentBadgeIsInformative(appointment) && (
                    <StatusBadge status={appointment.paymentStatus} />
                  )}
                  {awaitsPayment(appointment) && (
                    <PaymentAction
                      appointment={appointment}
                      patientName={user?.name ?? ''}
                    />
                  )}
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
