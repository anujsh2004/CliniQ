import { formatDate } from '@/lib/format';

/**
 * design.md 2.8: a date strip above the slot grid, so picking a day is one tap
 * rather than a calendar dialog.
 */
export function DateStrip({
  dates,
  selected,
  onSelect,
}: {
  dates: string[];
  selected: string;
  onSelect: (date: string) => void;
}) {
  return (
    <div className="mb-6 flex gap-2 overflow-x-auto pb-2" role="tablist" aria-label="Choose a date">
      {dates.map((date) => {
        const isSelected = date === selected;
        return (
          <button
            key={date}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => onSelect(date)}
            className={`whitespace-nowrap rounded-card border px-3 py-2 text-meta transition-colors ${
              isSelected
                ? 'border-accent bg-accent/10 font-medium text-accent'
                : 'border-border bg-surface text-text-secondary hover:border-accent'
            }`}
          >
            {formatDate(date)}
          </button>
        );
      })}
    </div>
  );
}
