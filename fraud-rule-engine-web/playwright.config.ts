import { defineConfig } from '@playwright/test';
import { browserOptions, testTimeoutMs } from './e2e/browser-options';

export default defineConfig({
  testDir: './e2e',
  testIgnore: '**/*.live.spec.ts',
  timeout: testTimeoutMs(),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4300',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    ...browserOptions(),
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true } },
  ],
  webServer: {
    command: 'node e2e/serve.mjs',
    url: 'http://127.0.0.1:4300',
    reuseExistingServer: false,
  },
});
