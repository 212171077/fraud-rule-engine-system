import { defineConfig } from '@playwright/test';
import { browserOptions, testTimeoutMs } from './e2e/browser-options';

if (!process.env['E2E_BASE_URL'] || !process.env['E2E_ADMIN_EMAIL'] || !process.env['E2E_ADMIN_PASSWORD']) {
  throw new Error('Live smoke requires E2E_BASE_URL, E2E_ADMIN_EMAIL, and E2E_ADMIN_PASSWORD.');
}
const url = new URL(process.env['E2E_BASE_URL']);
if (!['127.0.0.1', 'localhost', 'api.localhost', '[::1]'].includes(url.hostname)) {
  throw new Error('Live assessment smoke is restricted to loopback hosts.');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.live.spec.ts',
  timeout: testTimeoutMs(),
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: url.origin,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    ...browserOptions(),
  },
});
