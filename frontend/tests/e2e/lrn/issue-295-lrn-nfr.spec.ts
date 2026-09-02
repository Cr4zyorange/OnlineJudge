import type { APIRequestContext, APIResponse, Page, TestInfo } from '@playwright/test';
import { expect, test } from '@playwright/test';
import { timingSafeEqual } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
};

type AuthSession = {
  token: string;
  user: { id: number; username: string };
};

type NotificationPage = {
  records: Array<{
    notificationId: number;
    sourceModule: string;
    sourceId: number;
    title: string;
    actionUrl: string | null;
    isRead: boolean;
  }>;
  unreadCount: number;
};

const studentAccount = process.env.E2E_STUDENT_ACCOUNT?.trim() || 'student001';
const studentPassword = process.env.E2E_STUDENT_PASSWORD || 'Student001@pass';
const teacherAccount = process.env.E2E_TEACHER_ACCOUNT?.trim() || 'teacher001';
const teacherPassword = process.env.E2E_TEACHER_PASSWORD || 'Teacher001@pass';
const disposableProof = verifyDisposableProof();

test.describe('@lrn NFR-LN-01 and NFR-LN-02', () => {
  test.skip(!disposableProof, 'Run only through npm run test:e2e:lrn:disposable.');

  test('measures a fresh 200-task path and automatically refreshes real LAB/HWK/GRD notifications', async ({ page, request }, testInfo) => {
    test.setTimeout(180_000);
    const marker = `lrn295-${Date.now()}-${testInfo.workerIndex}`;
    const teacher = await login(request, teacherAccount, teacherPassword);
    const student = await login(request, studentAccount, studentPassword);
    const teacherHeaders = bearer(teacher.token);
    const studentHeaders = bearer(student.token);
    const courseId = await createCourse(request, teacherHeaders, marker);
    await ok(await request.post(`/api/v1/courses/${courseId}/join`, { headers: studentHeaders }), 'join isolated student');
    const initialUnreadCount = (await ok<NotificationPage>(await request.get('/api/v1/notifications?page=1&size=1', {
      headers: studentHeaders
    }), 'read initial notification baseline')).unreadCount;

    const homeworkIds: number[] = [];
    for (let index = 1; index <= 200; index += 1) {
      const homeworkId = await createHomework(request, teacherHeaders, courseId, `${marker}-task-${index}`);
      homeworkIds.push(homeworkId);
      await ok(await request.put(`/api/v1/homeworks/${homeworkId}/publish`, { headers: teacherHeaders }), `publish task ${index}`);
    }

    await expect.poll(async () => {
      const tasks = await ok<{ total: number }>(await request.get(`/api/v1/learning/tasks?courseId=${courseId}&page=1&size=20`, {
        headers: studentHeaders
      }), 'check fresh-course task total');
      return tasks.total;
    }, { timeout: 10_000, intervals: [100, 250, 500, 1_000] }).toBe(200);

    const browserTaskTotal = (await ok<{ total: number }>(await request.get('/api/v1/learning/tasks?page=1&size=20', {
      headers: studentHeaders
    }), 'read browser task total')).total;
    expect(browserTaskTotal).toBeGreaterThanOrEqual(200);

    await expect.poll(async () => {
      const notifications = await ok<NotificationPage>(await request.get('/api/v1/notifications?page=1&size=1', {
        headers: studentHeaders
      }), 'wait for fresh task notifications');
      return notifications.unreadCount;
    }, { timeout: 10_000, intervals: [100, 250, 500, 1_000] }).toBe(initialUnreadCount + 200);

    await loginThroughBrowser(page);
    const taskFirstPaintStartedAt = performance.now();
    await page.goto('/learning/tasks');
    await expect(page.locator('.task-card')).toHaveCount(20);
    const taskFirstPaintMs = performance.now() - taskFirstPaintStartedAt;
    expect(taskFirstPaintMs).toBeLessThanOrEqual(1_500);

    const scrollSamplesMs: number[] = [];
    const loadedTargets = Array.from(
      { length: Math.ceil((browserTaskTotal - 20) / 20) },
      (_, index) => Math.min(40 + (index * 20), browserTaskTotal)
    );
    for (const loadedCount of loadedTargets) {
      const scrollStartedAt = performance.now();
      await page.getByTestId('task-load-more-sentinel').scrollIntoViewIfNeeded();
      await expect(page.getByTestId('loaded-task-count')).toContainText(`${loadedCount} / ${browserTaskTotal}`);
      const elapsed = performance.now() - scrollStartedAt;
      scrollSamplesMs.push(elapsed);
      expect(elapsed).toBeLessThanOrEqual(1_000);
    }

    const notificationFirstPaintStartedAt = performance.now();
    await page.goto('/notifications');
    await expect(page.locator('.notification-card')).toHaveCount(20);
    const notificationFirstPaintMs = performance.now() - notificationFirstPaintStartedAt;
    expect(notificationFirstPaintMs).toBeLessThanOrEqual(1_000);

    const batchReadStartedAt = performance.now();
    await page.getByTestId('mark-all-read').click();
    await expect(page.getByTestId('notification-center-unread-count')).toHaveText('未读 0');
    const batchReadMs = performance.now() - batchReadStartedAt;
    expect(batchReadMs).toBeLessThanOrEqual(500);

    const labId = await createLab(request, teacherHeaders, courseId, marker);
    await ok(await request.post(`/api/v1/labs/${labId}/publish`, { headers: teacherHeaders }), 'publish LAB notification source');
    const labSubmission = await ok<{ submissionId: string }>(await request.post(`/api/v1/labs/${labId}/submissions`, {
      headers: studentHeaders,
      multipart: {
        code: 'print("Issue 295")',
        language: 'python'
      }
    }), 'submit LAB notification source');
    await ok(await request.post(`/api/v1/labs/${labId}/submissions/${labSubmission.submissionId}/score`, {
      headers: teacherHeaders,
      data: {
        manualScore: 95,
        finalScore: 95,
        comment: 'Issue #295 LRN source event',
        changeReason: 'real LAB to GRD notification chain'
      }
    }), 'score LAB notification source');
    await ok(await request.put(`/api/v1/labs/${labId}/release-scores`, { headers: teacherHeaders }), 'release LAB score');
    await ok(await request.post(`/api/v1/courses/${courseId}/grade-items`, {
      headers: teacherHeaders,
      data: {
        name: `LAB grade ${marker}`,
        sourceType: 'LAB',
        sourceId: labId,
        fullScore: 100,
        weight: 1,
        includedInFinal: true,
        sortOrder: 1
      }
    }), 'create GRD source item');
    await ok(await request.post(`/api/v1/courses/${courseId}/grades/sync`, { headers: teacherHeaders }), 'sync real LAB source into GRD');
    const gradePublication = await ok<{ publishId: number; publishedCount: number; notificationStatus: string }>(
      await request.post(`/api/v1/courses/${courseId}/grades/publish`, {
        headers: teacherHeaders,
        data: { publishScope: 'PARTIAL_STUDENTS', studentIds: [student.user.id], gradeItemIds: [] }
      }),
      'publish real GRD source notification'
    );
    expect(gradePublication).toMatchObject({ publishedCount: 1, notificationStatus: 'SENT' });

    let labNotificationId: number | undefined;
    let gradeNotificationId: number | undefined;
    let crossModuleUnreadCount = 0;
    await expect.poll(async () => {
      const notifications = await ok<NotificationPage>(await request.get('/api/v1/notifications?page=1&size=100', {
        headers: studentHeaders
      }), 'wait for fresh LAB and GRD notifications');
      const labNotifications = notifications.records.filter((notification) => (
        notification.sourceModule === 'LAB'
        && notification.sourceId === labId
        && notification.actionUrl?.includes(`/courses/${courseId}/labs/${labId}`)
      ));
      const labNotification = labNotifications[0];
      const gradeNotification = notifications.records.find((notification) => (
        notification.sourceModule === 'GRD'
        && notification.sourceId === gradePublication.publishId
      ));
      labNotificationId = labNotification?.notificationId;
      gradeNotificationId = gradeNotification?.notificationId;
      crossModuleUnreadCount = labNotifications.filter((notification) => !notification.isRead).length
        + (gradeNotification && !gradeNotification.isRead ? 1 : 0);
      return labNotification !== undefined && gradeNotification !== undefined;
    }, { timeout: 10_000, intervals: [100, 250, 500, 1_000] }).toBe(true);
    expect(labNotificationId).toBeDefined();
    expect(gradeNotificationId).toBeDefined();
    expect(crossModuleUnreadCount).toBeGreaterThanOrEqual(2);
    await expect(page.getByTestId('platform-nav-unread-badge')).toHaveText(String(crossModuleUnreadCount), { timeout: 1_000 });
    await expect(page.getByTestId(`notification-card-${labNotificationId}`)).toBeVisible({ timeout: 1_000 });
    await expect(page.getByTestId(`notification-card-${gradeNotificationId}`)).toBeVisible({ timeout: 1_000 });
    await page.getByTestId('mark-all-read').click();
    await expect(page.getByTestId('notification-center-unread-count')).toHaveText('未读 0');

    const progress = await page.evaluate(async ({ courseId, sourceId }) => {
      const startedAt = performance.now();
      const token = window.localStorage.getItem('onlinejudge.authToken');
      const response = await fetch('/api/v1/learning/progress', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
          courseId,
          sourceModule: 'HWK',
          sourceId,
          progressPercent: 50,
          lastPosition: 'issue-295-performance-measurement'
        })
      });
      return {
        elapsedMs: performance.now() - startedAt,
        status: response.status,
        body: await response.json()
      };
    }, { courseId, sourceId: homeworkIds[0] });
    expect(progress.status).toBe(200);
    expect(progress.body.code).toBe('0');
    expect(progress.elapsedMs).toBeLessThanOrEqual(1_000);

    const notificationHomeworkId = await createHomework(request, teacherHeaders, courseId, `自动刷新通知 ${marker}`);
    const publishStartedAt = performance.now();
    await ok(await request.put(`/api/v1/homeworks/${notificationHomeworkId}/publish`, { headers: teacherHeaders }), 'publish notification source');
    let freshNotificationId: number | undefined;
    await expect.poll(async () => {
      const notifications = await ok<NotificationPage>(await request.get('/api/v1/notifications?page=1&size=100', {
        headers: studentHeaders
      }), 'wait for fresh HWK notification');
      const freshNotification = notifications.records.find((notification) => (
        notification.sourceModule === 'HWK'
        && notification.sourceId === notificationHomeworkId
        && notification.actionUrl?.includes(`/courses/${courseId}/homeworks/${notificationHomeworkId}`)
      ));
      freshNotificationId = freshNotification?.notificationId;
      return freshNotification !== undefined;
    }, { timeout: 3_000, intervals: [100, 250, 500, 1_000] }).toBe(true);
    expect(freshNotificationId).toBeDefined();
    const notificationPersistMs = performance.now() - publishStartedAt;
    expect(notificationPersistMs).toBeLessThanOrEqual(3_000);

    const browserRefreshStartedAt = performance.now();
    await expect(page.getByTestId('platform-nav-unread-badge')).toHaveText('1', { timeout: 1_000 });
    await expect(page.getByTestId(`notification-card-${freshNotificationId}`)).toBeVisible({ timeout: 1_000 });
    const browserRefreshMs = performance.now() - browserRefreshStartedAt;
    expect(browserRefreshMs).toBeLessThanOrEqual(1_000);

    writeEvidence({
      marker,
      courseId,
      browserTaskTotal,
      homeworkCount: homeworkIds.length,
      labId,
      labNotificationId,
      gradePublicationId: gradePublication.publishId,
      gradeNotificationId,
      crossModuleUnreadCount,
      notificationHomeworkId,
      testSummary: {
        total: 1,
        passed: 1,
        failed: 0,
        skipped: 0
      },
      thresholds: {
        taskFirstPaintMs: 1_500,
        notificationFirstPaintMs: 1_000,
        batchReadMs: 500,
        progressSaveMs: 1_000,
        notificationRefreshMs: 1_000,
        scrollFrameMs: 1_000
      },
      measurements: {
        taskFirstPaintMs,
        notificationFirstPaintMs,
        batchReadMs,
        progressSaveMs: progress.elapsedMs,
        notificationPersistMs,
        browserRefreshMs,
        scrollSamplesMs
      }
    });
  });
});

