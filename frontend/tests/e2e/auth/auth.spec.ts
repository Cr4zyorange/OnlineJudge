import { expect, test } from '../fixtures';

const AUTH_TOKEN_KEY = 'onlinejudge.authToken';

test.describe('@auth AUTH real-application scenarios', () => {
  test('student logs in and lands on the learning task center', async ({ page, loginAs }) => {
    await loginAs('student');

    await page.getByRole('link', { name: '学生工作台' }).click();

    await expect(page).toHaveURL(/\/learning\/tasks$/);
    await expect(page.getByRole('heading', { name: '学习任务中心' })).toBeVisible();
  });

  test('teacher logs in and lands on the course center', async ({ page, loginAs }) => {
    await loginAs('teacher');

    await page.getByRole('link', { name: '教师工作台' }).click();

    await expect(page).toHaveURL(/\/courses$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('课程');
  });

  test('admin logs in and lands on the auth and permission management page', async ({ page, loginAs }) => {
    await loginAs('admin');

    await page.getByRole('link', { name: '管理员工作台' }).click();

    await expect(page).toHaveURL(/\/admin\/auth$/);
    await expect(page.getByRole('heading', { name: '用户权限管理' })).toBeVisible();
  });

  test('wrong credentials show the safe login failure without entering any workspace', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[name="account"]').fill('student001');
    await page.locator('input[name="password"]').fill('definitely-wrong-password');
    await page.locator('form[data-auth-form="login"] button[type="submit"]').click();

    await expect(page.locator('.auth-feedback.error')).toContainText('账号或密码错误');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('disabled account cannot log in and receives account status feedback', async ({ page, request }) => {
    const account = await registerUniqueStudent(request);

    const adminToken = await loginToken(request, 'admin001', 'Admin001@pass');
    const disableResponse = await request.put(`/api/v1/admin/users/${account.id}/status`, {
      headers: bearer(adminToken),
      data: { accountStatus: 'DISABLED' }
    });
    expect(disableResponse.ok()).toBe(true);

    await page.goto('/login');
    await page.locator('input[name="account"]').fill(account.username);
    await page.locator('input[name="password"]').fill(account.password);
    await page.locator('form[data-auth-form="login"] button[type="submit"]').click();

    await expect(page).toHaveURL(/\/account-disabled$/);
    await expect(page.getByRole('heading', { name: '账号状态异常' })).toBeVisible();
  });

  test('account locked by repeated failures cannot log in with the correct password', async ({ page, request }) => {
    const account = await registerUniqueStudent(request);

    for (let attempt = 0; attempt < 5; attempt++) {
      const failed = await request.post('/api/v1/auth/login', {
        data: { account: account.username, password: 'wrong-password' }
      });
      expect(failed.status()).toBe(401);
    }

    await page.goto('/login');
    await page.locator('input[name="account"]').fill(account.username);
    await page.locator('input[name="password"]').fill(account.password);
    await page.locator('form[data-auth-form="login"] button[type="submit"]').click();

    await expect(page).toHaveURL(/\/account-disabled$/);
    await expect(page.getByRole('heading', { name: '账号状态异常' })).toBeVisible();
  });

  test('invalid session is rejected and the app redirects to the session expired page', async ({ page, loginAs }) => {
    await loginAs('student');

    await page.evaluate((tokenKey) => {
      window.localStorage.setItem(tokenKey, 'forged.invalid.token');
    }, AUTH_TOKEN_KEY);

    await page.goto('/courses');

    await expect(page).toHaveURL(/\/session-expired$/);
    await expect(page.getByRole('heading', { name: '登录状态已失效' })).toBeVisible();
    await expect(page.evaluate((tokenKey) => window.localStorage.getItem(tokenKey), AUTH_TOKEN_KEY))
      .resolves.toBeNull();
  });

  test('student accessing the admin page is rejected with the forbidden page', async ({ page, loginAs }) => {
    await loginAs('student');

    await page.goto('/admin/auth');

    await expect(page).toHaveURL(/\/403$/);
    await expect(page.getByRole('heading', { name: '无权限访问' })).toBeVisible();
  });

  test('logout invalidates the old session so the token and protected pages are rejected', async ({
    page,
    request,
    loginAs,
    logout
  }) => {
    await loginAs('student');
    await page.getByRole('link', { name: '学生工作台' }).click();
    await expect(page).toHaveURL(/\/learning\/tasks$/);

    const oldToken = await page.evaluate((tokenKey) => window.localStorage.getItem(tokenKey), AUTH_TOKEN_KEY);
    expect(oldToken).toBeTruthy();

    await logout();
    await expect(page).toHaveURL(/\/login$/);

    const staleResponse = await request.get('/api/v1/auth/me', {
      headers: bearer(oldToken as string)
    });
    expect(staleResponse.status()).toBe(401);

    await page.goto('/learning/tasks');
    await expect(page).toHaveURL(/\/login$/);
  });
});

interface UniqueStudent {
  id: number;
  username: string;
  password: string;
}

async function registerUniqueStudent(request: import('@playwright/test').APIRequestContext): Promise<UniqueStudent> {
  const nonce = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const username = `e2e-auth-${nonce}`;
  const password = 'E2eAuth@pass';

  const response = await request.post('/api/v1/auth/register', {
    data: {
      username,
      password,
      userType: 'STUDENT',
      displayName: 'E2E 学生',
      email: `${username}@example.com`
    }
  });
  expect(response.ok()).toBe(true);

  const payload = await response.json();
  return {
    id: payload.data.id,
    username,
    password
  };
}

async function loginToken(
  request: import('@playwright/test').APIRequestContext,
  account: string,
  password: string
): Promise<string> {
  const response = await request.post('/api/v1/auth/login', {
    data: { account, password }
  });
  expect(response.ok()).toBe(true);
  const payload = await response.json();
  return payload.data.token;
}

function bearer(token: string) {
  return { Authorization: `Bearer ${token}` };
}
