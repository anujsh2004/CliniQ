import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { symptoms as symptomsApi } from '@/api/endpoints';

/** The API's own limit (API contract 27). */
export const MAX_SYMPTOMS = 20;

/** Matches shown while searching; enough to scan, few enough to stay tidy. */
const MAX_MATCHES = 12;

interface SymptomPickerProps {
  selected: string[];
  onChange: (codes: string[]) => void;
}

/**
 * Optional symptoms for the doctor to read before the visit (API contract 27).
 *
 * <p>Nothing is shown back to the patient about what the symptoms might mean:
 * the condition hints are for the doctor alone. Renders nothing when the
 * vocabulary cannot be fetched - the ML service is off or down - because a
 * picker with nothing in it is worse than no picker.
 *
 * <p>The emergency line is deliberately static. It must not depend on a model,
 * a network call, or which boxes were ticked.
 */
export function SymptomPicker({ selected, onChange }: SymptomPickerProps) {
  const [search, setSearch] = useState('');
  const query = useQuery({
    queryKey: ['symptoms'],
    queryFn: () => symptomsApi.list(),
    retry: false,
    staleTime: Infinity,
  });

  if (!query.isSuccess) {
    return null;
  }

  const all = query.data.symptoms;
  const labels = new Map(all.map((symptom) => [symptom.code, symptom.label]));
  const term = search.trim().toLowerCase();
  const matches = term
    ? all
        .filter((symptom) => !selected.includes(symptom.code))
        .filter((symptom) => symptom.label.toLowerCase().includes(term))
        .slice(0, MAX_MATCHES)
    : [];
  const full = selected.length >= MAX_SYMPTOMS;

  const toggle = (code: string) =>
    onChange(selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code]);

  return (
    <div className="mt-4 flex flex-col gap-2">
      <label htmlFor="symptom-search" className="text-meta font-medium text-text-secondary">
        Symptoms (optional) - shared with your doctor before the visit
      </label>
      <p role="note" className="text-meta text-danger">
        Chest pain, trouble breathing, or sudden weakness? Do not wait for an appointment. Call 112
        or go to the nearest hospital now.
      </p>

      {selected.length > 0 && (
        <ul aria-label="Chosen symptoms" className="flex flex-wrap gap-2">
          {selected.map((code) => (
            <li key={code}>
              <button
                type="button"
                onClick={() => toggle(code)}
                aria-label={`Remove ${labels.get(code) ?? code}`}
                className="rounded-pill bg-accent/10 px-3 py-1 text-meta font-medium text-accent"
              >
                {labels.get(code) ?? code} ×
              </button>
            </li>
          ))}
        </ul>
      )}

      <input
        id="symptom-search"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        disabled={full}
        placeholder={full ? `Up to ${MAX_SYMPTOMS} symptoms` : 'Type a symptom, e.g. fever'}
        className="rounded-card border border-border bg-surface px-3 py-2 text-body"
      />

      {matches.length > 0 && (
        <ul aria-label="Matching symptoms" className="flex flex-wrap gap-2">
          {matches.map((symptom) => (
            <li key={symptom.code}>
              <button
                type="button"
                onClick={() => {
                  toggle(symptom.code);
                  setSearch('');
                }}
                className="rounded-pill border border-border px-3 py-1 text-meta text-text-primary"
              >
                + {symptom.label}
              </button>
            </li>
          ))}
        </ul>
      )}
      {term && matches.length === 0 && (
        <p className="text-meta text-text-secondary">No matching symptom. You can describe it in the reason above.</p>
      )}
    </div>
  );
}
