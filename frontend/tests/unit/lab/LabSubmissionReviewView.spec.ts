import { readFileSync } from 'node:fs';
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionReviewView from '../../../src/views/lab/LabSubmissionReviewView.vue';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type {
  LabExperimentDetail,
  LabReportSummary,
  LabScoreSummary,
  LabSubmissionDetail,
  LabSubmissionResult
} from '../../../src/types/lab';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

const useRouteMock = vi.hoisted(() => vi.fn());

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>();
  return { ...actual, useRoute: useRouteMock };
});
vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/lrn/learningProgress');

describe('LabSubmissionReviewView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useRouteMock.mockReturnValue({ query: {} });
    vi.mocked(labApi.getLabDetail).mockResolvedValue(lab());
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValue(submission());
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('loads one full review context with the effective version, Chinese statuses, code, and case results', async () => {
    const wrapper = mountView();
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 301);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 301);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.get('h1').text()).toContain('链表与队列实验');
    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('林晓');
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('当前评分依据');
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('最新提交');
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('最终版本');
    expect(wrapper.text()).toContain('已提交');
    expect(wrapper.text()).toContain('评测通过');
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain("print('review source')");
    expect(wrapper.get('[data-testid="evaluation-summary"]').text()).toContain('2 / 3');
    expect(wrapper.get('[data-testid="evaluation-case-1"]').text()).toContain('用例 1');
    expect(wrapper.get('[data-testid="evaluation-case-1"]').text()).toContain('通过');
    expect(wrapper.get('[data-testid="evaluation-case-2"]').text()).toContain('实际输出');
    expect(wrapper.get('[data-testid="evaluation-case-2"]').text()).toContain('4');
    expect(wrapper.text()).not.toContain('SUBMITTED');
    expect(wrapper.text()).not.toContain('ACCEPTED');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toContainEqual({
      name: 'lab-submission-workspace',
      params: { courseId: 101, labId: 7 },
      query: {}
    });
  });

  it('forwards only validated queue filters when returning from a review route', async () => {
    useRouteMock.mockReturnValue({
      query: {
        keyword: ['  周然  ', '忽略后续值'],
        status: 'LATE',
        evaluation: 'PENDING',
        overdue: '1',
        role: 'teacher',
        studentId: '601',
        redirect: 'https://example.test'
      }
    });
    const wrapper = mountView();
    await flushPromises();

    const workspaceLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'lab-submission-workspace');
    const workspaceTarget = routeTarget(workspaceLink?.props('to'));
    expect(workspaceTarget).toEqual({
      name: 'lab-submission-workspace',
      params: { courseId: 101, labId: 7 },
      query: {
        keyword: '周然',
        status: 'LATE',
        evaluation: 'PENDING',
        overdue: 'true'
      }
    });
    expect(workspaceTarget.query).not.toHaveProperty('role');
    expect(workspaceTarget.query).not.toHaveProperty('studentId');
    expect(workspaceTarget.query).not.toHaveProperty('redirect');
  });

  it('never exposes a source file identifier and explains the missing controlled teacher download', async () => {
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="source-file-blocker"]').text())
      .toContain('服务尚未提供教师受控下载入口');
    expect(wrapper.text()).not.toContain('private-file-token-should-never-render');
  });

  it('degrades only the student name when the roster service fails and does not leak a student id', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('学生姓名暂不可用');
    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('学生名单服务暂不可用');
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain("print('review source')");
    expect(wrapper.text()).not.toContain('601');
  });

  it('shows a recoverable page error and retries all required review data', async () => {
    vi.mocked(labApi.getLabSubmissionDetail)
      .mockRejectedValueOnce(new Error('提交详情服务暂不可用'))
      .mockResolvedValueOnce(submission());

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('提交详情服务暂不可用');
    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledTimes(2);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('林晓');
  });

  it('downloads and scores the attached report with recoverable feedback', async () => {
    const reportBlob = new Blob(['review report'], { type: 'application/pdf' });
    vi.mocked(labApi.downloadLabReport).mockResolvedValueOnce({
      blob: reportBlob,
      filename: 'review-report.pdf'
    });
    vi.mocked(labApi.scoreLabReport).mockResolvedValueOnce(report({ score: 18, comment: '论证完整' }));
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:review-report'),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-action="download-report"]').trigger('click');
    await flushPromises();
    expect(labApi.downloadLabReport).toHaveBeenCalledWith(7, 901);
    expect(URL.createObjectURL).toHaveBeenCalledWith(reportBlob);
    expect(wrapper.get('[data-testid="report-download-feedback"]').text()).toContain('已开始下载');

    await wrapper.get('[name="reportScore"]').setValue('18');
    await wrapper.get('[name="reportComment"]').setValue('论证完整');
    await wrapper.get('[data-action="score-report"]').trigger('submit');
    await flushPromises();

    expect(labApi.scoreLabReport).toHaveBeenCalledWith(7, 901, {
      score: 18,
      comment: '论证完整'
    });
    expect(wrapper.get('[data-testid="report-score-feedback"]').text()).toContain('报告评分已保存');
    expect(wrapper.get('[data-testid="report-score-current"]').text()).toContain('18');
  });

  it('keeps the saved submission-score baseline after report scoring and requires a change reason to persist the new report score', async () => {
    vi.mocked(labApi.scoreLabReport).mockResolvedValueOnce(report({ score: 18, comment: '重新批阅报告' }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reportScore"]').setValue('18');
    await wrapper.get('[name="reportComment"]').setValue('重新批阅报告');
    await wrapper.get('[data-action="score-report"]').trigger('submit');
    await flushPromises();
    expect(labApi.scoreLabReport).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-action="score-submission"]').trigger('submit');

    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="submission-score-error"]').text())
      .toContain('修改已评分记录时必须填写修改原因');
  });

  it('localizes the report file type without exposing its transport enum', async () => {
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(submission({
      latestReport: report({
        fileName: '实验报告.docx',
        fileType: 'DOCX'
      })
    }));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="report-file-type"]').text()).toBe('Word 文档');
    expect(wrapper.text()).not.toContain('DOCX');
  });

  it('enforces integer scores within the experiment maximum and requires a reason when changing a saved score', async () => {
    vi.mocked(labApi.scoreLabSubmission).mockResolvedValueOnce(score({
      manualScore: 90,
      finalScore: 96,
      comment: '复核后调整',
      hasChangeLogs: true
    }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="manualScore"]').setValue('88.5');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    expect(wrapper.get('[data-testid="submission-score-error"]').text()).toContain('人工评分必须是整数');
    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();

    await wrapper.get('[name="manualScore"]').setValue('90');
    await wrapper.get('[name="finalScore"]').setValue('101');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    expect(wrapper.get('[data-testid="submission-score-error"]').text()).toContain('最终得分不得超过 100 分');
    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();

    await wrapper.get('[name="finalScore"]').setValue('96');
    await wrapper.get('[name="scoreComment"]').setValue('复核后调整');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    expect(wrapper.get('[data-testid="submission-score-error"]').text()).toContain('修改已评分记录时必须填写修改原因');
    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();

    await wrapper.get('[name="changeReason"]').setValue('重新核对代码与报告');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    await flushPromises();

    expect(labApi.scoreLabSubmission).toHaveBeenCalledWith(7, 301, {
      manualScore: 90,
      reportScore: 15,
      finalScore: 96,
      comment: '复核后调整',
      changeReason: '重新核对代码与报告'
    });
    expect(wrapper.get('[data-testid="submission-score-feedback"]').text()).toContain('提交评分已保存');
  });

  it('requires confirmation, exposes pending state, and refreshes the same review after reevaluation', async () => {
    const evaluationRequest = deferred<LabSubmissionResult>();
    const confirmSpy = vi.spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);
    vi.mocked(labApi.evaluateLabSubmission).mockReturnValueOnce(evaluationRequest.promise);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(submission()).mockResolvedValueOnce(submission({
      evaluationStatus: 'PENDING',
      autoScore: null
    }));
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce(evaluation()).mockResolvedValueOnce(evaluation({
      evaluationStatus: 'PENDING',
      score: 0,
      passedCases: 0,
      totalCases: 0,
      message: '重评任务已建立',
      caseResults: []
    }));

    const wrapper = mountView();
    await flushPromises();
    const button = wrapper.get('[data-action="reevaluate-submission"]');

    await button.trigger('click');
    expect(labApi.evaluateLabSubmission).not.toHaveBeenCalled();

    await button.trigger('click');
    await flushPromises();
    expect(confirmSpy).toHaveBeenCalledWith(expect.stringContaining('林晓'));
    expect(confirmSpy).toHaveBeenCalledWith(expect.stringContaining('版本 3'));
    expect(button.attributes('disabled')).toBeDefined();
    expect(button.text()).toContain('正在提交重新评测');

    evaluationRequest.resolve(evaluation({ evaluationStatus: 'PENDING', caseResults: [] }));
    await flushPromises();

    expect(labApi.evaluateLabSubmission).toHaveBeenCalledWith(7, 301);
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledTimes(2);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="reevaluation-feedback"]').text()).toContain('已提交重新评测');
    expect(wrapper.text()).toContain('等待评测');
  });

  it('keeps the review content and retry action available when reevaluation fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(labApi.evaluateLabSubmission).mockRejectedValueOnce(new Error('重评服务暂不可用'));
    const wrapper = mountView();
    await flushPromises();

    const button = wrapper.get('[data-action="reevaluate-submission"]');
    await button.trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="reevaluation-error"]').text()).toContain('重评服务暂不可用');
    expect(button.attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain("print('review source')");
  });

  it('keeps the review canvas full width and collapses the two-column content on phones', () => {
    const source = readFileSync('src/views/lab/LabSubmissionReviewView.vue', 'utf8');

    expect(source).toMatch(/\.lab-submission-review\s*\{[\s\S]*?width:\s*100%/);
    expect(source).toMatch(/\.lab-submission-review\s*\{[\s\S]*?max-width:\s*none/);
    expect(source).toMatch(/@media\s*\(max-width:\s*760px\)/);
    expect(source).toMatch(
      /@media\s*\(max-width:\s*760px\)[\s\S]*?\.review-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/
    );
  });
});

