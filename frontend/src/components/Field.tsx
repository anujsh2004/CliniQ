import type { InputHTMLAttributes } from 'react';

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

/**
 * design.md 2.7 and 3.7: a visible label above the input, never placeholder-only
 * labelling, with the validation message directly beneath the field and tied to
 * it for screen readers.
 */
export function Field({ label, error, id, ...rest }: FieldProps) {
  const inputId = id ?? `field-${label.toLowerCase().replace(/\s+/g, '-')}`;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-meta font-medium text-text-secondary">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`rounded-card border bg-surface px-3 py-2 text-body text-text-primary placeholder:text-text-muted ${
          error ? 'border-danger' : 'border-border'
        }`}
        {...rest}
      />
      {error && (
        <p id={errorId} className="text-meta text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
