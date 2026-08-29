import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

type ToastVariant = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  variant: ToastVariant;
  message: string;
}

interface ToastApi {
  show: (variant: ToastVariant, message: string) => void;
  success: (message: string) => void;
  error: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

// design.md 4.6: success dismisses after ~4s, errors linger a little longer.
const DISMISS_MS: Record<ToastVariant, number> = { success: 4000, error: 6000, info: 4000 };
const MAX_VISIBLE = 3;

const VARIANT_CLASSES: Record<ToastVariant, string> = {
  success: 'border-success/40 text-success',
  error: 'border-danger/40 text-danger',
  info: 'border-accent/40 text-accent',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const show = useCallback((variant: ToastVariant, message: string) => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, variant, message }].slice(-MAX_VISIBLE));
    window.setTimeout(
      () => setToasts((current) => current.filter((toast) => toast.id !== id)),
      DISMISS_MS[variant],
    );
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      success: (message: string) => show('success', message),
      error: (message: string) => show('error', message),
    }),
    [show],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed right-4 top-4 z-50 flex w-80 max-w-[calc(100vw-32px)] flex-col gap-2"
        role="status"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`pointer-events-auto rounded-card border bg-surface px-4 py-3 text-body shadow-md ${VARIANT_CLASSES[toast.variant]}`}
          >
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider');
  }
  return context;
}
