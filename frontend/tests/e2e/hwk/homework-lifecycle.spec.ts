import type { APIRequestContext, APIResponse, Page } from '@playwright/test';
import { expect, test } from '../fixtures';
import {
  captureIssue320RepresentativeScreenshot,
  recordIssue320Representative
} from '../issue320-representative-evidence';

const COURSE_ID = Number(process.env.E2E_COURSE_ID || 9501);
const CHAPTER_ID = Number(process.env.E2E_CHAPTER_ID || 950101);

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

interface NotificationPage {
  records: Array<{
    sourceModule: string;
    sourceId: number | null;
    title: string;
  }>;
}

interface GradeItem {
  id: number;
}

interface AuthUserRecord {
  id: number;
}

interface GradeRecord {
  gradeItemId: number;
  sourceType: string;
  sourceId: number | null;
  rawScore: number | string | null;
}

interface CourseGradeTablePage {
  records: Array<{
    studentId: number;
    records: GradeRecord[];
  }>;
}

interface GradeSyncResult {
  affectedItemCount: number;
  affectedStudentCount: number;
  syncedCount: number;
}

interface HomeworkRecord {
  id: number;
  questions?: Array<Record<string, unknown>>;
}

interface SubmissionRecord {
  submissionId: number;
  evaluationStatus: string;
  autoScore?: number | null;
}

interface EvaluationResult {
  submissionId: number;
  taskId: string;
  taskState: string;
  evaluationStatus: string;
  score: number | null;
}

interface AttachmentRecord {
  fileId: string;
}

