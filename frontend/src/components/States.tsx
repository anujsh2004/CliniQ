import type { ReactNode } from 'react';
import { Button } from './Button';

/**
 * design.md 2.13: skeletons shaped like the content they replace, rather than a
 * generic spinner, so the layout does not jump when data lands.
 */
export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-2" aria-hidden="true">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="h-12 animate-pulse rounded-card bg-border/60" />
      ))}
    </div>
  );
}

export function SkeletonGrid({ cells = 8 }: { cells?: number }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4" aria-hidden="true">
      {Array.from({ length: cells }).map((_, index) => (
        <div key={index} className="h-11 animate-pulse rounded-card bg-border/60" />
      ))}
    </div>
  );
}

/** design.md 2.12: every empty list says what is missing and what to do next. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-card border border-dashed border-border px-6 py-12 text-center">
      <p className="text-cardTitle text-text-primary">{title}</p>
      {description && <p className="max-w-sm text-body text-text-secondary">{description}</p>}
      {action}
    </div>
  );
}

/** design.md 2.14: a full-page error state with retry for failed data loads. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-card border border-danger/30 bg-danger/5 px-6 py-12 text-center">
      <p className="text-cardTitle text-text-primary">Something went wrong</p>
      <p className="max-w-sm text-body text-text-secondary">{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
