import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionResultView from '../../../src/views/lab/LabSubmissionResultView.vue';
import * as labApi from '../../../src/api/lab/labs';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
import type {
  LabExperimentDetail,
  LabResult,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionResult
} from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');

describe('LabSubmissionResultView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useRealTimers();
    resetRuntimeContext();
    currentUser.value = {
      id: 601,
      username: 'lab-student',
      displayName: '实验学生',
      userType: 'STUDENT',
      roles: ['STUDENT'],
      permissions: []
    };
  });

  afterEach(() => {
    vi.useRealTimers();
    resetRuntimeContext();
  });

  it('resolves the latest own submission, loads the aggregate result, and hides unpublished scoring', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({
        submissionId: 501,
        version: 3,
        submittedAt: '2026-08-18T11:00:00',
        isLatest: false,
        isFinal: false,
        isScoringBasis: false
      }),
      submission({
        submissionId: 502,
        version: 4,
        submittedAt: '2026-08-18T10:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true
      })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      status: 'PUBLISHED',
      submission: submissionDetail({
        submissionId: 502,
        version: 4,
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        latestScore: score({
          finalScore: 98,
          reportScore: 18,
          comment: '这条评语在发布前不得显示'
        })
      }),
      evaluationResult: evaluation({ submissionId: 502 }),
      latestReport: report({ score: 18, comment: '报告评语在发布前不得显示' }),
      latestScore: score({
        finalScore: 98,
        reportScore: 18,
        comment: '这条评语在发布前不得显示'
      })
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7);
    expect(labApi.getLabResult).toHaveBeenCalledWith(7, 601);
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('版本 4');
    expect(wrapper.get('[data-testid="result-hidden"]').text()).toContain('成绩尚未发布');
    expect(wrapper.text()).not.toContain('最终得分 98');
    expect(wrapper.text()).not.toContain('报告评分 18');
    expect(wrapper.text()).not.toContain('这条评语在发布前不得显示');
    expect(wrapper.text()).not.toContain('报告评语在发布前不得显示');
  });

  it('shows published scores and feedback with passed and failed public cases grouped separately', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'SCORE_PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 610, evaluationStatus: 'WRONG_ANSWER' })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      status: 'SCORE_PUBLISHED',
      submission: submissionDetail({
        submissionId: 610,
        evaluationStatus: 'WRONG_ANSWER',
        finalScore: 94,
        latestScore: score({
          finalScore: 94,
          manualScore: 14,
          reportScore: 20,
          comment: '边界处理清晰，请再补一个空输入用例。'
        })
      }),
      evaluationResult: evaluation({
        submissionId: 610,
        evaluationStatus: 'WRONG_ANSWER',
        score: 80,
        passedCases: 2,
        totalCases: 3,
        message: '公开用例中有一项未通过。',
        caseResults: [
          caseResult({ testcaseId: 701, orderNum: 1, passed: true, score: 40, message: '基础用例通过' }),
          caseResult({ testcaseId: 702, orderNum: 2, passed: false, score: 0, message: '空输入处理不正确' }),
          caseResult({ testcaseId: 703, orderNum: 3, passed: true, score: 40, message: '大数用例通过' })
        ]
      }),
      latestReport: report({ score: 20, comment: '报告结构完整。' }),
      latestScore: score({
        finalScore: 94,
        manualScore: 14,
        reportScore: 20,
        comment: '边界处理清晰，请再补一个空输入用例。'
      }),
      publishedAt: '2026-08-18T12:00:00'
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('最终得分 94');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('报告评分 20');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('边界处理清晰');
    expect(wrapper.text()).toContain('公开用例中有一项未通过。');
    expect(wrapper.get('[data-testid="case-group-passed"]').text()).toContain('基础用例通过');
    expect(wrapper.get('[data-testid="case-group-passed"]').text()).toContain('大数用例通过');
    expect(wrapper.get('[data-testid="case-group-failed"]').text()).toContain('空输入处理不正确');
  });

  it.each(['SCORE_PUBLISHED', 'ARCHIVED'] as const)(
    'shows an explicit ungraded review when %s experiment scores are published without a score',
    async (status) => {
      vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status }));
      vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
        submission({ finalScore: null })
      ]);
      vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
        status,
        submission: submissionDetail({ finalScore: null, latestScore: null }),
        latestReport: null,
        latestScore: null
      }));

      const wrapper = mountResult();
      await flushPromises();

      expect(wrapper.find('[data-testid="result-hidden"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="published-review-empty"]').text())
        .toContain('成绩已发布，当前提交暂无评分');
      expect(wrapper.text()).not.toContain('成绩尚未发布');
      expect(wrapper.text()).not.toContain('待发布');
    }
  );

  it('loads an explicitly selected historic submission and localizes statuses without exposing internal ids', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'SCORE_PUBLISHED' }));
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(submissionDetail({
      submissionId: 987654,
      studentId: 601,
      version: 1,
      evaluationStatus: 'WRONG_ANSWER',
      isLatest: false,
      isFinal: false,
      isScoringBasis: false
    }));
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce(evaluation({
      submissionId: 987654,
      evaluationStatus: 'WRONG_ANSWER',
      score: 40,
      passedCases: 1,
      totalCases: 2,
      message: '第 2 个公开用例输出不匹配。',
      caseResults: [
        caseResult({
          testcaseId: 818181,
          orderNum: 2,
          passed: false,
          score: 0,
          message: '请检查空数组分支。'
        })
      ]
    }));

    const wrapper = mountResult({ submissionId: 987654 });
    await flushPromises();

    expect(labApi.listLabSubmissions).not.toHaveBeenCalled();
    expect(labApi.getLabResult).not.toHaveBeenCalled();
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 987654);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 987654);
    expect(wrapper.text()).toContain('版本 1');
    expect(wrapper.text()).toContain('答案错误');
    expect(wrapper.text()).toContain('请检查空数组分支。');
    expect(wrapper.text()).not.toContain('WRONG_ANSWER');
    expect(wrapper.text()).not.toContain('SCORE_PUBLISHED');
    expect(wrapper.text()).not.toContain('987654');
    expect(wrapper.text()).not.toContain('601');
    expect(wrapper.text()).not.toContain('818181');
    expect(wrapper.text()).not.toContain('submissionId');
    expect(wrapper.text()).not.toContain('testcaseId');
    expect(wrapper.text()).not.toContain('studentId');
  });

  it('rejects a historic submission that belongs to another student', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'SCORE_PUBLISHED' }));
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(submissionDetail({
      submissionId: 987655,
      studentId: 602,
      version: 1
    }));
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce(evaluation({
      submissionId: 987655,
      message: '不应展示的他人评测结果'
    }));

    const wrapper = mountResult({ submissionId: 987655 });
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('返回的实验结果与当前提交不匹配');
    expect(wrapper.text()).not.toContain('不应展示的他人评测结果');
  });

  it('polls a pending evaluation until it becomes terminal and then stops polling', async () => {
    vi.useFakeTimers();
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 620, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      status: 'PUBLISHED',
      submission: submissionDetail({ submissionId: 620, evaluationStatus: 'PENDING' }),
      evaluationResult: evaluation({ submissionId: 620, evaluationStatus: 'PENDING' })
    }));
    vi.mocked(labApi.getLabSubmissionResult)
      .mockResolvedValueOnce(evaluation({ submissionId: 620, evaluationStatus: 'RUNNING' }))
      .mockResolvedValueOnce(evaluation({
        submissionId: 620,
        evaluationStatus: 'ACCEPTED',
        score: 100,
        passedCases: 2,
        totalCases: 2,
        message: '全部公开用例通过。'
      }));

    const wrapper = mountResult();
    await flushPromises();
    expect(wrapper.text()).toContain('等待评测');
    expect(labApi.getLabSubmissionResult).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('评测中');

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('通过');

    await vi.advanceTimersByTimeAsync(10_000);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(2);

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(2);
  });

  it('stops pending evaluation polling at the 60 second boundary', async () => {
    vi.useFakeTimers();
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 621, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      status: 'PUBLISHED',
      submission: submissionDetail({ submissionId: 621, evaluationStatus: 'PENDING' }),
      evaluationResult: evaluation({ submissionId: 621, evaluationStatus: 'PENDING' })
    }));
    vi.mocked(labApi.getLabSubmissionResult).mockImplementation(() => new Promise((resolve) => {
      setTimeout(() => {
        resolve(evaluation({ submissionId: 621, evaluationStatus: 'PENDING' }));
      }, 400);
    }));

    const wrapper = mountResult();
    await flushPromises();

    await vi.advanceTimersByTimeAsync(60_000);
    await flushPromises();
    const callsAtBoundary = vi.mocked(labApi.getLabSubmissionResult).mock.calls.length;
    expect(wrapper.text()).toContain('自动刷新已暂停');

    await vi.advanceTimersByTimeAsync(10_000);
    await flushPromises();
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(callsAtBoundary);
  });

  it('times out a never-settling poll request and ignores its late response', async () => {
    vi.useFakeTimers();
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail({ status: 'PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 623, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      status: 'PUBLISHED',
      submission: submissionDetail({ submissionId: 623, evaluationStatus: 'PENDING' }),
      evaluationResult: evaluation({ submissionId: 623, evaluationStatus: 'PENDING' })
    }));
    const pendingEvaluation = deferred<LabSubmissionResult>();
    vi.mocked(labApi.getLabSubmissionResult).mockReturnValueOnce(pendingEvaluation.promise);

    const wrapper = mountResult();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(60_000);
    await flushPromises();

    expect(wrapper.text()).toContain('自动刷新已暂停');
    expect(wrapper.text()).toContain('手动刷新');

    pendingEvaluation.resolve(evaluation({
      submissionId: 623,
      evaluationStatus: 'ACCEPTED',
      message: '迟到的结果不应覆盖超时状态'
    }));
    await flushPromises();

    expect(wrapper.text()).not.toContain('迟到的结果不应覆盖超时状态');
    expect(wrapper.text()).toContain('自动刷新已暂停');
  });

  it('rejects an aggregate response whose evaluation belongs to another submission', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 622 })
    ]);
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce(aggregateResult({
      submission: submissionDetail({ submissionId: 622 }),
      evaluationResult: evaluation({ submissionId: 999 })
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('返回的实验结果与当前提交不匹配');
  });

  it.each([
    ['another experiment', { labId: 8 }],
    ['another student', { studentId: 602 }]
  ])('rejects every latest-history item belonging to %s before requesting an aggregate result', async (_case, mismatch) => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 622, version: 2 }),
      submission({ submissionId: 621, version: 1, isLatest: false, ...mismatch })
    ]);

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('提交历史与当前实验或学生不匹配');
    expect(labApi.getLabResult).not.toHaveBeenCalled();
  });

  it('shows an empty state when the latest-result route has no submission history', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(labDetail());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('empty');
    expect(wrapper.text()).toContain('还没有可查看的实验结果');
    expect(labApi.getLabResult).not.toHaveBeenCalled();
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
  });

  it('offers a retry action after a recoverable page-load failure', async () => {
    vi.mocked(labApi.getLabDetail)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce(labDetail());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('网络暂时不可用');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('empty');
  });
});

