import type { APIResponse, Page } from '@playwright/test';
import { expect, test } from '../fixtures';
import { verifyLrnDisposableProof } from './disposable-proof';

const DEMO_COURSE_ID = 9501;
const DEMO_GRADE_SUMMARY_ID = 950421;
const hasDisposableProof = verifyLrnDisposableProof();

test.describe.configure({ timeout: 60_000 });
test.use({ navigationTimeout: 30_000 });

type ApiEnvelope<T> = { code: string; message: string; data: T };
type CurrentUser = { id: number };
type CreatedEntity = { id: number };
type NotificationRecord = {
  notificationId: number;
  sourceModule: 'LAB' | 'HWK' | 'GRD' | string;
  sourceId: number;
  isRead: boolean;
  actionUrl?: string | null;
};
type NotificationPage = { records: NotificationRecord[]; unreadCount: number; total: number };

test.describe('@lrn UC-LRN-01 business closure', () => {
  test.skip(!hasDisposableProof, 'Mutating LRN closure must run through npm run test:e2e:lrn:disposable');

  test('real LAB/HWK/GRD changes create isolated notifications with auditable lifecycle', async ({
    page,
    loginAs,
    logout
  }) => {
    await loginAs('student');
    const firstStudentHeaders = await authHeaders(page);
    const student = await apiData<CurrentUser>(await page.request.get('/api/v1/auth/me', {
      headers: firstStudentHeaders
    }));
    const beforeBusinessEvents = await notificationPage(page, firstStudentHeaders);
    const beforeGradeNotificationIds = new Set(beforeBusinessEvents.records
      .filter((notice) => notice.sourceModule === 'GRD' && notice.sourceId === DEMO_GRADE_SUMMARY_ID)
      .map((notice) => notice.notificationId));

    const forbidden = await page.request.post(`/api/v1/courses/${DEMO_COURSE_ID}/labs`, {
      headers: firstStudentHeaders,
      data: labPayload('student must not create this lab')
    });
    expect(forbidden.status()).toBe(403);
    await page.goto('/courses');
    await logout();

    await loginAs('teacher');
    const teacherHeaders = await authHeaders(page);
    const suffix = `${Date.now()}-${test.info().workerIndex}`;
    const lab = await apiData<CreatedEntity>(await page.request.post(
      `/api/v1/courses/${DEMO_COURSE_ID}/labs`,
      { headers: teacherHeaders, data: labPayload(`Issue 262 LAB ${suffix}`) }
    ));
    await expectOk(await page.request.post(`/api/v1/labs/${lab.id}/publish`, { headers: teacherHeaders }));

    const homework = await apiData<CreatedEntity>(await page.request.post('/api/v1/homeworks', {
      headers: teacherHeaders,
      data: homeworkPayload(`Issue 262 HWK ${suffix}`)
    }));
    await expectOk(await page.request.put(`/api/v1/homeworks/${homework.id}/publish`, { headers: teacherHeaders }));

    await expectOk(await page.request.put(`/api/v1/course-grade-summaries/${DEMO_GRADE_SUMMARY_ID}/adjust`, {
      headers: teacherHeaders,
      data: { newScore: '89.60', reason: `Issue 262 E2E ${suffix}` }
    }));
    await page.goto('/courses');
    await logout();

    await loginAs('student');
    const studentHeaders = await authHeaders(page);
    const list = await notificationPage(page, studentHeaders);
    const labNotice = findNotice(list, 'LAB', lab.id);
    const homeworkNotice = findNotice(list, 'HWK', homework.id);
    const gradeNotice = findNewNotice(list, 'GRD', DEMO_GRADE_SUMMARY_ID, beforeGradeNotificationIds);
    expect([labNotice, homeworkNotice, gradeNotice]).toHaveLength(3);
    expect([labNotice, homeworkNotice, gradeNotice].every((notice) => !notice.isRead)).toBe(true);

    await page.goto('/notifications');
    const labCard = page.getByTestId(`notification-card-${labNotice.notificationId}`);
    await expect(labCard).toContainText('Issue 262 LAB');
    await page.screenshot({
      path: test.info().outputPath('notification-before-valid-jump.png'),
      fullPage: true
    });
    await labCard.getByRole('link', { name: '查看详情' }).click();
    await expect(page).toHaveURL(new RegExp(`/courses/${DEMO_COURSE_ID}/labs/${lab.id}`));
    await expect(page.getByTestId('lab-detail-page')).toContainText(`Issue 262 LAB ${suffix}`);
    await page.screenshot({
      path: test.info().outputPath('valid-jump-target.png'),
      fullPage: true
    });

    // UC-LRN-01 requires opening a valid target to mark the notification as read.
    const afterJump = await notificationPage(page, studentHeaders);
    expect(findNotice(afterJump, 'LAB', lab.id).isRead).toBe(true);

    await page.goto('/notifications');
    await page.getByTestId(`notification-select-${homeworkNotice.notificationId}`).check();
    await page.getByTestId('mark-selected-read').click();
    await expect(page.getByTestId(`notification-card-${homeworkNotice.notificationId}`)).not.toContainText('未读');
    await page.getByTestId(`delete-notification-${homeworkNotice.notificationId}`).click();
    await expect(page.getByTestId(`notification-card-${homeworkNotice.notificationId}`)).toHaveCount(0);

    const finalList = await notificationPage(page, studentHeaders);
    expect(finalList.records.some((notice) => notice.notificationId === homeworkNotice.notificationId)).toBe(false);
    expect(finalList.records.some((notice) => notice.notificationId === gradeNotice.notificationId)).toBe(true);
    expect(student.id).toBeGreaterThan(0);
  });

  test('student opens every LRN page and saves reminder preferences on seeded real data', async ({
    page,
    loginAs
  }) => {
    await loginAs('student');

    await page.goto('/learning/tasks');
    await expect(page.getByRole('heading', { name: '学习任务中心' })).toBeVisible();
    await expect(page.locator('.task-card').first()).toBeVisible();
    await page.locator('select[name="taskType"]').selectOption('EXPERIMENT');
    await expect(page.locator('.task-card').first()).toContainText('实验');

    await page.getByTestId('learning-progress-entry').click();
    await expect(page.getByRole('heading', { name: '我的课程进度' })).toBeVisible();
    await expect(page.getByRole('link', { name: '继续学习' }).first()).toBeVisible();

    await page.goto('/learning/statistics');
    await expect(page.getByRole('heading', { name: '我的学习趋势' })).toBeVisible();
    await expect(page.getByRole('region', { name: '行为统计' })).toBeVisible();

    await page.goto('/learning/reminders');
    await expect(page.getByRole('heading', { name: '截止提醒与通知偏好' })).toBeVisible();
    await expect(page.getByTestId('save-reminder-rules')).toBeEnabled();
    await page.getByTestId('save-reminder-rules').click();
    await expect(page.getByText('提醒规则已保存')).toBeVisible();
  });

  test('notification list reports a short disconnection and refreshes after recovery', async ({ page, loginAs }) => {
    await loginAs('student');
    let interrupted = false;
    await page.route('**/api/v1/notifications**', async (route) => {
      const requestUrl = new URL(route.request().url());
      if (!interrupted && requestUrl.searchParams.get('size') === '20') {
        interrupted = true;
        await route.abort('internetdisconnected');
        return;
      }
      await route.continue();
    });

    await page.goto('/notifications');
    await expect(page.getByText('通知加载失败')).toBeVisible();
    await page.getByRole('button', { name: '重试' }).click();
    await expect(page.getByRole('heading', { name: '我的通知' })).toBeVisible();
    await expect(page.getByText(/未读 \d+/)).toBeVisible();
  });
});

