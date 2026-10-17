import { useEffect, useState } from 'react';

/** Whole seconds left until an instant, never negative. */
function secondsUntil(iso: string): number {
  return Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
}

/**
 * How long this appointment keeps its slot (API contract 12, v1.4).
 *
 * <p>Booking holds a slot rather than taking it, so a patient needs to know
 * there is a clock at all. Without this the slot would simply vanish from their
 * appointments a few minutes later with no explanation.
 *
 * <p>Calls {@code onExpired} once the hold runs out, so the list can refetch
 * and show what the server actually did rather than guessing.
 */
export function HoldCountdown({
  expiresAt,
  onExpired,
}: {
  expiresAt: string;
  onExpired?: () => void;
}) {
  const [remaining, setRemaining] = useState(() => secondsUntil(expiresAt));

  useEffect(() => {
    setRemaining(secondsUntil(expiresAt));
    const timer = setInterval(() => {
      const left = secondsUntil(expiresAt);
      setRemaining(left);
      if (left === 0) {
        clearInterval(timer);
        onExpired?.();
      }
    }, 1_000);
    return () => clearInterval(timer);
  }, [expiresAt, onExpired]);

  if (remaining === 0) {
    return (
      <span className="text-meta text-danger" role="status">
        Hold expired
      </span>
    );
  }

  const minutes = Math.floor(remaining / 60);
  const seconds = String(remaining % 60).padStart(2, '0');
  // Under a minute the amber reads as "hurry" rather than merely "pending".
  const urgent = remaining <= 60;

  return (
    <span
      role="status"
      aria-live="polite"
      className={`tabular text-meta ${urgent ? 'text-danger' : 'text-warning'}`}
    >
      Slot held · {minutes}:{seconds} left
    </span>
  );
}
