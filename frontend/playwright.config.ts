import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration for Project Aegis Console.
 * 
 * Strict constraints per TSK-008:
 * - Chromium only
 * - workers = 1 (memory is tight on this machine)
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 45000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60000,
  },
});
