import type { ReactNode } from 'react';

/** design.md 1.10: whitespace separates sections rather than padding everything. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-display text-text-primary">{title}</h1>
        {description && <p className="mt-1 text-body text-text-secondary">{description}</p>}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}
