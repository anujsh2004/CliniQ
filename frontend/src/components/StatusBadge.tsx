import type { AppointmentStatus, PaymentStatus, SlotStatus } from '@/types/api';

type AnyStatus = SlotStatus | AppointmentStatus | PaymentStatus | string;

type Variant = 'success' | 'warning' | 'danger' | 'neutral' | 'info';

/**
 * The status to variant map from design.md 1.3. Callers never choose a variant
 * themselves - that is what keeps one status looking identical everywhere it
 * appears.
 */
const VARIANTS: Record<string, Variant> = {
  CONFIRMED: 'success',
  COMPLETED: 'success',
  PAID: 'success',
  AVAILABLE: 'success',
  DELIVERED: 'success',
  PENDING_PAYMENT: 'warning',
  HELD: 'warning',
  PENDING: 'warning',
  CREATED: 'warning',
  QUEUED: 'warning',
  CANCELLED: 'danger',
  NO_SHOW: 'danger',
  FAILED: 'danger',
  BLOCKED: 'danger',
  EXPIRED: 'neutral',
  BOOKED: 'neutral',
  SENT: 'info',
  PROCESSING: 'info',
};

const VARIANT_CLASSES: Record<Variant, string> = {
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  danger: 'bg-danger/10 text-danger',
  neutral: 'bg-neutral/10 text-neutral',
  info: 'bg-accent/10 text-accent',
};

/** PENDING_PAYMENT reads as "Pending payment", not as a database constant. */
export function humanizeStatus(status: AnyStatus): string {
  const lower = status.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

interface StatusBadgeProps {
  status: AnyStatus;
}

/**
 * design.md 3.7: status is never colour alone. Every badge carries its text
 * label, so the meaning survives for colour-blind users.
 */
export function StatusBadge({ status }: StatusBadgeProps) {
  const variant = VARIANTS[status] ?? 'neutral';
  return (
    <span
      className={`inline-flex items-center rounded-pill px-3 py-1 text-meta font-medium ${VARIANT_CLASSES[variant]}`}
    >
      {humanizeStatus(status)}
    </span>
  );
}
