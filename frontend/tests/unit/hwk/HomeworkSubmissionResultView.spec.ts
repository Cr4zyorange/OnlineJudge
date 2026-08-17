import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionResultView from '../../../src/views/hwk/HomeworkSubmissionResultView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkSubmissionDetail,
  HomeworkSubmissionSummary
} from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkSubmissionResultView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useRealTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('selects the latest submission by submitted time and id and shows published scoring details', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      status: 'SCORE_PUBLISHED',
      showEvaluationBeforePublish: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 101, version: 1, submittedAt: '2026-08-17T09:00:00' }),
      submission({ submissionId: 102, version: 2, submittedAt: '2026-08-17T10:00:00' }),
      submission({ submissionId: 103, version: 3, submittedAt: '2026-08-17T10:00:00' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 103,
      version: 3,
      submittedAt: '2026-08-17T10:00:00',
      finalScore: 96,
      manualScore: 6,
      comment: '边界条件处理完整。'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      submissionId: 103,
      evaluationStatus: 'ACCEPTED',
      score: 90,
      passedCases: 8,
      totalCases: 8,
      feedback: '全部公开与隐藏用例通过。'
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(11);
    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenCalledWith(11);
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(103);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(103);
    expect(wrapper.text()).toContain('数据结构第三次作业');
    expect(wrapper.text()).toContain('通过');
    expect(wrapper.get('[data-testid="evaluation-score"]').text()).toContain('90');
    expect(wrapper.text()).toContain('8 / 8');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('最终得分 96');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('边界条件处理完整。');
    expect(wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))).toEqual(expect.arrayContaining([
      '/courses/101/homeworks/11',
      '/courses/101/homeworks/11/submissions'
    ]));
  });

  it('treats version as the authoritative latest-submission order before time and id', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({
        submissionId: 999,
        version: 2,
        submittedAt: '2026-08-17T12:00:00'
      }),
      submission({
        submissionId: 50,
        version: 3,
        submittedAt: '2026-08-17T09:00:00'
      })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 50,
      version: 3,
      submittedAt: '2026-08-17T09:00:00'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      submissionId: 50
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(50);
    expect(wrapper.text()).toContain('版本 3');
  });

  it('does not request or reveal evaluation and final review before the visibility policy permits it', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      status: 'PUBLISHED',
      showEvaluationBeforePublish: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 91, evaluationStatus: 'ACCEPTED', finalScore: 100, comment: '不应显示' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 91,
      evaluationStatus: 'ACCEPTED',
      finalScore: 100,
      comment: '不应显示'
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="result-hidden"]').text()).toContain('评测结果尚未发布');
    expect(wrapper.text()).not.toContain('最终得分 100');
    expect(wrapper.text()).not.toContain('不应显示');
  });

  it('keeps evaluation and published review visible after the homework is archived', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      status: 'ARCHIVED',
      showEvaluationBeforePublish: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 104, evaluationStatus: 'ACCEPTED' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 104,
      evaluationStatus: 'ACCEPTED',
      finalScore: 97,
      manualScore: 7,
      comment: '归档后仍可查看的评语'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      submissionId: 104,
      evaluationStatus: 'ACCEPTED',
      score: 90
    }));

    const wrapper = mountResult();
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(104);
    expect(wrapper.get('[data-testid="evaluation-score"]').text()).toContain('90');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('最终得分 97');
    expect(wrapper.get('[data-testid="published-review"]').text()).toContain('归档后仍可查看的评语');
  });

  it('loads an explicitly selected historic submission without replacing it with the latest version', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 55,
      version: 1,
      evaluationStatus: 'WRONG_ANSWER'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      submissionId: 55,
      evaluationStatus: 'WRONG_ANSWER',
      score: 40,
      passedCases: 2,
      totalCases: 5,
      errorMessage: '第 3 个用例输出不匹配',
      feedback: '检查空输入。',
      runLog: 'expected 0, received 1'
    }));

    const wrapper = mountResult({ submissionId: 55 });
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).not.toHaveBeenCalled();
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(55);
    expect(wrapper.text()).toContain('版本 1');
    expect(wrapper.text()).toContain('第 3 个用例输出不匹配');
    expect(wrapper.text()).toContain('检查空输入。');
    expect(wrapper.text()).toContain('expected 0, received 1');
  });

  it('reloads the result when navigation reuses the component for another submission', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homeworkDetail());
    vi.mocked(homeworkApi.getHomeworkSubmission)
      .mockResolvedValueOnce(submissionDetail({ submissionId: 55, version: 1 }))
      .mockResolvedValueOnce(submissionDetail({ submissionId: 56, version: 2 }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation)
      .mockResolvedValueOnce(evaluation({ submissionId: 55 }))
      .mockResolvedValueOnce(evaluation({ submissionId: 56 }));

    const wrapper = mountResult({ submissionId: 55 });
    await flushPromises();
    expect(wrapper.text()).toContain('版本 1');

    await wrapper.setProps({ submissionId: 56 });
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenLastCalledWith(56);
    expect(wrapper.text()).toContain('版本 2');
  });

  it('shows an empty state when the latest-result route has no submissions', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('empty');
    expect(wrapper.text()).toContain('还没有可查看的提交结果');
    expect(homeworkApi.getHomeworkSubmission).not.toHaveBeenCalled();
  });

  it('retries a failed page load and renders forbidden failures explicitly', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);

    const retryable = mountResult();
    await flushPromises();
    expect(retryable.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');

    await retryable.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledTimes(2);
    expect(retryable.get('[data-testid="page-state"]').attributes('data-state')).toBe('empty');

    vi.resetAllMocks();
    vi.mocked(homeworkApi.getHomeworkDetail).mockRejectedValueOnce(new Error('无权限访问该作业'));
    const forbidden = mountResult();
    await flushPromises();
    expect(forbidden.get('[data-testid="page-state"]').attributes('data-state')).toBe('forbidden');
  });

  it.each([
    'course access denied',
    'permission denied'
  ])('classifies the English access failure "%s" as forbidden', async (message) => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockRejectedValueOnce(new Error(message));

    const wrapper = mountResult();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('forbidden');
    expect(wrapper.find('[data-testid="page-state-retry"]').exists()).toBe(false);
  });

  it('polls pending evaluations until a terminal result and stops after the component unmounts', async () => {
    vi.useFakeTimers();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 88, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 88,
      evaluationStatus: 'PENDING'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation)
      .mockResolvedValueOnce(evaluation({ submissionId: 88, evaluationStatus: 'PENDING' }))
      .mockResolvedValueOnce(evaluation({ submissionId: 88, evaluationStatus: 'RUNNING' }))
      .mockResolvedValueOnce(evaluation({
        submissionId: 88,
        evaluationStatus: 'ACCEPTED',
        score: 100,
        passedCases: 5,
        totalCases: 5
      }));

    const wrapper = mountResult();
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('等待评测');

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('评测中');

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(3);
    expect(wrapper.text()).toContain('通过');

    await vi.advanceTimersByTimeAsync(10_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(3);

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(3);
  });

  it('does not let an older automatic-poll response overwrite a newer manual terminal result', async () => {
    vi.useFakeTimers();
    const delayedPoll = deferred<HomeworkEvaluationResult>();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 87, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 87,
      evaluationStatus: 'PENDING'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation)
      .mockResolvedValueOnce(evaluation({ submissionId: 87, evaluationStatus: 'PENDING' }))
      .mockImplementationOnce(() => delayedPoll.promise)
      .mockResolvedValueOnce(evaluation({
        submissionId: 87,
        evaluationStatus: 'ACCEPTED',
        score: 100,
        passedCases: 5,
        totalCases: 5
      }));

    const wrapper = mountResult();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(2);

    await wrapper.get('.homework-result__refresh-row button').trigger('click');
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(3);
    expect(wrapper.get('[data-testid="evaluation-score"]').text()).toContain('100');

    delayedPoll.resolve(evaluation({
      submissionId: 87,
      evaluationStatus: 'RUNNING',
      score: 0,
      passedCases: 0,
      totalCases: 5
    }));
    await flushPromises();

    expect(wrapper.get('[data-testid="evaluation-score"]').text()).toContain('100');
    await vi.advanceTimersByTimeAsync(5_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(3);
  });

  it('cancels a scheduled pending-result poll when leaving the page', async () => {
    vi.useFakeTimers();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 89, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 89,
      evaluationStatus: 'PENDING'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValue(
      evaluation({ submissionId: 89, evaluationStatus: 'PENDING' })
    );

    const wrapper = mountResult();
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(1);
  });

  it('pauses automatic polling after three network failures and allows a manual recovery', async () => {
    vi.useFakeTimers();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 90, evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submissionDetail({
      submissionId: 90,
      evaluationStatus: 'PENDING'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation)
      .mockResolvedValueOnce(evaluation({ submissionId: 90, evaluationStatus: 'PENDING' }))
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockResolvedValueOnce(evaluation({
        submissionId: 90,
        evaluationStatus: 'ACCEPTED',
        score: 100
      }));

    const wrapper = mountResult();
    await flushPromises();
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await vi.advanceTimersByTimeAsync(1_000);
      await flushPromises();
    }

    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(4);
    expect(wrapper.text()).toContain('自动重试已暂停');
    await vi.advanceTimersByTimeAsync(5_000);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(4);

    await wrapper.get('.homework-result__refresh-row button').trigger('click');
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledTimes(5);
    expect(wrapper.text()).toContain('通过');
  });
});