function mountResult(extraProps: { submissionId?: number } = {}) {
  return mount(LabSubmissionResultView, {
    props: {
      courseId: 101,
      labId: 7,
      ...extraProps
    },
    global: {
      stubs: {
        RouterLink: RouterLinkStub
      }
    }
  });
}

function labDetail(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 1001,
    title: '链表与边界条件实验',
    description: '完成链表实现并通过公开测试。',
    status: 'PUBLISHED',
    deadline: '2026-08-18T23:59:59',
    maxScore: 100,
    evaluationMode: 'DOCKER_IO',
    autoEvaluate: true,
    reportRequired: true,
    publishedAt: '2026-08-17T09:00:00',
    attachmentIds: [],
    allowedLanguages: 'java,python',
    timeLimitMs: 60_000,
    memoryLimitKb: 262_144,
    testcases: [],
    deleted: false,
    ...overrides
  };
}

function submission(overrides: Partial<LabSubmissionHistoryItem> = {}): LabSubmissionHistoryItem {
  return {
    submissionId: 500,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 80,
    finalScore: null,
    version: 2,
    submittedAt: '2026-08-18T10:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: false,
    ...overrides
  };
}

function submissionDetail(overrides: Partial<LabSubmissionDetail> = {}): LabSubmissionDetail {
  const base = submission(overrides);
  return {
    ...base,
    code: 'print("lab")',
    fileId: null,
    latestReport: null,
    latestScore: null,
    ...overrides
  };
}

