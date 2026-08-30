import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  loading?: boolean;
  children: ReactNode;
}

const VARIANTS = {
  primary: 'bg-accent text-white hover:bg-accent-hover disabled:bg-accent/50',
  secondary: 'bg-surface text-text-primary border border-border hover:bg-bg',
  danger: 'bg-danger text-white hover:opacity-90',
  ghost: 'text-text-secondary hover:text-text-primary',
};

/**
 * design.md 3.5: the submit button stays enabled and shows a loading state on
 * click, rather than being disabled up front, which leaves people wondering why
 * they cannot click it.
 */
export function Button({
  variant = 'primary',
  loading = false,
  children,
  className = '',
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-card px-4 py-2 text-body font-medium transition-colors disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      disabled={disabled ?? loading}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
