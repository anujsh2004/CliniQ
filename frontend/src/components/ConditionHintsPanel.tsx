import { symptomLabel } from '@/lib/format';
import type { ConditionHints } from '@/types/api';

interface ConditionHintsPanelProps {
  symptoms?: string[];
  hints?: ConditionHints;
}

/**
 * What the patient reported, and what the model makes of it (API contract 27).
 *
 * <p>For the treating doctor only. The wording is careful on purpose: these
 * are conditions the symptoms resemble in training data, not a diagnosis, and
 * the label says so every time rather than once in a help page nobody reads.
 */
export function ConditionHintsPanel({ symptoms, hints }: ConditionHintsPanelProps) {
  if (!symptoms || symptoms.length === 0) {
    return null;
  }
  return (
    <div className="basis-full border-t border-border pt-3 text-meta">
      <p className="text-text-secondary">
        <span className="font-medium text-text-primary">Reported:</span>{' '}
        {symptoms.map(symptomLabel).join(', ')}
      </p>
      {hints && hints.conditions.length > 0 && (
        <p className="mt-1 text-text-secondary">
          <span className="font-medium text-text-primary">May resemble:</span>{' '}
          <span className="tabular">
            {hints.conditions
              .map((condition) => `${condition.name} ${Math.round(condition.probability * 100)}%`)
              .join(' · ')}
          </span>{' '}
          <span title={`Model ${hints.modelVersion}, trained on a public synthetic dataset.`}>
            (AI hint, not a diagnosis)
          </span>
        </p>
      )}
    </div>
  );
}
