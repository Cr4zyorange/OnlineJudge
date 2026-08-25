import { expect, test } from '../fixtures';

test.describe('@smoke shared real-application contract', () => {
  test('serves the OnlineJudgeForSE application shell', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(/\u5b66\u77e5\u5b9e\u8bad\u5e73\u53f0/);
    await expect(page.locator('#app')).not.toBeEmpty();
    await expect(page.getByRole('heading', { name: '\u7528\u6237\u767b\u5f55' })).toBeVisible();
  });

  test('proxies the backend health endpoint through the application entry', async ({ request }) => {
    const response = await request.get('/api/v1/system/health');

    expect(response.ok()).toBe(true);
    expect(await response.json()).toEqual({
      code: '0',
      message: 'success',
      data: { status: 'UP' }
    });
  });
});
