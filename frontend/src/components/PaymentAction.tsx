import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { payments } from '@/api/endpoints';
import { Button } from './Button';
import { useToast } from './Toast';
import { isCheckoutConfigured, openCheckout } from '@/lib/razorpay';
import { formatFee } from '@/lib/format';
import type { AppointmentListItem } from '@/types/api';

/**
 * Pays for an appointment that is still awaiting payment (API contract 14).
 *
 * <p>The button creates a gateway order and hands it to Razorpay's checkout.
 * Nothing here confirms the appointment: that happens when the gateway's signed
 * webhook reaches the backend, so closing the browser mid-payment cannot leave
 * the appointment wrongly confirmed, and a manipulated browser cannot confirm
 * an unpaid one (product-description.md 8.6).
 */
export function PaymentAction({
  appointment,
  patientName,
}: {
  appointment: AppointmentListItem;
  patientName: string;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [awaitingConfirmation, setAwaitingConfirmation] = useState(false);

  const refreshAppointments = () =>
    queryClient.invalidateQueries({ queryKey: ['appointments'] });

  const handlePay = async () => {
    setBusy(true);
    try {
      const order = await payments.createOrder(appointment.appointmentId);

      if (!isCheckoutConfigured()) {
        // No publishable key in this environment. Say so plainly rather than
        // opening a checkout that cannot work; the order is real and the
        // appointment can still be paid once a key is configured.
        toast.error('Online payment is not set up yet. Please pay at the clinic.');
        void refreshAppointments();
        return;
      }

      await openCheckout(order, patientName, {
        onCompleted: () => {
          // Deliberately not "Paid": the gateway still has to tell the backend.
          setAwaitingConfirmation(true);
          toast.success('Payment submitted. Confirming with the clinic…');
          void refreshAppointments();
        },
        onDismissed: () => {
          toast.error('Payment cancelled. Your appointment is still held.');
        },
      });
    } catch (error) {
      toast.error(
        error instanceof ApiRequestError ? error.message : 'Could not start the payment.',
      );
    } finally {
      setBusy(false);
    }
  };

  if (awaitingConfirmation) {
    return (
      <span className="text-meta text-text-secondary">Confirming payment…</span>
    );
  }

  return (
    <Button loading={busy} onClick={handlePay}>
      {busy ? 'Starting…' : 'Pay now'}
    </Button>
  );
}

/** The fee is not on the list payload, so the label stays generic there. */
export function payLabel(amount?: number): string {
  return amount === undefined ? 'Pay now' : `Pay ${formatFee(amount)}`;
}