function mountView() {
  return mount(LabSubmissionReviewView, {
    props: { courseId: 101, labId: 7, submissionId: 301 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function routeTarget(target: string | Record<string, unknown> | undefined) {
  return typeof target === 'object' && target !== null ? target : {};
}

function lab(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 12,
    title: '链表与队列实验',
    description: '完成链表与队列的基础实现。',
    status: 'PUBLISHED',
    deadline: '2026-08-25T23:59:59',
    maxScore: 100,
    evaluationMode: 'MIXED',
    autoEvaluate: true,
    reportRequired: true,
    publishedAt: '2026-08-19T08:00:00',
    deleted: false,
    attachmentIds: [],
    allowedLanguages: 'python,java',
    timeLimitMs: 1000,
    memoryLimitKb: 262144,
    testcases: [],
    ...overrides
  };
}

function submission(overrides: Partial<LabSubmissionDetail> = {}): LabSubmissionDetail {
  return {
    submissionId: 301,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 80,
    finalScore: 95,
    version: 3,
    submittedAt: '2026-08-19T09:30:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: true,
    code: "print('review source')",
    fileId: 'private-file-token-should-never-render',
    latestReport: report(),
    latestScore: score(),
    ...overrides
  };
}

function evaluation(overrides: Partial<LabSubmissionResult> = {}): LabSubmissionResult {
  return {
    submissionId: 301,
    evaluationStatus: 'ACCEPTED',
    score: 80,
    passedCases: 2,
    totalCases: 3,
    message: '公开用例通过 2 项',
    submittedAt: '2026-08-19T09:30:00',
    finishedAt: '2026-08-19T09:31:00',
    caseResults: [
      {
        testcaseId: 11,
        orderNum: 1,
        passed: true,
        score: 40,
        input: '1 2',
        expectedOutput: '3',
        actualOutput: '3',
        message: '通过'
      },
      {
        testcaseId: 12,
        orderNum: 2,
        passed: false,
        score: 0,
        input: '2 3',
        expectedOutput: '5',
        actualOutput: '4',
        message: '答案不一致'
      }
    ],
    ...overrides
  };
}

function report(overrides: Partial<LabReportSummary> = {}): LabReportSummary {
  return {
    reportId: 901,
    submissionId: 301,
    fileName: '实验报告.pdf',
    fileType: 'PDF',
    fileSize: 4096,
    version: 2,
    score: 15,
    comment: '结构完整',
    submittedAt: '2026-08-19T09:32:00',
    downloadUrl: '/reports/901',
    ...overrides
  };
}

function score(overrides: Partial<LabScoreSummary> = {}): LabScoreSummary {
  return {
    submissionId: 301,
    reportId: 901,
    autoScore: 80,
    reportScore: 15,
    manualScore: 88,
    finalScore: 95,
    comment: '完成良好',
    hasChangeLogs: false,
    scoredAt: '2026-08-19T10:00:00',
    updatedAt: '2026-08-19T10:00:00',
    ...overrides
  };
}

function courseProgress(): LearningCourseProgressAggregate {
  return {
    courseId: 101,
    courseName: '数据结构',
    studentCount: 1,
    averageProgressPercent: 92,
    students: [
      {
        studentId: 601,
        studentName: '林晓',
        progressPercent: 92,
        status: 'COMPLETED',
        updatedAt: '2026-08-19T09:35:00'
      }
    ]
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
