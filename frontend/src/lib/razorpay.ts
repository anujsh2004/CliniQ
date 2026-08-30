import type { PaymentOrder } from '@/types/api';

/**
 * Razorpay's hosted checkout (API contract 14, tech-stack.md 6.4).
 *
 * <p>The key id here is the publishable one and is meant to be in the browser.
 * The key secret never is: it signs the webhook the backend verifies, which is
 * the only thing that can confirm an appointment.
 */
const KEY_ID = import.meta.env.VITE_RAZORPAY_KEY_ID as string | undefined;
const CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

export function isCheckoutConfigured(): boolean {
  return Boolean(KEY_ID);
}

interface RazorpayInstance {
  open: () => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

let scriptPromise: Promise<void> | null = null;

/** Loads the checkout script once, and reuses it afterwards. */
function loadCheckoutScript(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = CHECKOUT_SCRIPT;
    script.onload = () => resolve();
    script.onerror = () => {
      scriptPromise = null;
      reject(new Error('Could not load the payment checkout.'));
    };
    document.body.appendChild(script);
  });
  return scriptPromise;
}

export interface CheckoutHandlers {
  /**
   * The patient finished the checkout. This is a hint to refresh, not proof of
   * payment: the appointment is confirmed by the gateway's signed webhook, and
   * a manipulated browser reaching this callback changes nothing
   * (product-description.md 8.6).
   */
  onCompleted: () => void;
  onDismissed: () => void;
}

export async function openCheckout(
  order: PaymentOrder,
  patientName: string,
  handlers: CheckoutHandlers,
): Promise<void> {
  if (!KEY_ID) {
    throw new Error('No payment key configured.');
  }
  await loadCheckoutScript();
  if (!window.Razorpay) {
    throw new Error('Could not load the payment checkout.');
  }

  const checkout = new window.Razorpay({
    key: KEY_ID,
    // Razorpay works in the minor unit; the API returns rupees.
    amount: Math.round(order.amount * 100),
    currency: order.currency,
    name: 'Cliniva',
    description: 'Appointment booking',
    order_id: order.orderId,
    prefill: { name: patientName },
    theme: { color: '#2a5ce0' },
    handler: () => handlers.onCompleted(),
    modal: { ondismiss: () => handlers.onDismissed() },
  });
  checkout.open();
}
