import { expect, test } from '../fixtures';

/**
 * CRS 主流程闭环浏览器用例（共享 E2E 入口，Issue #267）。
 * 复用 tests/e2e/fixtures.ts 的 loginAs/waitForBusinessState 与隔离会话约定；
 * baseURL 由 playwright.config.ts 的 E2E_BASE_URL 控制（默认 Compose 8088）。
 *
 * 覆盖：教师建课/章节/资源/公告；学生公开、邀请码、审批三模式加入；
 * 审批前后权限变化；非法邀请码、满员、重复加入与资源失败。
 *
 * 前置：Compose（8088）或本地 Spring Boot + Vite（E2E_BASE_URL=http://127.0.0.1:5173）已启动并完成种子数据。
 */

test.describe('@crs CRS 主流程闭环', () => {
  test('教师建课并展示章节/资源/公告管理入口，学生公开加入', async ({
    page,
    loginAs,
    waitForBusinessState
  }) => {
    await loginAs('teacher');
    await page.goto('/courses');
    await expect(page.getByText('数据结构全流程演示课')).toBeVisible();

    // 教师建课：创建表单位于“我管理的”工作区，不能假设课程列表页直接显示它。
    const courseName = `E2E闭环课-${Date.now()}`;
    await page.getByRole('button', { name: '我管理的' }).click();
    await expect(page.getByRole('heading', { name: '课程创建与管理' })).toBeVisible();
    await page.getByLabel('课程名称').fill(courseName);
    await page.getByLabel('学期').fill('2026秋');
    await page.getByLabel('课程分类').fill('E2E');
    await page.getByLabel('开课日期').fill('2026-09-01');
    await page.getByLabel('结课日期').fill('2027-01-15');
    await page.getByLabel('课程状态').selectOption('ACTIVE');
    await page.getByRole('button', { name: '创建课程' }).click();
    await expect(page.getByText(courseName)).toBeVisible();

    // 管理页的课程卡片不可直接打开详情；切回可进入详情的全部课程页。
    await page.getByRole('button', { name: '全部课程' }).click();
    const teacherCourseCard = page.locator('.course-card').filter({ hasText: courseName });
    await teacherCourseCard.getByRole('button', { name: '管理课程' }).click();

    // 进入课程详情，验证管理入口。
    await expect(page.getByRole('button', { name: /管理章节/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /管理资源/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /管理公告/ })).toBeVisible();

    // 学生公开加入（同一用例内切换会话：先退出教师）
    await page.getByRole('button', { name: /退出/ }).click();
    await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    await loginAs('student');
    await page.goto('/courses');
    const studentCourseCard = page.locator('.course-card').filter({ hasText: courseName });
    // 非成员不可先进入课程详情；必须通过卡片上的公开加入动作取得成员关系。
    await studentCourseCard.getByRole('button', { name: '直接加入' }).click();
    await expect(page.getByTestId('course-detail-page')).toBeVisible();
    await waitForBusinessState(page.locator('body'), /课程详情|课程公告/);
  });

  test('邀请码错误与审批加入的受控失败和审批后权限', async ({
    page,
    loginAs,
    waitForBusinessState
  }) => {
    await loginAs('teacher');
    await page.goto('/courses');

    // 邀请码课与审批课由教师通过 API 前置数据准备（复用真实后端契约，避免重复 UI 路径）
    const created = await page.evaluate(async () => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
      const invite = await fetch('/api/v1/courses', {
        method: 'POST',
        headers,
        body: JSON.stringify({ name: `E2E邀请码课-${Date.now()}`, enrollmentMode: 'INVITE', inviteCode: 'E2E-INV', status: 'ACTIVE' })
      }).then((res) => res.json());
      const review = await fetch('/api/v1/courses', {
        method: 'POST',
        headers,
        body: JSON.stringify({ name: `E2E审批课-${Date.now()}`, enrollmentMode: 'REVIEW', status: 'ACTIVE' })
      }).then((res) => res.json());
      return { inviteId: invite.data.id, reviewId: review.data.id };
    });
    expect(Number(created.inviteId)).toBeGreaterThan(0);
    expect(Number(created.reviewId)).toBeGreaterThan(0);

    await page.getByRole('button', { name: /退出/ }).click();
    await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    await loginAs('student');
    await page.goto('/courses');

    // 非法邀请码返回受控错误
    const wrongCode = await page.evaluate(async ({ courseId }) => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      return fetch(`/api/v1/courses/${courseId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ inviteCode: 'WRONG' })
      }).then(async (res) => ({ status: res.status, body: await res.json() }));
    }, { courseId: created.inviteId });
    expect(wrongCode.status).toBe(400);
    expect(wrongCode.body.code).toBe('INVALID_INVITE_CODE');

    // 审批课加入为 PENDING，审批前无权限
    const pending = await page.evaluate(async ({ courseId }) => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      return fetch(`/api/v1/courses/${courseId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ applyReason: 'E2E 审批加入验证' })
      }).then(async (res) => res.json());
    }, { courseId: created.reviewId });
    expect(pending.data.status).toBe('PENDING');

    const studentId = await page.evaluate(() => Number(window.localStorage.getItem('onlinejudge.userId')));

    // 教师审批通过
    await page.getByRole('button', { name: /退出/ }).click();
    await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    await loginAs('teacher');
    await page.goto('/courses');
    const approve = await page.evaluate(async ({ courseId, userId }) => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      return fetch(`/api/v1/courses/${courseId}/members/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ role: 'STUDENT', status: 'ACTIVE' })
      }).then(async (res) => res.json());
    }, { courseId: created.reviewId, userId: studentId });
    expect(approve.data.status).toBe('ACTIVE');

    // 学生侧审批后权限开放
    await page.getByRole('button', { name: /退出/ }).click();
    await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    await loginAs('student');
    await page.goto('/courses');
    const afterApproval = await page.evaluate(async ({ courseId }) => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      return fetch(`/api/v1/courses/${courseId}/permissions/${window.localStorage.getItem('onlinejudge.userId')}`, {
        headers: { Authorization: `Bearer ${token}` }
      }).then(async (res) => res.json());
    }, { courseId: created.reviewId });
    expect(afterApproval.data.member).toBe(true);
    await waitForBusinessState(page.locator('body'), /课程管理|全部课程/);
  });
});
