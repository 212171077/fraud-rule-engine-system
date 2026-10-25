import type { LaunchOptions } from '@playwright/test';

const MAX_SLOW_MO_MS = 5000;

// Headed mode is opt-in so CI and container runs without a display stay headless.
export function browserOptions(): { headless: boolean; launchOptions: LaunchOptions } {
  const headed = process.env['E2E_HEADED'] === '1';
  const slowMo = Number.parseInt(process.env['E2E_SLOW_MO_MS'] ?? '0', 10);
  const launchOptions: LaunchOptions = {};
  if (process.env['CHROME_BIN']) {
    launchOptions.executablePath = process.env['CHROME_BIN'];
  }
  if (Number.isInteger(slowMo) && slowMo > 0) {
    launchOptions.slowMo = Math.min(slowMo, MAX_SLOW_MO_MS);
  }
  return { headless: !headed, launchOptions };
}

// Slowed actions need proportionally more time than Playwright's 30 s default.
export function testTimeoutMs(): number {
  const slowMo = browserOptions().launchOptions.slowMo ?? 0;
  return 30_000 + slowMo * 150;
}