async function apiData<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), `${response.url()} returned ${response.status()}`).toBe(true);
  const envelope = await response.json() as ApiEnvelope<T>;
  expect(envelope.code).toBe('0');
  return envelope.data;
}

async function expectOk(response: APIResponse) {
  await apiData<unknown>(response);
}

async function notificationPage(page: Page, headers: Record<string, string>) {
  return apiData<NotificationPage>(await page.request.get('/api/v1/notifications?size=100', { headers }));
}

function findNotice(page: NotificationPage, sourceModule: string, sourceId: number) {
  const notice = page.records.find((record) => record.sourceModule === sourceModule && record.sourceId === sourceId);
  expect(notice, `missing ${sourceModule} notification for source ${sourceId}`).toBeDefined();
  return notice!;
}

function findNewNotice(
  page: NotificationPage,
  sourceModule: string,
  sourceId: number,
  previousNotificationIds: Set<number>
) {
  const notice = page.records.find((record) => (
    record.sourceModule === sourceModule
      && record.sourceId === sourceId
      && !previousNotificationIds.has(record.notificationId)
  ));
  expect(notice, `missing new ${sourceModule} notification for source ${sourceId}`).toBeDefined();
  return notice!;
}

function labPayload(title: string) {
  return {
    title,
    description: 'Issue 262 real cross-module browser acceptance',
    deadline: futureDeadline(),
    maxScore: 100,
    attachmentIds: [],
    allowedLanguages: 'java,python',
    evaluationMode: 'DOCKER_IO',
    autoEvaluate: true,
    reportRequired: false,
    timeLimitMs: 60000,
    memoryLimitKb: 262144,
    testcases: []
  };
}

function homeworkPayload(title: string) {
  return {
    courseId: DEMO_COURSE_ID,
    title,
    description: 'Issue 262 real cross-module browser acceptance',
    type: 'TEXT',
    deadline: futureDeadline(),
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    questions: [],
    testCases: []
  };
}

function futureDeadline() {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + 30);
  return date.toISOString().replace(/\.\d{3}Z$/, '');
}

async function authHeaders(page: Page) {
  const token = await page.evaluate(() => window.localStorage.getItem('onlinejudge.authToken'));
  expect(token).toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}