async function loginThroughBrowser(page: Page) {
  await page.goto('/login');
  await page.locator('input[name="account"]').fill(studentAccount);
  await page.locator('input[name="password"]').fill(studentPassword);
  await page.locator('form[data-auth-form="login"] button[type="submit"]').click();
  await expect(page.locator('.auth-feedback.success')).toHaveText('登录成功');
}

async function createLab(
  request: APIRequestContext,
  headers: Record<string, string>,
  courseId: number,
  marker: string
) {
  const lab = await ok<{ id: number }>(await request.post(`/api/v1/courses/${courseId}/labs`, {
    headers,
    data: {
      title: `LAB notification ${marker}`,
      description: 'Fresh Issue #295 LAB event consumed by GRD.',
      deadline: '2030-12-31T23:59:00',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'MANUAL',
      autoEvaluate: false,
      reportRequired: false,
      timeLimitMs: 1_000,
      memoryLimitKb: 65_536,
      testcases: []
    }
  }), 'create LAB notification source');
  return lab.id;
}

async function createCourse(request: APIRequestContext, headers: Record<string, string>, marker: string) {
  const course = await ok<{ id: number }>(await request.post('/api/v1/courses', {
    headers,
    data: {
      name: `LRN NFR ${marker}`,
      description: 'Issue #295 isolated browser-performance acceptance data',
      semester: '2026-D2',
      category: 'E2E',
      enrollmentMode: 'PUBLIC',
      maxStudents: 5,
      status: 'ACTIVE'
    }
  }), 'create isolated course');
  return course.id;
}

