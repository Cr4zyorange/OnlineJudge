import { test, expect } from '../fixtures';

const COURSE_ID = 9501;
const SOURCE_NAME = 'issue-265-source.py';
const REPORT_NAME = 'issue-265-report.pdf';

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
