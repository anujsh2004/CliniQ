import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { appointments, doctors } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { DateStrip } from '@/components/DateStrip';
import { PageHeader } from '@/components/PageHeader';
import { SlotGrid } from '@/components/SlotGrid';
import { EmptyState, ErrorState, SkeletonGrid } from '@/components/States';
import { Stepper, type Step } from '@/components/Stepper';
import { useToast } from '@/components/Toast';
import { formatDate, formatFee, formatTime, upcomingDates } from '@/lib/format';

const STEPS: Step[] = [
  { key: 'date', label: 'Pick a date' },
  { key: 'slot', label: 'Pick a time' },
  { key: 'confirm', label: 'Confirm' },
];

const DATE_WINDOW_DAYS = 14;

export function DoctorDetailPage() {
  const { doctorId = '' } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const dates = upcomingDates(DATE_WINDOW_DAYS);
  const [date, setDate] = useState(dates[0]);
  const [selectedSlotId, setSelectedSlotId] = useState<string | undefined>();
  const [staleSlotId, setStaleSlotId] = useState<string | undefined>();
  const [reason, setReason] = useState('');

  const doctorQuery = useQuery({
    queryKey: ['doctor', doctorId],
    queryFn: () => doctors.get(doctorId),
  });

  const slotsQuery = useQuery({
    queryKey: ['slots', doctorId, date],
    queryFn: () => doctors.slots(doctorId, date),
  });

  const booking = useMutation({
    mutationFn: (slotId: string) => appointments.book({ doctorId, slotId, reason: reason.trim() }),
    onSuccess: () => {
      toast.success('Appointment booked');
      void queryClient.invalidateQueries({ queryKey: ['appointments'] });
      navigate('/appointments');
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.isSlotTaken) {
        // design.md 4.5 and 2.14: on SLOT_ALREADY_BOOKED, return the patient to
        // slot selection with a refreshed grid rather than leaving them staring
        // at a dead confirm button.
        setStaleSlotId(selectedSlotId);
        setSelectedSlotId(undefined);
        void slotsQuery.refetch();
        toast.error('That slot was just booked by someone else. Please pick another time.');
        return;
      }
      toast.error(
        error instanceof ApiRequestError ? error.message : 'Could not reach the server.',
      );
    },
  });

  const selectedSlot = slotsQuery.data?.slots.find((slot) => slot.slotId === selectedSlotId);
  const currentStep = selectedSlotId ? 2 : 1;

  if (doctorQuery.isError) {
    return (
      <ErrorState
        message="We could not load this doctor."
        onRetry={() => void doctorQuery.refetch()}
      />
    );
  }

  return (
    <>
      <PageHeader
        title={doctorQuery.data?.name ?? 'Doctor'}
        description={
          doctorQuery.data
            ? `${doctorQuery.data.specialization} · ${formatFee(doctorQuery.data.consultationFee)} per visit`
            : undefined
        }
      />

      {doctorQuery.data && (
        <Card className="mb-6">
          <p className="text-cardTitle text-text-primary">{doctorQuery.data.clinic.name}</p>
          <p className="text-body text-text-secondary">{doctorQuery.data.clinic.address}</p>
          {doctorQuery.data.clinic.phone && (
            <p className="tabular text-body text-text-secondary">{doctorQuery.data.clinic.phone}</p>
          )}
        </Card>
      )}

      <Stepper steps={STEPS} currentIndex={currentStep} />

      <DateStrip
        dates={dates}
        selected={date}
        onSelect={(next) => {
          setDate(next);
          setSelectedSlotId(undefined);
          setStaleSlotId(undefined);
        }}
      />

      {slotsQuery.isPending && <SkeletonGrid cells={8} />}

      {slotsQuery.isError && (
        <ErrorState
          message="We could not load this doctor's slots."
          onRetry={() => void slotsQuery.refetch()}
        />
      )}

      {slotsQuery.isSuccess &&
        (slotsQuery.data.slots.length === 0 ? (
          <EmptyState
            title="No slots on this date"
            description="This doctor has nothing scheduled that day. Try another date above."
          />
        ) : (
          <SlotGrid
            slots={slotsQuery.data.slots}
            selectedSlotId={selectedSlotId}
            staleSlotId={staleSlotId}
            onSelect={(slotId) => {
              setSelectedSlotId(slotId);
              setStaleSlotId(undefined);
            }}
          />
        ))}

      {selectedSlot && doctorQuery.data && (
        // design.md 3.6: a review step before confirming, since booking creates
        // a payment obligation.
        <Card className="mt-6">
          <h2 className="text-cardTitle text-text-primary">Confirm your appointment</h2>
          <p className="mt-2 text-body text-text-secondary">
            {doctorQuery.data.name} · {formatDate(date)} ·{' '}
            <span className="tabular">
              {formatTime(selectedSlot.startTime)}–{formatTime(selectedSlot.endTime)}
            </span>{' '}
            · {formatFee(doctorQuery.data.consultationFee)}
          </p>

          <div className="mt-4 flex flex-col gap-1">
            <label htmlFor="reason" className="text-meta font-medium text-text-secondary">
              Reason for the visit (optional)
            </label>
            <input
              id="reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Dental check-up"
              className="rounded-card border border-border bg-surface px-3 py-2 text-body"
            />
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setSelectedSlotId(undefined)}>
              Change time
            </Button>
            <Button loading={booking.isPending} onClick={() => booking.mutate(selectedSlot.slotId)}>
              {booking.isPending ? 'Booking…' : 'Confirm booking'}
            </Button>
          </div>
        </Card>
      )}
    </>
  );
}
