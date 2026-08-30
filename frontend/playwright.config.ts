import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests (tech-stack.md Phase 5).
 *
 * These run against a real running stack - the SPA, the Spring Boot API and
 * PostgreSQL - because the things they check only exist when all three are
 * present. The concurrency race in particular cannot be observed anywhere but
 * a real database.
 */
export default defineConfig({
  testDir: './e2e',
  // The booking race needs two contexts acting at the same instant, so the
  // specs manage their own parallelism rather than being sharded.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
