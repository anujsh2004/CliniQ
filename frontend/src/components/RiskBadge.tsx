import type { NoShowRisk } from '@/types/api';

interface RiskBadgeProps {
  risk?: NoShowRisk;
}

/**
 * The no-show model's hint, on the doctor's own schedule only (API contract 26).
 *
 * <p>LOW is not shown. Half the day carries it by construction, and a badge on
 * every row would teach the doctor to ignore the ones that matter. The text
 * says what the colour means, per design.md 3.7.
 */
export function RiskBadge({ risk }: RiskBadgeProps) {
  if (!risk || risk.band === 'LOW') {
    return null;
  }
  const high = risk.band === 'HIGH';
  return (
    <span
      className={`tabular inline-flex items-center rounded-pill px-3 py-1 text-meta font-medium ${
        high ? 'bg-danger/10 text-danger' : 'bg-warning/10 text-warning'
      }`}
      title="Estimated from booking lead time, past visits and reminders. A prompt to call ahead, not a certainty."
    >
      {high ? 'High' : 'Medium'} no-show risk · {Math.round(risk.probability * 100)}%
    </span>
  );
}
