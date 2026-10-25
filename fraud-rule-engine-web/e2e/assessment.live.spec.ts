import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function adminSignIn(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('Email address', { exact: true }).fill(process.env['E2E_ADMIN_EMAIL']!);
  await page.getByLabel('Password', { exact: true }).fill(process.env['E2E_ADMIN_PASSWORD']!);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
}

test('live synthetic assessment read-only browser journey', async ({ page }) => {
  await adminSignIn(page);
  await expect(page.getByText('Total evaluations', { exact: true })).toBeVisible();
  const nav = page.getByRole('navigation', { name: 'Primary navigation' });
  await nav.getByRole('link', { name: 'Evaluations', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Persisted evaluations' })).toBeVisible();
  const detail = page.getByRole('link', { name: /^Open evaluation for event/ }).filter({ visible: true }).first();
  await expect(detail).toBeVisible();
  await detail.click();
  await expect(page.getByRole('heading', { name: 'Transaction details' })).toBeVisible();
  await nav.getByRole('link', { name: 'Rules', exact: true }).click();
  await page.getByRole('link', { name: 'View configuration and audit history' }).first().click();
  await expect(page.getByRole('heading', { name: 'Audit records' })).toBeVisible();
  await nav.getByRole('link', { name: 'Users', exact: true }).click();
  await expect(page.getByRole('link', { name: /^Manage / }).first()).toBeVisible();
  await page.getByRole('link', { name: /^Manage / }).first().click();
  await expect(page.getByRole('heading', { name: 'Manage account', exact: true })).toBeVisible();
});

test('opt-in live registration, approval, sign-in and disabling of a synthetic account', async ({ page, browser }) => {
  test.skip(process.env['E2E_ALLOW_WRITES'] !== 'synthetic-only',
    'Set E2E_ALLOW_WRITES=synthetic-only to explicitly permit durable account mutations.');
  const email = `smoke-${randomUUID()}@example.test`;
  const password = `Synthetic-only-${randomUUID()}`;
  const context = await browser.newContext({ baseURL: process.env['E2E_BASE_URL'] });
  try {
    const candidate = await context.newPage();
    await candidate.goto('/register');
    await candidate.getByLabel('Name', { exact: true }).fill('Synthetic');
    await candidate.getByLabel('Surname', { exact: true }).fill('Browser smoke');
    await candidate.getByLabel('Email address', { exact: true }).fill(email);
    await candidate.getByLabel(/^Password/).fill(password);
    await candidate.getByLabel('Confirm password', { exact: true }).fill(password);
    await candidate.getByRole('button', { name: 'Request account', exact: true }).click();
    await expect(candidate.getByRole('heading', { name: 'Account request received' })).toBeVisible();
    await adminSignIn(page);
    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await nav.getByRole('link', { name: 'Users', exact: true }).click();
    await page.getByRole('link', { name: `Manage ${email}`, exact: true }).click();
    await page.getByRole('combobox', { name: 'Account status', exact: true }).selectOption('ACTIVE');
    await page.getByRole('button', { name: 'Review status change' }).click();
    await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
    await expect(page.getByText('Account access updated. Existing tokens were revoked.', { exact: true })).toBeVisible();
    await candidate.goto('/login');
    await candidate.getByLabel('Email address', { exact: true }).fill(email);
    await candidate.getByLabel('Password', { exact: true }).fill(password);
    await candidate.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(candidate.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
    await page.getByRole('combobox', { name: 'Account status', exact: true }).selectOption('DISABLED');
    await page.getByRole('button', { name: 'Review status change' }).click();
    await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
    await expect(page.getByRole('definition').filter({ hasText: /^DISABLED$/ })).toBeVisible();
    await candidate.getByRole('navigation', { name: 'Primary navigation' })
      .getByRole('link', { name: 'Rules', exact: true }).click();
    await expect(candidate).toHaveURL(/\/login\?expired=true/);
  } finally {
    await context.close();
  }
});
