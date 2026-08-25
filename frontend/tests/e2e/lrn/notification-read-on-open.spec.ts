import type { APIResponse, Page } from '@playwright/test';
import { expect, test } from '../fixtures';

const DEMO_COURSE_ID = 9501;

type ApiEnvelope<T> = { code: string; message: string; data: T };
type CreatedEntity = { id: number };
type NotificationRecord = {
  notificationId: number;
  sourceModule: string;
  sourceId: number;
  isRead: boolean;
};
type NotificationPage = {
  records: NotificationRecord[];
  unreadCount: number;
};

test.describe('@lrn #269 notification read-on-open', () => {
  test('marks a real LAB notification read before navigating and keeps repeated opens idempotent', async ({
    page,
    loginAs,
    logout
  }) => {
    await loginAs('teacher');
    const teacherHeaders = await authHeaders(page);
    const suffix = `${Date.now()}-${test.info().workerIndex}`;
    const lab = await apiData<CreatedEntity>(await page.request.post(
      `/api/v1/courses/${DEMO_COURSE_ID}/labs`,
      { headers: teacherHeaders, data: labPayload(`Issue 269 LAB ${suffix}`) }
    ));
    await expectOk(await page.request.post(`/api/v1/labs/${lab.id}/publish`, {
      headers: teacherHeaders
    }));
    await page.goto('/courses');
    await logout();

    await loginAs('student');
    const studentHeaders = await authHeaders(page);
    const beforeOpen = await notificationPage(page, studentHeaders);
    const notice = findNotice(beforeOpen, lab.id);
    expect(notice.isRead).toBe(false);

    let readMutationCount = 0;
    page.on('request', (request) => {
      if (request.method() === 'PUT' && request.url().includes('/api/v1/notifications/read')) {
        readMutationCount += 1;
      }
    });

    await page.goto('/notifications');
    const card = page.getByTestId(`notification-card-${notice.notificationId}`);
    await expect(card).toContainText(`Issue 269 LAB ${suffix}`);
    await card.getByRole('link', { name: '查看详情' }).click();

    await expect(page).toHaveURL(new RegExp(`/courses/${DEMO_COURSE_ID}/labs/${lab.id}$`));
    await expect(page.getByTestId('lab-detail-page')).toContainText(`Issue 269 LAB ${suffix}`);

    const afterOpen = await notificationPage(page, studentHeaders);
    expect(findNotice(afterOpen, lab.id).isRead).toBe(true);
    expect(afterOpen.unreadCount).toBe(beforeOpen.unreadCount - 1);
    expect(readMutationCount).toBe(1);

    await page.goto('/notifications');
    const readCard = page.getByTestId(`notification-card-${notice.notificationId}`);
    await expect(readCard).not.toContainText('未读');
    await page.screenshot({
      path: '../output/test/issue-262/evidence/notification-read-state-after-fix.png',
      fullPage: true
    });
    await readCard.getByRole('link', { name: '查看详情' }).click();
    await expect(page).toHaveURL(new RegExp(`/courses/${DEMO_COURSE_ID}/labs/${lab.id}$`));
    expect(readMutationCount).toBe(1);

    await page.screenshot({
      path: '../output/test/issue-262/evidence/notification-read-on-open-after-fix.png',
      fullPage: true
    });
  });
});

async function notificationPage(page: Page, headers: Record<string, string>) {
  return apiData<NotificationPage>(await page.request.get('/api/v1/notifications?size=100', { headers }));
}

async function apiData<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), `${response.url()} returned ${response.status()}`).toBe(true);
  const envelope = await response.json() as ApiEnvelope<T>;
  expect(envelope.code).toBe('0');
  return envelope.data;
}

async function expectOk(response: APIResponse) {
  await apiData<unknown>(response);
}

function findNotice(page: NotificationPage, sourceId: number) {
  const notice = page.records.find((record) => record.sourceModule === 'LAB' && record.sourceId === sourceId);
  expect(notice, `missing LAB notification for source ${sourceId}`).toBeDefined();
  return notice!;
}

function labPayload(title: string) {
  return {
    title,
    description: 'Issue 269 notification read-on-open browser acceptance',
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
