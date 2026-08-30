/** Formatting helpers shared across screens. */

/** Fees are rupees; the clinic is India-only for MVP (product doc 22). */
export function formatFee(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(amount);
}

/** "2026-08-20" reads as "Thu 20 Aug 2026". */
export function formatDate(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

/** "10:00:00" reads as "10:00". */
export function formatTime(time: string): string {
  return time.slice(0, 5);
}

/** The date the API expects, in the clinic's own local terms. */
export function toApiDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** The next `count` days starting today, for the date strip. */
export function upcomingDates(count: number): string[] {
  const today = new Date();
  return Array.from({ length: count }, (_, offset) => {
    const date = new Date(today);
    date.setDate(today.getDate() + offset);
    return toApiDate(date);
  });
}
