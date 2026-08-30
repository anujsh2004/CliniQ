/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Razorpay's publishable key. Safe in the browser by design; the secret that
   * signs webhooks lives only on the server.
   */
  readonly VITE_RAZORPAY_KEY_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
