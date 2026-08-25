import {
  expect,
  test as base,
  type Locator,
  type Page,
  type TestInfo
} from '@playwright/test';

export type DemoRole = 'student' | 'teacher' | 'admin';

type LoginAs = (role: DemoRole) => Promise<void>;
type Logout = () => Promise<void>;
type WaitForBusinessState = (
  target: Locator,
  expected: string | RegExp,
  timeout?: number
) => Promise<void>;
type FailureEvidenceName = (suffix: string) => string;

interface SharedE2EFixtures {
  isolatedSession: void;
  loginAs: LoginAs;
  logout: Logout;
  waitForBusinessState: WaitForBusinessState;
  failureEvidenceName: FailureEvidenceName;
}

interface DemoCredentials {
  account: string;
  password: string;
}

const DEMO_ACCOUNTS: Record<DemoRole, DemoCredentials> = {
  student: {
    account: process.env.E2E_STUDENT_ACCOUNT?.trim() || 'student001',
    password: process.env.E2E_STUDENT_PASSWORD || 'Student001@pass'
  },
  teacher: {
    account: process.env.E2E_TEACHER_ACCOUNT?.trim() || 'teacher001',
    password: process.env.E2E_TEACHER_PASSWORD || 'Teacher001@pass'
  },
  admin: {
    account: process.env.E2E_ADMIN_ACCOUNT?.trim() || 'admin001',
    password: process.env.E2E_ADMIN_PASSWORD || 'Admin001@pass'
  }
};

export const test = base.extend<SharedE2EFixtures>({
  isolatedSession: [async ({ context, page }, use) => {
    await context.clearCookies();
    await page.goto('/');
    await page.evaluate(() => {
      window.localStorage.clear();
      window.sessionStorage.clear();
    });

    await use();

    await context.clearCookies();
    if (!page.isClosed()) {
      await page.evaluate(() => {
        window.localStorage.clear();
        window.sessionStorage.clear();
      }).catch(() => undefined);
    }
  }, { auto: true }],

  loginAs: async ({ page }, use) => {
    await use(async (role) => loginAs(page, role));
  },

  logout: async ({ page }, use) => {
    await use(async () => {
      await page.getByRole('button', { name: /\u9000\u51fa/ }).click();
      await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    });
  },

  waitForBusinessState: async ({}, use) => {
    await use(async (target, expected, timeout = 10_000) => {
      await expect(target).toContainText(expected, { timeout });
    });
  },

  failureEvidenceName: async ({}, use, testInfo) => {
    await use((suffix) => evidenceName(testInfo, suffix));
  }
});

async function loginAs(page: Page, role: DemoRole) {
  const credentials = DEMO_ACCOUNTS[role];

  await page.goto('/login');
  await page.locator('input[name="account"]').fill(credentials.account);
  await page.locator('input[name="password"]').fill(credentials.password);
  await page.locator('form[data-auth-form="login"] button[type="submit"]').click();
  await expect(page.locator('.auth-feedback.success')).toHaveText('\u767b\u5f55\u6210\u529f');
}

function evidenceName(testInfo: TestInfo, suffix: string) {
  const title = testInfo.titlePath.join('-');
  const safeTitle = `${title}-${suffix}`
    .normalize('NFKD')
    .replace(/[^a-zA-Z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();

  return `${safeTitle || 'e2e-failure'}-retry-${testInfo.retry}`;
}

export { expect };