test.describe('@hwk HWK 真实业务闭环', () => {
  test.describe.configure({ mode: 'serial' });

  test('教师创建发布作业后学生提交并获得已发布评语', async ({
    page,
    request,
    loginAs,
    logout
  }) => {
    const runId = Date.now();
    const title = `D2-HWK-E2E-${runId}`;
    const reviewComment = `E2E 批阅通过 ${runId}`;

    await loginAs('teacher');
    await page.goto(`/courses/${COURSE_ID}/homeworks/new`);
    await expect(page.getByTestId('homework-editor')).toBeVisible();

    await page.locator('input[name="title"]').fill(title);
    await page.locator('textarea[name="description"]').fill('验证教师发布、学生提交、批阅、LRN 通知与 GRD 成绩同步边界。');
    await page.locator('select[name="type"]').selectOption('TEXT');
    await page.locator('input[name="deadline"]').fill(futureLocalDateTime());
    await page.locator('input[name="totalScore"]').fill('100');
    await page.getByTestId('save-homework').click();

    await expect(page.getByText('草稿已保存，可返回作业管理继续发布。')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/courses/${COURSE_ID}/homeworks/\\d+/edit$`));
    const homeworkId = Number(page.url().match(/\/homeworks\/(\d+)\/edit$/)?.[1]);
    expect(homeworkId).toBeGreaterThan(0);

    await page.goto(`/courses/${COURSE_ID}/homeworks/manage`);
    const teacherRow = page.locator('tr').filter({ hasText: title });
    await expect(teacherRow).toBeVisible();
    await acceptNextConfirmation(page);
    await teacherRow.getByTestId(`publish-homework-${homeworkId}`).click();
    await expect(page.getByTestId('operation-feedback')).toContainText(`“${title}”发布成功`);

    await logout();
    await loginAs('student');
    const studentHeaders = await authorizationHeaders(page);
    const student = await apiData<AuthUserRecord>(await request.get('/api/v1/auth/me', {
      headers: studentHeaders
    }));
    await expect.poll(async () => {
      const publishedNotifications = await apiData<NotificationPage>(await request.get(
        '/api/v1/notifications?page=1&size=100',
        { headers: studentHeaders }
      ));
      return publishedNotifications.records.some((notification) => (
        notification.sourceModule === 'HWK'
          && notification.sourceId === homeworkId
          && notification.title === 'homework published'
      ));
    }, { intervals: [100, 250, 500, 1_000], timeout: 10_000 }).toBe(true);

    await page.goto(`/courses/${COURSE_ID}/homeworks/${homeworkId}/submit`);
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
    await page.locator('textarea[name="answerText"]').fill(`E2E 文本答案 ${runId}`);
    await page.getByTestId('homework-primary-submit').click();
    await expect(page.getByTestId('homework-latest-submission')).toContainText('提交状态：已提交');
    const receipt = await page.getByTestId('homework-latest-submission').innerText();
    const submissionId = Number(receipt.match(/提交编号\s+(\d+)/)?.[1]);
    expect(submissionId).toBeGreaterThan(0);

    await logout();
    await loginAs('teacher');
    await page.goto(`/courses/${COURSE_ID}/homeworks/${homeworkId}/manage/submissions/${submissionId}`);
    await expect(page.getByTestId('homework-submission-review')).toBeVisible();
    await page.locator('input[name="manualScore"]').fill('88');
    await page.locator('input[name="finalScore"]').fill('88');
    await page.locator('textarea[name="reviewReason"]').fill(reviewComment);
    await acceptNextConfirmation(page);
    await page.locator('form[data-action="save-review"] button[type="submit"]').click();
    await expect(page.getByTestId('review-feedback')).toContainText('批阅已保存');

    await page.goto(`/courses/${COURSE_ID}/homeworks/manage`);
    const publishedRow = page.locator('tr').filter({ hasText: title });
    await expect(publishedRow).toBeVisible();
    await acceptNextConfirmation(page);
    await publishedRow.getByTestId(`release-homework-${homeworkId}`).click();
    await expect(page.getByTestId('operation-feedback')).toContainText(`“${title}”成绩发布成功`);

    const teacherHeaders = await authorizationHeaders(page);
    let gradeItemId: number | null = null;
    try {
      const gradeItem = await apiData<GradeItem>(await request.post(
        `/api/v1/courses/${COURSE_ID}/grade-items`,
        {
          headers: teacherHeaders,
          data: {
            name: `E2E-HWK-${runId}`,
            sourceType: 'HWK',
            sourceId: homeworkId,
            fullScore: 100,
            weight: 0,
            includedInFinal: true,
            sortOrder: 999
          }
        }
      ));
      gradeItemId = gradeItem.id;

      let syncResult: GradeSyncResult | undefined;
      let projectedGradeTable: CourseGradeTablePage | undefined;
      await expect.poll(async () => {
        syncResult = await apiData<GradeSyncResult>(await request.post(
          `/api/v1/courses/${COURSE_ID}/grades/sync`,
          { headers: teacherHeaders }
        ));
        projectedGradeTable = await apiData<CourseGradeTablePage>(await request.get(
          `/api/v1/courses/${COURSE_ID}/grades?gradeItemId=${gradeItemId}&page=1&size=100`,
          { headers: teacherHeaders }
        ));
        const gradeRecord = projectedGradeTable.records
          .find((row) => row.studentId === student.id)?.records
          .find((record) => record.gradeItemId === gradeItemId);
        return Number(gradeRecord?.rawScore);
      }, { intervals: [100, 250, 500, 1_000], timeout: 15_000 }).toBe(88);
      const confirmedSync = syncResult!;
      expect(confirmedSync.affectedItemCount).toBeGreaterThan(0);
      expect(confirmedSync.affectedStudentCount).toBeGreaterThan(0);
      expect(confirmedSync.syncedCount).toBeGreaterThan(0);

      const gradeTable = projectedGradeTable!;
      const studentGradeRow = gradeTable.records.find((row) => row.studentId === student.id);
      const gradeRecord = studentGradeRow?.records.find((record) => record.gradeItemId === gradeItemId);
      expect(gradeRecord).toEqual(expect.objectContaining({
        gradeItemId,
        sourceType: 'HWK',
        sourceId: homeworkId
      }));
      expect(Number(gradeRecord?.rawScore)).toBe(88);
    } finally {
      if (gradeItemId !== null) {
        await request.delete(`/api/v1/grade-items/${gradeItemId}`, { headers: teacherHeaders });
      }
    }

    await logout();
    await loginAs('student');
    await page.goto(`/courses/${COURSE_ID}/homeworks/${homeworkId}/submissions/${submissionId}/result`);
    await expect(page.getByTestId('published-review')).toContainText('最终得分 88');
    await expect(page.getByTestId('published-review')).toContainText(reviewComment);

    const notificationHeaders = await authorizationHeaders(page);
    await expect.poll(async () => {
      const notifications = await apiData<NotificationPage>(await request.get(
        '/api/v1/notifications?page=1&size=100',
        { headers: notificationHeaders }
      ));
      return notifications.records.some((notification) => (
        notification.sourceModule === 'HWK'
          && notification.sourceId === homeworkId
          && notification.title === 'homework score published'
      ));
    }, { intervals: [100, 250, 500, 1_000], timeout: 15_000 }).toBe(true);
  });

  test('多类型提交验证代码后台评测并覆盖附件异常、过期、越权与重评', async ({
    page,
    request,
    loginAs,
    logout
  }) => {
    test.setTimeout(60_000);
    const runId = Date.now();

    await loginAs('teacher');
    const teacherHeaders = await authorizationHeaders(page);
    const objective = await createAndPublishHomework(request, teacherHeaders, objectivePayload(runId));
    const code = await createAndPublishHomework(request, teacherHeaders, codePayload(runId));
    const file = await createAndPublishHomework(request, teacherHeaders, filePayload(runId));
    const expiring = await createAndPublishHomework(
      request,
      teacherHeaders,
      textPayload(`D2-HWK-EXPIRED-${runId}`, localDateTimeFromNow(4_000))
    );

    await page.goto(`/courses/${COURSE_ID}/homeworks/manage`);
    await logout();
    await loginAs('student');
    const studentHeaders = await authorizationHeaders(page);

    const studentObjective = await apiData<HomeworkRecord>(await request.get(
      `/api/v1/homeworks/${objective.id}`,
      { headers: studentHeaders }
    ));
    expect(studentObjective.questions).not.toContainEqual(expect.objectContaining({ answerJson: expect.anything() }));

    const objectiveSubmission = await apiData<SubmissionRecord>(await request.post(
      `/api/v1/homeworks/${objective.id}/submissions`,
      { headers: studentHeaders, data: { answerJson: '{"q1":["2"],"q2":["true"]}' } }
    ));
    expect(objectiveSubmission.evaluationStatus).toBe('ACCEPTED');
    expect(objectiveSubmission.autoScore).toBe(100);

    const acceptedCodeResponse = await request.post(
      `/api/v1/homeworks/${code.id}/submissions`,
      {
        headers: studentHeaders,
        data: {
          codeText: 'left, right = map(int, input().split())\nprint(left + right)',
          language: 'python'
        }
      }
    );
    const acceptedCodeSubmission = await apiData<SubmissionRecord>(acceptedCodeResponse);
    expect(acceptedCodeSubmission.evaluationStatus).toBe('PENDING');
    const acceptedCodeResult = await waitForSubmissionTerminal(
      request,
      studentHeaders,
      acceptedCodeSubmission.submissionId
    );
    expect(acceptedCodeResult.evaluationStatus).toBe('ACCEPTED');
    expect(acceptedCodeResult.autoScore).toBe(100);
    const acceptedCodeEvaluationResponse = await request.get(
      `/api/v1/submissions/${acceptedCodeSubmission.submissionId}/evaluation`,
      { headers: studentHeaders }
    );
    const acceptedCodeEvaluation = await apiData<EvaluationResult>(acceptedCodeEvaluationResponse);
    expect(acceptedCodeEvaluation).toMatchObject({
      submissionId: acceptedCodeSubmission.submissionId,
      taskState: 'SUCCEEDED',
      evaluationStatus: 'ACCEPTED',
      score: 100
    });
    expect(acceptedCodeEvaluation.taskId).toMatch(/\S/);
    await page.goto(`/courses/${COURSE_ID}/homeworks/${code.id}/submissions/${acceptedCodeSubmission.submissionId}/result`);
    await expect(page.getByTestId('evaluation-score')).toContainText('100');
    const screenshot = await captureIssue320RepresentativeScreenshot(page, 'assessment-worker');
    recordIssue320Representative({
      group: 'ASSESSMENT-WORKER',
      proofId: `task-${acceptedCodeEvaluation.taskId}`,
      chain: {
        submit: {
          method: 'POST',
          path: `/api/v1/homeworks/${code.id}/submissions`,
          status: acceptedCodeResponse.status(),
          response: {
            submissionId: acceptedCodeSubmission.submissionId,
            evaluationStatus: acceptedCodeSubmission.evaluationStatus
          }
        },
        finalRead: {
          method: 'GET',
          path: `/api/v1/submissions/${acceptedCodeSubmission.submissionId}/evaluation`,
          status: acceptedCodeEvaluationResponse.status(),
          response: {
            submissionId: acceptedCodeEvaluation.submissionId,
            taskId: acceptedCodeEvaluation.taskId,
            taskState: acceptedCodeEvaluation.taskState,
            evaluationStatus: acceptedCodeEvaluation.evaluationStatus,
            score: acceptedCodeEvaluation.score
          }
        }
      },
      uiAssertion: {
        route: new URL(page.url()).pathname,
        selector: '[data-testid="evaluation-score"]',
        expected: 'student result page displays the terminal automatic score 100',
        screenshot,
        junitCase: '多类型提交验证代码后台评测并覆盖附件异常、过期、越权与重评'
      }
    });

    const failedCodeSubmission = await apiData<SubmissionRecord>(await request.post(
      `/api/v1/homeworks/${code.id}/submissions`,
      { headers: studentHeaders, data: { codeText: 'print(', language: 'python' } }
    ));
    expect(failedCodeSubmission.submissionId).toBeGreaterThan(acceptedCodeSubmission.submissionId);
    expect(failedCodeSubmission.evaluationStatus).toBe('PENDING');
    const failedCodeResult = await waitForSubmissionTerminal(
      request,
      studentHeaders,
      failedCodeSubmission.submissionId
    );
    expect(failedCodeResult.evaluationStatus).toBe('COMPILE_ERROR');
    expect(failedCodeResult.autoScore).toBe(0);

    const invalidAttachmentResponse = await request.post(
      `/api/v1/homeworks/${file.id}/attachments`,
      {
        headers: studentHeaders,
        multipart: {
          file: {
            name: 'disguised-answer.pdf',
            mimeType: 'application/pdf',
            buffer: Buffer.from('not-a-pdf')
          }
        }
      }
    );
    expect(invalidAttachmentResponse.status()).toBe(400);
    expect((await invalidAttachmentResponse.json()).code).toBe('HWK_4005');

    const attachment = await apiData<AttachmentRecord>(await request.post(
      `/api/v1/homeworks/${file.id}/attachments`,
      {
        headers: studentHeaders,
        multipart: {
          file: {
            name: 'answer.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from(`E2E attachment ${runId}`, 'utf8')
          }
        }
      }
    ));
    const fileSubmission = await apiData<SubmissionRecord>(await request.post(
      `/api/v1/homeworks/${file.id}/submissions`,
      { headers: studentHeaders, data: { fileIds: [attachment.fileId] } }
    ));
    expect(fileSubmission.submissionId).toBeGreaterThan(0);

    const forbiddenStatistics = await request.get(
      `/api/v1/homeworks/${objective.id}/statistics`,
      { headers: studentHeaders }
    );
    expect(forbiddenStatistics.status()).toBe(403);
    expect((await forbiddenStatistics.json()).code).toBe('HWK_4031');

    await expect.poll(() => Date.now(), { timeout: 10_000 }).toBeGreaterThanOrEqual(Date.now() + 4_200);
    const expiredSubmission = await request.post(
      `/api/v1/homeworks/${expiring.id}/submissions`,
      { headers: studentHeaders, data: { answerText: 'too late' } }
    );
    expect(expiredSubmission.status()).toBe(409);
    expect((await expiredSubmission.json()).code).toBe('HWK_4004');

    await page.goto(`/courses/${COURSE_ID}/homeworks`);
    await logout();
    await loginAs('teacher');
    const currentTeacherHeaders = await authorizationHeaders(page);
    const reevaluation = await apiData<{ evaluationStatus: string; reevaluation: boolean }>(await request.post(
      `/api/v1/submissions/${acceptedCodeSubmission.submissionId}/reevaluate`,
      { headers: currentTeacherHeaders, data: { reason: `E2E rejudge ${runId}` } }
    ));
    expect(reevaluation.reevaluation).toBe(true);
    expect(['ACCEPTED', 'SYSTEM_ERROR']).toContain(reevaluation.evaluationStatus);
  });
});

async function authorizationHeaders(page: Page) {
  const token = await page.evaluate(() => window.localStorage.getItem('onlinejudge.authToken'));
  expect(token).toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}

async function apiData<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), `${response.url()} returned ${response.status()}`).toBe(true);
  const body = await response.json() as ApiEnvelope<T> | T;
  if (!isApiEnvelope<T>(body)) {
    return body;
  }
  expect(body.code).toBe('0');
  return body.data;
}

function isApiEnvelope<T>(body: ApiEnvelope<T> | T): body is ApiEnvelope<T> {
  return typeof body === 'object' && body !== null && 'code' in body && 'data' in body;
}

async function waitForSubmissionTerminal(
  request: APIRequestContext,
  headers: Record<string, string>,
  submissionId: number
) {
  await expect.poll(async () => {
    const latest = await apiData<SubmissionRecord>(await request.get(
      `/api/v1/submissions/${submissionId}`,
      { headers }
    ));
    return latest.evaluationStatus;
  }, { timeout: 10_000 }).not.toMatch(/^(PENDING|RUNNING)$/);
  return apiData<SubmissionRecord>(await request.get(
    `/api/v1/submissions/${submissionId}`,
    { headers }
  ));
}

async function acceptNextConfirmation(page: Page) {
  page.once('dialog', async (dialog) => {
    expect(dialog.type()).toBe('confirm');
    await dialog.accept();
  });
}

function futureLocalDateTime() {
  return localDateTimeFromNow(7 * 24 * 60 * 60 * 1000).slice(0, 16);
}

function localDateTimeFromNow(offset: number) {
  return new Date(Date.now() + offset).toISOString();
}

async function createAndPublishHomework(
  request: APIRequestContext,
  headers: Record<string, string>,
  payload: Record<string, unknown>
) {
  const homework = await apiData<HomeworkRecord>(await request.post('/api/v1/homeworks', {
    headers,
    data: payload
  }));
  await apiData<HomeworkRecord>(await request.put(`/api/v1/homeworks/${homework.id}/publish`, { headers }));
  return homework;
}

function basePayload(title: string, type: 'TEXT' | 'OBJECTIVE' | 'CODE' | 'FILE', deadline = localDateTimeFromNow(86_400_000)) {
  return {
    courseId: COURSE_ID,
    chapterId: CHAPTER_ID,
    title,
    description: `E2E ${type} homework`,
    type,
    deadline,
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    questions: [],
    testCases: []
  };
}

function textPayload(title: string, deadline?: string) {
  return basePayload(title, 'TEXT', deadline);
}

function filePayload(runId: number) {
  return basePayload(`D2-HWK-FILE-${runId}`, 'FILE');
}

function objectivePayload(runId: number) {
  return {
    ...basePayload(`D2-HWK-OBJECTIVE-${runId}`, 'OBJECTIVE'),
    questions: [
      {
        questionType: 'SINGLE_CHOICE',
        stem: '1 + 1 = ?',
        optionsJson: '["1","2"]',
        answerJson: '["2"]',
        score: 40,
        sortOrder: 1
      },
      {
        questionType: 'JUDGE',
        stem: 'Java is statically typed.',
        optionsJson: '["true","false"]',
        answerJson: '["true"]',
        score: 60,
        sortOrder: 2
      }
    ]
  };
}

function codePayload(runId: number) {
  return {
    ...basePayload(`D2-HWK-CODE-${runId}`, 'CODE'),
    languageLimitJson: '["python"]',
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    outputCompareMode: 'EXACT',
    testCases: [
      {
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }
    ]
  };
}
