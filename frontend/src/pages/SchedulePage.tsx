import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { appointments, doctors } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ConditionHintsPanel } from '@/components/ConditionHintsPanel';
import { DateStrip } from '@/components/DateStrip';
import { PageHeader } from '@/components/PageHeader';
import { RiskBadge } from '@/components/RiskBadge';
import { StatusBadge } from '@/components/StatusBadge';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';
import { formatTime, toApiDate, upcomingDates } from '@/lib/format';
import type { AppointmentStatus, DoctorDayAppointment } from '@/types/api';

const DATE_WINDOW_DAYS = 14;

/** Only a live appointment can be closed out. */
function canComplete(status: AppointmentStatus): boolean {
  return status === 'PENDING_PAYMENT' || status === 'CONFIRMED';
}

/**
 * A no-show is a confirmed visit whose time has come. The server enforces the
 * same rule; this only keeps the button off rows where it would be refused.
 */
function canMarkNoShow(appointment: DoctorDayAppointment, date: string, now: Date): boolean {
  if (appointment.status !== 'CONFIRMED') {
    return false;
  }
  const today = toApiDate(now);
  if (date !== today) {
    return date < today;
  }
  const [hours, minutes] = appointment.startTime.split(':').map(Number);
  return hours * 60 + minutes <= now.getHours() * 60 + now.getMinutes();
}

export function SchedulePage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const dates = upcomingDates(DATE_WINDOW_DAYS);
  const [date, setDate] = useState(dates[0]);

  const query = useQuery({
    queryKey: ['schedule', date],
    queryFn: () => doctors.ownDay(date),
  });

  const complete = useMutation({
    mutationFn: (appointmentId: string) => appointments.complete(appointmentId),
    onSuccess: (result) => {
      toast.success(
        result.followUpEligible
          ? 'Marked complete. This visit is eligible for a follow-up.'
          : 'Marked complete.',
      );
      void queryClient.invalidateQueries({ queryKey: ['schedule'] });
    },
    onError: (error) => {
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not reach the server.');
    },
  });

  const noShow = useMutation({
    mutationFn: (appointmentId: string) => appointments.markNoShow(appointmentId),
    onSuccess: () => {
      toast.success('Marked as a no-show.');
      void queryClient.invalidateQueries({ queryKey: ['schedule'] });
    },
    onError: (error) => {
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not reach the server.');
    },
  });

  const dayAppointments = query.data?.appointments ?? [];
  const now = new Date();

  return (
    <>
      <PageHeader
        title="Schedule"
        description="Your appointments, in time order."
        actions={
          query.isSuccess ? (
            <span className="tabular self-center text-meta text-text-secondary">
              {dayAppointments.length} booked
            </span>
          ) : undefined
        }
      />

      <DateStrip dates={dates} selected={date} onSelect={setDate} />

      {query.isPending && <SkeletonRows rows={4} />}

      {query.isError && (
        <ErrorState
          message={
            query.error instanceof ApiRequestError && query.error.status === 403
              ? 'This account is not linked to a doctor profile yet.'
              : 'We could not load your schedule.'
          }
          onRetry={() => void query.refetch()}
        />
      )}

      {query.isSuccess &&
        (dayAppointments.length === 0 ? (
          <EmptyState
            title="Nothing booked on this date"
            description="Patients who book this day will appear here."
          />
        ) : (
          <div className="flex flex-col gap-3">
            {dayAppointments.map((appointment) => (
              <Card
                key={appointment.appointmentId}
                className={`flex flex-wrap items-center justify-between gap-4 ${
                  canComplete(appointment.status) ? '' : 'opacity-70'
                }`}
              >
                <div className="flex items-center gap-4">
                  {/* Tabular figures keep the time column aligned down the list
                      (design.md 1.4), which is what makes a day scannable. */}
                  <span className="tabular text-cardTitle text-text-primary">
                    {formatTime(appointment.startTime)}
                  </span>
                  <div>
                    <p className="text-body text-text-primary">{appointment.patient.name}</p>
                    {appointment.patient.phone && (
                      <p className="tabular text-meta text-text-secondary">
                        {appointment.patient.phone}
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <RiskBadge risk={appointment.noShowRisk} />
                  <StatusBadge status={appointment.status} />
                  {canComplete(appointment.status) && (
                    <Button
                      variant="secondary"
                      loading={complete.isPending && complete.variables === appointment.appointmentId}
                      onClick={() => complete.mutate(appointment.appointmentId)}
                    >
                      Mark complete
                    </Button>
                  )}
                  {canMarkNoShow(appointment, date, now) && (
                    <Button
                      variant="secondary"
                      loading={noShow.isPending && noShow.variables === appointment.appointmentId}
                      onClick={() => noShow.mutate(appointment.appointmentId)}
                    >
                      No-show
                    </Button>
                  )}
                </div>
                <ConditionHintsPanel
                  symptoms={appointment.symptoms}
                  hints={appointment.conditionHints}
                />
              </Card>
            ))}
          </div>
        ))}
    </>
  );
}