async function createHomework(
  request: APIRequestContext,
  headers: Record<string, string>,
  courseId: number,
  title: string
) {
  const homework = await ok<{ id: number }>(await request.post('/api/v1/homeworks', {
    headers,
    data: {
      courseId,
      title,
      description: 'Fresh issue #295 E2E source event',
      type: 'TEXT',
      deadline: '2030-12-31T23:59:00',
      totalScore: 100,
      allowResubmit: true,
      allowLateSubmit: false,
      showEvaluationBeforePublish: false,
      questions: [],
      testCases: []
    }
  }), `create homework ${title}`);
  return homework.id;
}

async function login(request: APIRequestContext, account: string, password: string) {
  return ok<AuthSession>(await request.post('/api/v1/auth/login', {
    data: { account, password }
  }), `login ${account}`);
}

function bearer(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function ok<T>(response: APIResponse, label: string): Promise<T> {
  const body = await response.json() as ApiEnvelope<T>;
  expect(response.status(), `${label}: ${JSON.stringify(body)}`).toBeGreaterThanOrEqual(200);
  expect(response.status(), `${label}: ${JSON.stringify(body)}`).toBeLessThan(300);
  expect(body.code, `${label}: ${body.message}`).toBe('0');
  return body.data;
}

function verifyDisposableProof() {
  const proofFile = process.env.E2E_LRN_DISPOSABLE_PROOF_FILE?.trim();
  const suppliedToken = process.env.E2E_LRN_DISPOSABLE_TOKEN?.trim();
  const baseUrl = process.env.E2E_BASE_URL?.trim();
  if (!proofFile || !suppliedToken || !baseUrl || !/^http:\/\/127\.0\.0\.1:\d+$/.test(baseUrl)) {
    return false;
  }
  try {
    const [storedToken, storedBaseUrl] = readFileSync(proofFile, 'utf8').trim().split('\n');
    const suppliedBytes = Buffer.from(suppliedToken, 'utf8');
    const storedBytes = Buffer.from(storedToken || '', 'utf8');
    return storedBytes.length === suppliedBytes.length
      && timingSafeEqual(storedBytes, suppliedBytes)
      && storedBaseUrl === baseUrl;
  } catch {
    return false;
  }
}

function writeEvidence(payload: Record<string, unknown>) {
  const evidenceFile = process.env.E2E_LRN_EVIDENCE_FILE?.trim();
  if (!evidenceFile) {
    throw new Error('E2E_LRN_EVIDENCE_FILE must be configured by the disposable runner');
  }
  writeFileSync(evidenceFile, `${JSON.stringify({
    status: 'PASS',
    baseSha: process.env.E2E_LRN_BASE_SHA,
    testedHeadSha: process.env.E2E_LRN_TESTED_HEAD_SHA,
    environment: process.env.E2E_BASE_URL,
    generatedAt: new Date().toISOString(),
    ...payload
  }, null, 2)}\n`, { mode: 0o600 });
}
