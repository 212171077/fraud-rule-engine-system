import { expect, Page } from '@playwright/test';

export const password = 'synthetic-browser-only-42';
export const token = 'synthetic-browser-token-not-a-jwt';
export const user = {
  id: 'synthetic-admin', name: 'Synthetic', surname: 'Administrator',
  email: 'admin@example.test', role: 'ADMIN', status: 'ACTIVE',
  createdAt: '2026-10-22T10:00:00Z', updatedAt: '2026-10-22T10:00:00Z',
};
export const permissions = ['REPORTS_READ', 'EVALUATIONS_READ', 'RULES_READ', 'RULES_WRITE',
  'USERS_MANAGE', 'OWN_PROFILE_READ', 'OWN_PROFILE_WRITE'];
export const evaluation = {
  evaluationId: 'synthetic-evaluation', eventId: 'synthetic-event', category: 'TRANSFER',
  occurredAt: '2026-10-22T10:00:00Z', evaluatedAt: '2026-10-22T10:01:00Z',
  decision: 'REVIEW', status: 'COMPLETED', riskScore: 2.5, amount: 50, currency: 'ZAR',
  ruleConfigurationVersion: 1, referenceDataVersions: { country: 1 },
  rules: [{
    ruleCode: 'THRESHOLD_LIMIT', configurationVersion: 1, outcome: 'MATCH', reasonCode: 'SYNTHETIC_MATCH',
    evidence: { detail: '<img src=x onerror=alert(1)>' },
  }],
};
export const rule = {
  ruleCode: 'THRESHOLD_LIMIT', version: 1, enabled: true,
  effectiveFrom: '2026-10-22T09:00:00Z', effectiveUntil: null,
  scoreContribution: 2.5, parameters: { amount_threshold_zar: '50' },
};

export async function mockApi(page: Page, role: 'ADMIN' | 'AUDITOR' = 'ADMIN') {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let body: unknown;
    if (path.endsWith('/auth/login')) {
      body = { accessToken: token, tokenType: 'Bearer', expiresIn: 300, user: { ...user, role } };
    } else if (path.endsWith('/auth/me')) {
      body = { ...user, role, permissions: role === 'ADMIN' ? permissions :
        permissions.filter((value) => !['RULES_WRITE', 'USERS_MANAGE'].includes(value)) };
    } else if (path.endsWith('/fraud-evaluations/summary')) {
      body = { from: url.searchParams.get('from'), to: url.searchParams.get('to'),
        total: 1, decisions: { REVIEW: 1 }, decisionPercentages: { REVIEW: 100 } };
    } else if (path.endsWith('/fraud-evaluations/synthetic-evaluation')) {
      body = evaluation;
    } else if (path.endsWith('/fraud-evaluations')) {
      const index = Number(url.searchParams.get('page') ?? 0);
      body = { content: index > 0 ? [] : [evaluation], page: index,
        size: 20, totalElements: 1, totalPages: 1 };
    } else if (path.endsWith('/fraud-rules/THRESHOLD_LIMIT/history')) {
      body = { ruleCode: rule.ruleCode, history: [{
        ruleCode: rule.ruleCode, configurationVersion: 1, action: 'CREATED',
        actorReference: 'synthetic-hashed-actor', reasonCode: 'ASSESSMENT_CREATE',
        occurredAt: rule.effectiveFrom,
      }] };
    } else if (path.endsWith('/fraud-rules')) {
      body = [rule];
    } else if (path.endsWith('/users')) {
      body = { content: [user], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    } else if (path.endsWith('/users/synthetic-admin')) {
      body = user;
    } else {
      throw new Error(`Unmocked API request: ${route.request().method()} ${path}`);
    }
    await route.fulfill({ json: body });
  });
}

export async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address', { exact: true }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  for (const dismiss of await page.getByRole('button', { name: 'Dismiss notification' }).all()) {
    await dismiss.click();
  }
}

export async function navigate(page: Page, name: string) {
  const toggle = page.getByRole('button', { name: 'Toggle navigation' });
  if (await toggle.isVisible()) {
    await toggle.click();
  }
  await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name, exact: true }).click();
}