function evaluation(overrides: Partial<LabSubmissionResult> = {}): LabSubmissionResult {
  return {
    submissionId: 500,
    evaluationStatus: 'ACCEPTED',
    score: 80,
    passedCases: 2,
    totalCases: 2,
    message: '全部公开用例通过。',
    caseResults: [],
    submittedAt: '2026-08-18T10:00:00',
    finishedAt: '2026-08-18T10:00:02',
    ...overrides
  };
}

function caseResult(overrides: Partial<LabSubmissionResult['caseResults'][number]> = {}) {
  return {
    testcaseId: 700,
    orderNum: 1,
    passed: true,
    score: 40,
    input: '1 2',
    expectedOutput: '3',
    actualOutput: '3',
    message: '通过',
    ...overrides
  };
}

function report(overrides: Partial<NonNullable<LabResult['latestReport']>> = {}): NonNullable<LabResult['latestReport']> {
  return {
    reportId: 810,
    submissionId: 500,
    fileName: 'lab-report.pdf',
    fileType: 'PDF',
    fileSize: 2048,
    version: 1,
    score: 20,
    comment: '报告结构完整。',
    submittedAt: '2026-08-18T10:05:00',
    downloadUrl: '/api/v1/labs/7/reports/810/download',
    ...overrides
  };
}

function score(overrides: Partial<NonNullable<LabResult['latestScore']>> = {}): NonNullable<LabResult['latestScore']> {
  return {
    submissionId: 500,
    reportId: 810,
    autoScore: 80,
    reportScore: 20,
    manualScore: 14,
    finalScore: 94,
    comment: '边界处理清晰。',
    hasChangeLogs: false,
    scoredAt: '2026-08-18T11:00:00',
    updatedAt: '2026-08-18T11:00:00',
    ...overrides
  };
}

function aggregateResult(overrides: Partial<LabResult> = {}): LabResult {
  return {
    labId: 7,
    studentId: 601,
    status: 'PUBLISHED',
    submission: submissionDetail(),
    evaluationResult: evaluation(),
    latestReport: null,
    latestScore: null,
    publishedAt: null,
    ...overrides
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
