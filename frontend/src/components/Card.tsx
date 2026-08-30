import type { ReactNode } from 'react';

/**
 * design.md 2.5: surface, hairline border, 8px radius, 16-24px padding.
 * Borders rather than heavy shadows carry the separation (design.md 1.7).
 */
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-card border border-border bg-surface p-6 shadow-sm ${className}`}>
      {children}
    </div>
  );
}
