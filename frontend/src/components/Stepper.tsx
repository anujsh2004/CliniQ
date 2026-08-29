/**
 * design.md 4.5: the patient-facing flow shell. Steps show as upcoming, active
 * or complete, and a step cannot be skipped ahead of its required selection.
 */
export interface Step {
  key: string;
  label: string;
}

export function Stepper({ steps, currentIndex }: { steps: Step[]; currentIndex: number }) {
  return (
    <ol className="mb-6 flex flex-wrap items-center gap-2" aria-label="Booking progress">
      {steps.map((step, index) => {
        const state = index < currentIndex ? 'complete' : index === currentIndex ? 'active' : 'upcoming';
        return (
          <li key={step.key} className="flex items-center gap-2">
            <span
              aria-current={state === 'active' ? 'step' : undefined}
              className={`flex items-center gap-2 rounded-pill px-3 py-1 text-meta ${
                state === 'active'
                  ? 'bg-accent/10 font-medium text-accent'
                  : state === 'complete'
                    ? 'bg-success/10 text-success'
                    : 'bg-bg text-text-muted'
              }`}
            >
              <span className="tabular">{index + 1}</span>
              {step.label}
            </span>
            {index < steps.length - 1 && <span aria-hidden="true" className="text-text-muted">→</span>}
          </li>
        );
      })}
    </ol>
  );
}
