import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { evaluation, mockApi, navigate, password, rule, signIn, token, user } from './fixtures';

test('investigation, rule history, admin pages, keyboard and mobile reflow', async ({ page }) => {
  const logs: string[] = [];
  const errors: string[] = [];
  page.on('console', (message) => logs.push(message.text()));
  page.on('pageerror', (error) => errors.push(error.message));
  await mockApi(page);
  await signIn(page);
  await page.keyboard.press('Tab');
  await page.getByRole('link', { name: 'Skip to main content' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();
  const toggle = page.getByRole('button', { name: 'Toggle navigation' });
  if (await toggle.isVisible()) {
    await toggle.click();
    await page.getByRole('navigation', { name: 'Primary navigation' })
      .getByRole('link', { name: 'Overview', exact: true }).focus();
    await page.keyboard.press('Escape');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(toggle).toBeFocused();
  }
  for (const destination of ['Overview', 'Evaluations', 'Rules', 'Users', 'My profile']) {
    await navigate(page, destination);
    await expect(page.getByRole('heading', { name: destination, exact: true })).toBeVisible();
    const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
    expect(audit.violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  }
  await page.setViewportSize({ width: 320, height: 812 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.setViewportSize(test.info().project.name === 'mobile'
    ? { width: 375, height: 812 } : { width: 1440, height: 1000 });
  await navigate(page, 'Evaluations');
  await page.getByRole('link', { name: 'Open evaluation for event synthetic-event' }).filter({ visible: true }).click();
  await expect(page.getByText(evaluation.rules[0].evidence.detail, { exact: true })).toBeVisible();
  expect(await page.locator('.evidence-list img').count()).toBe(0);
  await page.getByRole('link', { name: 'Back to evaluations' }).click();
  await navigate(page, 'Rules');
  await page.getByRole('link', { name: 'View configuration and audit history' }).click();
  await expect(page.getByRole('heading', { name: 'Audit records' })).toBeVisible();
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  expect(logs.join('\n')).not.toContain(token);
  expect(logs.join('\n')).not.toContain(password);
  expect(logs.join('\n')).not.toContain(evaluation.eventId);
  expect(logs.join('\n')).not.toContain(evaluation.rules[0].evidence.detail);
  expect(errors).toEqual([]);
  expect(await page.evaluate(() => Object.keys(localStorage).length + Object.keys(sessionStorage).length)).toBe(0);
});

test('expired or disabled account clears session and returns to sign-in', async ({ page }) => {
  await mockApi(page);
  await signIn(page);
  await page.route('**/api/v1/fraud-rules', (route) => route.fulfill({ status: 401, json: {} }));
  await navigate(page, 'Rules');
  await expect(page).toHaveURL(/\/login\?expired=true/);
  await expect(page.getByText('Your session ended. Sign in again to continue.')).toBeVisible();
});

test('auditor permissions prevent admin navigation and direct route access', async ({ page }) => {
  await mockApi(page, 'AUDITOR');
  await signIn(page);
  const toggle = page.getByRole('button', { name: 'Toggle navigation' });
  if (await toggle.isVisible()) await toggle.click();
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Users', exact: true })).toHaveCount(0);
  await page.evaluate(() => {
    history.pushState({}, '', '/users');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page).toHaveURL(/\/forbidden$/);
});

test('out-of-range search can return to page one and network failure is visible', async ({ page }) => {
  await mockApi(page);
  await signIn(page);
  await page.evaluate(() => {
    history.pushState({}, '', '/evaluations?page=99');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('heading', { name: 'No evaluations on this page' })).toBeVisible();
  await page.getByRole('button', { name: 'First page' }).click();
  await expect(page.getByText('synthetic-event', { exact: true }).filter({ visible: true })).toBeVisible();
  await page.route('**/api/v1/fraud-evaluations?**', (route) => route.abort());
  await page.getByRole('button', { name: 'Refresh results' }).click();
  await expect(page.getByRole('heading', { name: 'Evaluations unavailable' })).toBeVisible();
});

test('registration confirmation does not grant access', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/v1/auth/register', (route) => route.fulfill({
    status: 201, json: { id: 'pending-synthetic', status: 'PENDING', role: 'AUDITOR' },
  }));
  await page.goto('/register');
  await page.getByLabel('Name', { exact: true }).fill('Synthetic');
  await page.getByLabel('Surname', { exact: true }).fill('Pending');
  await page.getByLabel('Email address', { exact: true }).fill('pending@example.test');
  await page.getByLabel(/^Password/).fill(password);
  await page.getByLabel('Confirm password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Request account' }).click();
  await expect(page.getByRole('heading', { name: 'Account request received' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('PENDING');
  await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
});

test('disabled auth configuration offers no credentials on a deep link or reload', async ({ page }) => {
  await page.route('**/app-config.json', (route) => route.fulfill({ json: { authMode: 'disabled' } }));
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: 'Local sign-in unavailable' })).toBeVisible();
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Local sign-in unavailable' })).toBeVisible();
});

test('admin account creation and access changes require confirmation', async ({ page }) => {
  await mockApi(page);
  let account = { ...user, id: 'synthetic-reviewer', role: 'AUDITOR', status: 'PENDING' };
  let creations = 0;
  let changes = 0;
  await page.route('**/api/v1/users', async (route) => {
    if (route.request().method() === 'POST') {
      creations++;
      const body = route.request().postDataJSON();
      expect(body.role).toBe('AUDITOR');
      expect(body).not.toHaveProperty('status');
      await route.fulfill({ status: 201, json: { ...account, ...body, status: 'ACTIVE', password: undefined } });
    } else {
      await route.fallback();
    }
  });
  await page.route('**/api/v1/users?**', (route) => route.fulfill({
    json: { content: [account], page: 0, size: 20, totalElements: 1, totalPages: 1 },
  }));
  await page.route('**/api/v1/users/synthetic-reviewer', (route) => route.fulfill({ json: account }));
  await page.route('**/api/v1/users/synthetic-reviewer/status', async (route) => {
    changes++;
    account = { ...account, status: route.request().postDataJSON().status };
    await route.fulfill({ json: account });
  });
  await signIn(page);
  await navigate(page, 'Users');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill('Synthetic');
  await page.getByLabel('Surname', { exact: true }).fill('Reviewer');
  await page.getByLabel('Email address', { exact: true }).fill('new@example.test');
  await page.getByLabel(/^Initial password/).fill(password);
  await page.getByLabel('Confirm password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Review account', exact: true }).click();
  expect(creations).toBe(0);
  await page.getByRole('button', { name: 'Confirm and create' }).click();
  await expect(page.getByText('Account created with AUDITOR role and ACTIVE status.', { exact: true })).toBeVisible();
  expect(creations).toBe(1);
  await page.getByRole('link', { name: /^Manage / }).click();
  await page.getByRole('combobox', { name: 'Account status', exact: true }).selectOption('ACTIVE');
  await page.getByRole('button', { name: 'Review status change' }).click();
  expect(changes).toBe(0);
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  expect(changes).toBe(0);
  await page.getByRole('button', { name: 'Review status change' }).click();
  await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
  await expect(page.getByText('Account access updated. Existing tokens were revoked.', { exact: true })).toBeVisible();
  expect(changes).toBe(1);
});

test('rule review displays replacement parameters and saves the numeric input', async ({ page }) => {
  await mockApi(page);
  let updates = 0;
  await page.route('**/api/v1/fraud-rules/THRESHOLD_LIMIT', async (route) => {
    updates++;
    expect(route.request().postDataJSON()).toMatchObject({
      scoreContribution: 3.5, parameters: { amount_threshold_zar: '50' }, reasonCode: 'ASSESSMENT_BROWSER_TEST',
    });
    await route.fulfill({ json: { ...rule, version: 2, scoreContribution: 3.5 } });
  });
  await signIn(page);
  await navigate(page, 'Rules');
  await page.getByRole('link', { name: 'View configuration and audit history' }).click();
  await page.getByRole('button', { name: 'Create updated version' }).click();
  await page.getByLabel(/^Score contribution/).fill('3.5');
  await page.getByLabel(/^Reason code/).fill('ASSESSMENT_BROWSER_TEST');
  await page.getByRole('button', { name: 'Review changes', exact: true }).click();
  await expect(page.locator('.confirmation-panel')).toContainText('amount_threshold_zar');
  expect(updates).toBe(0);
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'Confirm and save version' }).click();
  await expect(page.getByText('THRESHOLD_LIMIT version 2 created.', { exact: true })).toBeVisible();
  expect(updates).toBe(1);
});
