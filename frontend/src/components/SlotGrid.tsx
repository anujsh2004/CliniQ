import type { Slot } from '@/types/api';
import { StatusBadge } from './StatusBadge';

interface SlotGridProps {
  slots: Slot[];
  selectedSlotId?: string;
  onSelect?: (slotId: string) => void;
  /** Doctor-facing view: every status shown, nothing selectable. */
  readOnly?: boolean;
  /** A slot the patient had selected that was taken before they submitted. */
  staleSlotId?: string;
}

type PartOfDay = 'Morning' | 'Afternoon' | 'Evening';

/** design.md 4.2: slots group by time of day so a long list stays scannable. */
function partOfDay(startTime: string): PartOfDay {
  const hour = Number(startTime.slice(0, 2));
  if (hour < 12) return 'Morning';
  if (hour < 17) return 'Afternoon';
  return 'Evening';
}

const ORDER: PartOfDay[] = ['Morning', 'Afternoon', 'Evening'];

export function formatTime(time: string): string {
  return time.slice(0, 5);
}

export function SlotGrid({
  slots,
  selectedSlotId,
  onSelect,
  readOnly = false,
  staleSlotId,
}: SlotGridProps) {
  const groups = ORDER.map((label) => ({
    label,
    slots: slots.filter((slot) => partOfDay(slot.startTime) === label),
  })).filter((group) => group.slots.length > 0);

  return (
    <div className="flex flex-col gap-6">
      {groups.map((group) => (
        <section key={group.label}>
          <h3 className="mb-2 text-meta font-medium uppercase tracking-wide text-text-muted">
            {group.label}
          </h3>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
            {group.slots.map((slot) => (
              <SlotButton
                key={slot.slotId}
                slot={slot}
                selected={slot.slotId === selectedSlotId}
                stale={slot.slotId === staleSlotId}
                readOnly={readOnly}
                onSelect={onSelect}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function SlotButton({
  slot,
  selected,
  stale,
  readOnly,
  onSelect,
}: {
  slot: Slot;
  selected: boolean;
  stale: boolean;
  readOnly: boolean;
  onSelect?: (slotId: string) => void;
}) {
  const bookable = slot.status === 'AVAILABLE' && !readOnly;

  if (readOnly) {
    // The doctor's own view shows every status rather than only what is free.
    return (
      <div className="flex flex-col gap-1 rounded-card border border-border bg-surface px-3 py-2">
        <span className="tabular text-body text-text-primary">{formatTime(slot.startTime)}</span>
        <StatusBadge status={slot.status} />
      </div>
    );
  }

  return (
    <button
      type="button"
      disabled={!bookable}
      aria-pressed={selected}
      onClick={() => onSelect?.(slot.slotId)}
      className={`flex flex-col items-start gap-1 rounded-card border px-3 py-2 text-left transition-colors ${
        selected
          ? 'border-accent bg-accent/10'
          : bookable
            ? 'border-border bg-surface hover:border-accent'
            : 'cursor-not-allowed border-border bg-bg text-text-muted'
      }`}
    >
      <span className="tabular text-body">{formatTime(slot.startTime)}</span>
      {stale ? (
        <span className="text-meta text-danger">Just booked</span>
      ) : (
        !bookable && <span className="text-meta">Unavailable</span>
      )}
    </button>
  );
}
