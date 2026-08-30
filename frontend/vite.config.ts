/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    // Mirrors the "@/*" path alias in tsconfig.app.json.
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      // The SPA talks to the Spring Boot API on the same origin in production
      // (Nginx proxies /api/v1/*); this mirrors that in development.
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    // Vitest owns unit and component tests; the e2e directory is Playwright's,
    // and its specs cannot run under jsdom.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
