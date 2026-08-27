import { test, expect } from '../fixtures';

const COURSE_ID = 9501;
const SOURCE_NAME = 'issue-265-source.py';
const REPORT_NAME = 'issue-265-report.pdf';
const lifecycleState: { labId: number; submissionId: number } = {
  labId: 0,
  submissionId: 0
};

test.describe.configure({ mode: 'serial' });

test('teacher and student complete the LAB publish, submission, review, release, and feedback lifecycle', async ({ page, loginAs, logout }) => {
  await loginAs('teacher');
  await page.goto(`/courses/${COURSE_ID}/labs/manage`);
  await page.getByTestId('create-lab').click();
  await expect(page.getByTestId('lab-editor-form')).toBeVisible();

  await page.locator('input[name="title"]').fill('Issue 265 LAB E2E lifecycle');
  await page.locator('textarea[name="description"]').fill('Exercises the LAB document and test closure through the real UI.');
  await page.locator('input[name="deadline"]').fill('2030-12-31T23:59');
  await page.getByTestId('language-python').check();
  await page.locator('select[name="evaluationMode"]').selectOption('MIXED');
  await page.locator('input[name="reportRequired"]').check();
  await page.locator('textarea[name="testcase-input-0"]').fill('2 3');
  await page.locator('textarea[name="testcase-output-0"]').fill('5');
  await page.locator('input[name="testcase-weight-0"]').fill('60');
  await page.getByRole('button', { name: '新增用例' }).click();
  await page.locator('textarea[name="testcase-input-1"]').fill('4 6');
  await page.locator('textarea[name="testcase-output-1"]').fill('10');
  await page.locator('input[name="testcase-weight-1"]').fill('40');
  await page.locator('input[name="testcase-public-1"]').uncheck();
  await page.getByRole('button', { name: '保存草稿' }).click();
  await expect(page.getByRole('status')).toContainText('草稿已保存');

  const labId = Number(page.url().match(/\/courses\/9501\/labs\/(\d+)\/edit/)?.[1]);
  expect(labId).toBeGreaterThan(0);

  await page.goto(`/courses/${COURSE_ID}/labs/manage`);
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByTestId(`publish-lab-${labId}`).first().click();
  await expect(page.locator('p.notice--success').filter({ hasText: '发布成功' })).toBeVisible();
  await logout();

  await loginAs('student');
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}`);
  await expect(page.getByTestId('lab-detail-page')).toBeVisible();
  await expect(page.getByText('用例 1')).toBeVisible();
  await expect(page.getByText('用例 2')).toHaveCount(0);
  await page.getByTestId('lab-submit-link').click();
  await expect(page.getByTestId('lab-submit-page')).toBeVisible();
  await page.locator('select[name="language"]').selectOption('python');
  await page.locator('input[name="file"]').setInputFiles({
    name: SOURCE_NAME,
    mimeType: 'text/x-python',
    buffer: Buffer.from('left, right = map(int, input().split())\nprint(left + right)\n')
  });

  const submissionResponsePromise = page.waitForResponse((response) => (
    response.request().method() === 'POST'
    && response.url().includes(`/api/v1/labs/${labId}/submissions`)
  ));
  await page.getByTestId('submit-lab-button').click();
  const submissionResponse = await submissionResponsePromise;
  expect(submissionResponse.ok()).toBe(true);
  const submissionPayload = await submissionResponse.json() as { data: { submissionId: number } };
  const submissionId = submissionPayload.data.submissionId;
  expect(submissionId).toBeGreaterThan(0);
  lifecycleState.labId = labId;
  lifecycleState.submissionId = submissionId;
  await expect(page.getByRole('status')).toContainText('提交成功');

  await page.locator('input[name="reportFile"]').setInputFiles({
    name: REPORT_NAME,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\nIssue 265 report\n%%EOF\n')
  });
  await page.getByRole('button', { name: '上传报告' }).click();
  await expect(page.getByText(/实验报告上传成功/)).toBeVisible();
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/submissions/${submissionId}/result`);
  await expect(page.getByTestId('evaluation-score')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('case-group-passed')).toContainText('用例 1');
  await expect(page.getByTestId('case-group-passed')).not.toContainText('用例 2');

  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/manage/submissions`);
  await expect(page).toHaveURL(/\/403(?:\?.*)?$/);
  const sourceDownloadStatus = await page.evaluate(async ({ requestedLabId, requestedSubmissionId }) => {
    const token = window.localStorage.getItem('onlinejudge.authToken');
    const response = await fetch(
      `/api/v1/labs/${requestedLabId}/submissions/${requestedSubmissionId}/source/download`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} }
    );
    return response.status;
  }, { requestedLabId: labId, requestedSubmissionId: submissionId });
  expect(sourceDownloadStatus).toBe(403);
  await page.goto('/courses');
  await logout();

  await loginAs('teacher');
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/manage/submissions/${submissionId}`);
  await expect(page.getByTestId('source-file-panel')).toBeVisible();
  const download = page.waitForEvent('download');
  await page.locator('[data-action="download-source-file"]').click();
  expect((await download).suggestedFilename()).toBe(SOURCE_NAME);

  const reportForm = page.locator('form[data-action="score-report"]');
  await reportForm.locator('input[name="reportScore"]').fill('15');
  await reportForm.locator('textarea[name="reportComment"]').fill('Report reviewed in issue 265 E2E.');
  await reportForm.getByRole('button', { name: '保存报告评分' }).click();
  await expect(page.getByTestId('report-score-feedback')).toContainText('保存');

  const scoreForm = page.locator('form[data-action="score-submission"]');
  await scoreForm.locator('input[name="manualScore"]').fill('90');
  await scoreForm.locator('input[name="finalScore"]').fill('95');
  await scoreForm.locator('textarea[name="scoreComment"]').fill('Submission reviewed in issue 265 E2E.');
  await scoreForm.getByRole('button', { name: '保存提交评分' }).click();
  await expect(page.getByTestId('submission-score-feedback')).toContainText('保存');

  await page.goto(`/courses/${COURSE_ID}/labs/manage`);
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByTestId(`close-lab-${labId}`).first().click();
  await expect(page.locator('p.notice--success').filter({ hasText: '已截止' })).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByTestId(`release-lab-${labId}`).first().click();
  await expect(page.locator('p.notice--success').filter({ hasText: '成绩发布成功' })).toBeVisible();
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/manage/statistics`);
  await expect(page.getByTestId('summary-submitted')).toContainText('1');
  await expect(page.getByTestId('summary-evaluated')).toContainText('1');
  await logout();

  await loginAs('student');
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/submissions/${submissionId}/result`);
  await expect(page.getByTestId('published-review')).toBeVisible();
  await expect(page.getByTestId('published-review')).toContainText('95');
  await expect(page.getByTestId('published-review')).toContainText('Submission reviewed in issue 265 E2E.');
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/submit`);
  await expect(page.getByTestId('lab-submit-blocked')).toContainText('实验提交阶段已结束');
});

test('student download permissions and teacher download failures remain observable', async ({ page, loginAs, logout }) => {
  const { labId, submissionId } = lifecycleState;
  expect(labId).toBeGreaterThan(0);
  expect(submissionId).toBeGreaterThan(0);

  await loginAs('student');
  // loginAs verifies credentials only; enter the authenticated shell before using its logout control.
  await page.goto('/courses');
  await expect(page.getByTestId('platform-navigation')).toBeVisible();
  const deniedDownload = await page.evaluate(async ({ requestedLabId, requestedSubmissionId }) => {
    const token = window.localStorage.getItem('onlinejudge.authToken');
    const response = await fetch(
      `/api/v1/labs/${requestedLabId}/submissions/${requestedSubmissionId}/source/download`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} }
    );
    return { status: response.status, body: await response.json().catch(() => null) };
  }, { requestedLabId: labId, requestedSubmissionId: submissionId });
  expect(deniedDownload.status).toBe(403);
  expect(deniedDownload.body?.code).toBe('ERR-AUTH-05');
  await logout();

  await loginAs('teacher');
  await page.goto(`/courses/${COURSE_ID}/labs/${labId}/manage/submissions/${submissionId}`);
  const sourceDownloadUrl = `**/api/v1/labs/${labId}/submissions/${submissionId}/source/download`;
  await page.route(sourceDownloadUrl, async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'LAB-500-01', message: '存储服务暂不可用', data: null })
    });
  });
  await page.locator('[data-action="download-source-file"]').click();
  await expect(page.getByTestId('source-file-download-error')).toBeVisible();
  await page.unroute(sourceDownloadUrl);
  const recoveredDownload = page.waitForEvent('download');
  await page.locator('[data-action="download-source-file"]').click();
  expect((await recoveredDownload).suggestedFilename()).toBe(SOURCE_NAME);
  await logout();
});

test('student receives a LAB notification tied to the published lifecycle', async ({ page, loginAs, logout }) => {
  const { labId } = lifecycleState;
  expect(labId).toBeGreaterThan(0);

  await loginAs('student');
  // API assertions do not render the shell; navigate before using its logout control.
  await page.goto('/courses');
  await expect(page.getByTestId('platform-navigation')).toBeVisible();
  let labNotification: { sourceModule: string; sourceId: number | null; actionUrl: string | null; title: string } | undefined;

  await expect.poll(async () => {
    const notificationPage = await page.evaluate(async () => {
      const token = window.localStorage.getItem('onlinejudge.authToken');
      const response = await fetch('/api/v1/notifications?page=1&size=100', {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      });
      const body = await response.json();
      return { status: response.status, data: body.data } as {
        status: number;
        data: { records: Array<{ sourceModule: string; sourceId: number | null; actionUrl: string | null; title: string }> };
      };
    });
    if (notificationPage.status !== 200) {
      return false;
    }
    labNotification = notificationPage.data.records.find((notification) => (
      notification.sourceModule === 'LAB' && notification.sourceId === labId
    ));
    return labNotification !== undefined;
  }, {
    message: 'wait for asynchronous LAB publication notification',
    timeout: 10_000,
    intervals: [100, 250, 500, 1_000]
  }).toBe(true);

  expect(labNotification?.sourceModule).toBe('LAB');
  expect(labNotification?.sourceId).toBe(labId);
  expect(labNotification?.actionUrl).toContain(`/courses/${COURSE_ID}/labs/${labId}`);
  expect(['实验已发布', '实验成绩已发布']).toContain(labNotification?.title);
  await logout();
});

test('teacher syncs the released LAB source score into GRD', async ({ page, loginAs, logout }) => {
  const { labId } = lifecycleState;
  expect(labId).toBeGreaterThan(0);

  await loginAs('teacher');
  // API assertions do not render the shell; navigate before using its logout control.
  await page.goto('/courses');
  await expect(page.getByTestId('platform-navigation')).toBeVisible();
  const gradeSync = await page.evaluate(async ({ courseId, requestedLabId }) => {
    const token = window.localStorage.getItem('onlinejudge.authToken');
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const gradeItemResponse = await fetch(`/api/v1/courses/${courseId}/grade-items`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        name: `Issue 265 LAB ${requestedLabId}`,
        sourceType: 'LAB',
        sourceId: requestedLabId,
        fullScore: 100,
        // Keep the source item eligible for sync without consuming the course weight budget.
        weight: 0,
        includedInFinal: true,
        sortOrder: 9999
      })
    });
    const gradeItemBody = await gradeItemResponse.json();
    const syncResponse = await fetch(`/api/v1/courses/${courseId}/grades/sync`, { method: 'POST', headers });
    const syncBody = await syncResponse.json();
    const gradesResponse = await fetch(`/api/v1/courses/${courseId}/grades?page=1&size=100`, { headers });
    const gradesBody = await gradesResponse.json();
    return {
      gradeItemStatus: gradeItemResponse.status,
      gradeItem: gradeItemBody.data,
      syncStatus: syncResponse.status,
      sync: syncBody.data,
      gradesStatus: gradesResponse.status,
      grades: gradesBody.data
    } as {
      gradeItemStatus: number;
      gradeItem: { sourceType: string; sourceId: number | null };
      syncStatus: number;
      sync: { syncedCount: number };
      gradesStatus: number;
      grades: { records: Array<{ studentId: number; records: Array<{ courseId: number; studentId: number; sourceType: string; sourceId: number | null; rawScore: string | null; gradeStatus: string }> }> };
    };
  }, { courseId: COURSE_ID, requestedLabId: labId });
  expect(gradeSync.gradeItemStatus).toBe(201);
  expect(gradeSync.gradeItem.sourceType).toBe('LAB');
  expect(gradeSync.gradeItem.sourceId).toBe(labId);
  expect(gradeSync.syncStatus).toBe(200);
  expect(gradeSync.sync.syncedCount).toBeGreaterThan(0);
  expect(gradeSync.gradesStatus).toBe(200);
  const labGradeRow = gradeSync.grades.records.find((row) => (
    row.records.some((record) => record.sourceType === 'LAB' && record.sourceId === labId)
  ));
  const labRecord = labGradeRow?.records.find((record) => (
    record.sourceType === 'LAB' && record.sourceId === labId
  ));
  expect(labGradeRow).toBeDefined();
  expect(labRecord).toBeDefined();
  expect(labRecord?.courseId).toBe(COURSE_ID);
  expect(labRecord?.studentId).toBe(labGradeRow?.studentId);
  expect(Number(labRecord?.rawScore)).toBe(95);
  expect(labRecord?.gradeStatus).toBe('SCORED');
  await logout();
});