function mountResult(extraProps: { submissionId?: number } = {}) {
  return mount(HomeworkSubmissionResultView, {
    props: {
      courseId: 101,
      homeworkId: 11,
      ...extraProps
    },
    global: {
      stubs: {
        RouterLink: RouterLinkStub
      }
    }
  });
}

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 11,
    courseId: 101,
    chapterId: 1001,
    judgeConfigId: 1,
    title: '数据结构第三次作业',
    description: '完成堆优先队列。',
    type: 'CODE',
    status: 'PUBLISHED',
    totalScore: 100,
    deadline: '2026-08-18T23:59:59',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    createdBy: 7,
    publishedAt: '2026-08-16T09:00:00',
    createdAt: '2026-08-15T09:00:00',
    updatedAt: '2026-08-16T09:00:00',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function submission(overrides: Partial<HomeworkSubmissionSummary> = {}): HomeworkSubmissionSummary {
  return {
    submissionId: 91,
    homeworkId: 11,
    studentId: 601,
    submitType: 'CODE',
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    reviewStatus: 'REVIEWED',
    autoScore: 90,
    manualScore: null,
    finalScore: null,
    comment: null,
    version: 2,
    final: true,
    submittedAt: '2026-08-17T10:00:00',
    ...overrides
  };
}

function submissionDetail(overrides: Partial<HomeworkSubmissionDetail> = {}): HomeworkSubmissionDetail {
  return submission(overrides);
}

function evaluation(overrides: Partial<HomeworkEvaluationResult> = {}): HomeworkEvaluationResult {
  return {
    evaluationId: 8001,
    submissionId: 91,
    evaluationStatus: 'ACCEPTED',
    score: 90,
    passedCases: 5,
    totalCases: 5,
    durationMs: 128,
    errorMessage: null,
    feedback: null,
    compileLog: null,
    runLog: null,
    reevaluation: false,
    startedAt: '2026-08-17T10:00:01',
    finishedAt: '2026-08-17T10:00:02',
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
