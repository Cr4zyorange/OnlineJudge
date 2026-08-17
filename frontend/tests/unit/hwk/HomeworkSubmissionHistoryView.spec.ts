import { config, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionHistoryView from '../../../src/views/hwk/HomeworkSubmissionHistoryView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkReviewLog, HomeworkSubmissionSummary } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkSubmissionHistoryView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    config.global.stubs.RouterLink = RouterLinkStub;
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs).mockResolvedValue([]);
  });

  it('loads student history and marks the current effective submission', async () => {
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 202, version: 2, final: true, answerText: 'second answer' }),
      submission({ submissionId: 201, version: 1, final: false, answerText: 'first answer' })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 202,
      version: 2,
      final: true,
      answerText: 'second answer'
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'student'
      }
    });
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenCalledWith(11);
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain('当前有效');
    expect(wrapper.text()).toContain('历史版本');

    await wrapper.get('[data-submission-id="202"] button').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(202);
    expect(wrapper.text()).toContain('second answer');
  });

  it('sorts student submissions newest first and keeps latest and effective markers independent', async () => {
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({
        submissionId: 201,
        version: 1,
        final: false,
        submittedAt: '2026-06-01T10:00:00'
      }),
      submission({
        submissionId: 202,
        version: 2,
        final: true,
        submittedAt: '2026-06-01T11:00:00'
      }),
      submission({
        submissionId: 203,
        version: 3,
        final: false,
        submittedAt: '2026-06-01T12:00:00'
      })
    ]);

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'student'
      },
      global: {
        stubs: { RouterLink: RouterLinkStub }
      }
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="page-header"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="summary-strip"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="data-table"]').exists()).toBe(true);

    const versionCells = wrapper.findAll('[data-testid^="history-version-"]');
    expect(versionCells.slice(0, 3).map((cell) => cell.get('strong').text())).toEqual(['版本 3', '版本 2', '版本 1']);
    expect(wrapper.get('[data-testid="history-version-203"]').text()).toContain('最新版本');
    expect(wrapper.get('[data-testid="history-version-203"]').text()).not.toContain('当前有效');
    expect(wrapper.get('[data-testid="history-version-202"]').text()).toContain('当前有效');
    expect(wrapper.get('[data-testid="history-version-202"]').text()).not.toContain('最新版本');

    const resultLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => link.attributes('data-testid') === 'history-result-203');
    expect(resultLink?.props('to')).toEqual({
      name: 'homework-submission-result',
      params: {
        courseId: 101,
        homeworkId: 11,
        submissionId: 203
      }
    });
  });

  it('shows a retryable student error state and reloads the real history API', async () => {
    vi.mocked(homeworkApi.listMyHomeworkSubmissions)
      .mockRejectedValueOnce(new Error('网络连接失败'))
      .mockResolvedValueOnce([
        submission({ submissionId: 204, version: 4, final: true })
      ]);

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'student'
      },
      global: {
        stubs: { RouterLink: RouterLinkStub }
      }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('网络连接失败');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="data-table"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('版本 4');
  });

  it.each([
    {
      name: '对象形式',
      answerJson: '{"q1":["B"],"q2":["A","C"]}',
      expected: ['题目 q1：选项 B', '题目 q2：选项 A、C'],
      forbidden: ['{"q1"', 'answerJson', 'JSON']
    },
    {
      name: '数组形式',
      answerJson: '["B",["A","C"]]',
      expected: ['题目 1：选项 B', '题目 2：选项 A、C'],
      forbidden: ['["B"', 'answerJson', 'JSON']
    },
    {
      name: '无法解析的历史数据',
      answerJson: '{broken-answer',
      expected: ['客观题答案暂时无法解析'],
      forbidden: ['{broken-answer', 'answerJson', 'JSON']
    }
  ])('将学生客观题的$name提交显示为可读答案而不暴露原始存储内容', async ({
    answerJson,
    expected,
    forbidden
  }) => {
    const objectiveSubmission = submission({
      submissionId: 205,
      submitType: 'OBJECTIVE',
      answerText: null,
      answerJson
    });
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([objectiveSubmission]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(objectiveSubmission);

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'student'
      }
    });
    await flushPromises();

    await wrapper.get('[data-submission-id="205"] button').trigger('click');
    await flushPromises();

    for (const value of expected) {
      expect(wrapper.get('.hwk-history__answer').text()).toContain(value);
    }
    for (const value of forbidden) {
      expect(wrapper.get('.hwk-history__answer').text()).not.toContain(value);
    }
  });

  it('在路由复用到新作业时重载并忽略旧列表的迟到响应', async () => {
    const oldHistory = deferred<HomeworkSubmissionSummary[]>();
    const newHistory = deferred<HomeworkSubmissionSummary[]>();
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockImplementation((homeworkId) => (
      homeworkId === 11 ? oldHistory.promise : newHistory.promise
    ));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'student'
      }
    });
    await flushPromises();

    await wrapper.setProps({ homeworkId: 12 });
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenNthCalledWith(1, 11);
    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenNthCalledWith(2, 12);

    newHistory.resolve([
      submission({ submissionId: 212, homeworkId: 12, version: 2, answerText: '新作业答案' })
    ]);
    await flushPromises();
    expect(wrapper.text()).toContain('版本 2');

    oldHistory.resolve([
      submission({ submissionId: 111, homeworkId: 11, version: 99, answerText: '旧作业迟到答案' })
    ]);
    await flushPromises();

    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).not.toContain('版本 99');
  });

  it('课程或角色变化时清空分页与选中详情并按新权限上下文重载', async () => {
    const refreshedTeacherHistory = deferred<{
      list: HomeworkSubmissionSummary[];
      page: number;
      size: number;
      total: number;
    }>();
    const oldDetail = deferred<HomeworkSubmissionSummary>();
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce({
        list: [submission({ submissionId: 301, studentId: 601, answerText: '旧课程答案' })],
        page: 2,
        size: 20,
        total: 21
      })
      .mockImplementationOnce(() => refreshedTeacherHistory.promise);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockImplementationOnce(() => oldDetail.promise);
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ submissionId: 401, studentId: 701, version: 4, answerText: '学生新上下文' })
    ]);

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();
    expect(wrapper.text()).toContain('第 2 页');

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();
    await wrapper.setProps({ courseId: 102 });
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, { page: 1, size: 20 });
    expect(wrapper.text()).not.toContain('学生 601');

    oldDetail.resolve(submission({ submissionId: 301, studentId: 601, answerText: '旧课程迟到详情' }));
    await flushPromises();
    expect(wrapper.text()).not.toContain('旧课程迟到详情');
    expect(homeworkApi.getHomeworkSubmissionReviewLogs).not.toHaveBeenCalled();

    refreshedTeacherHistory.resolve({
      list: [submission({ submissionId: 302, studentId: 702, answerText: '新课程答案' })],
      page: 1,
      size: 20,
      total: 1
    });
    await flushPromises();
    expect(wrapper.text()).toContain('学生 702');

    await wrapper.setProps({ role: 'student' });
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenCalledWith(11);
    expect(wrapper.text()).toContain('版本 4');
    expect(wrapper.text()).not.toContain('学生 702');
  });

  it('loads teacher submission history with pagination', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 301, studentId: 601, answerText: 'student 601 answer' })
      ],
      page: 1,
      size: 20,
      total: 21
    }).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 302, studentId: 602, answerText: 'student 602 answer', version: 1 })
      ],
      page: 2,
      size: 20,
      total: 21
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      answerText: 'student 601 answer'
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(11, { page: 1, size: 20 });
    expect(wrapper.text()).toContain('共 21 条');
    expect(wrapper.text()).toContain('学生 601');

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(301);
    expect(wrapper.text()).toContain('student 601 answer');

    await wrapper.get('[data-testid="history-next"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, { page: 2, size: 20 });
    expect(wrapper.text()).toContain('学生 602');
  });

  it('lets a teacher review a submission and refreshes review logs', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 301, studentId: 601, answerText: 'student 601 answer' })
      ],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      answerText: 'student 601 answer'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([reviewLog({ submissionId: 301, newScore: 90, comment: 'Clear reasoning.' })]);
    vi.mocked(homeworkApi.reviewHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      answerText: 'student 601 answer',
      reviewStatus: 'REVIEWED',
      manualScore: 88,
      finalScore: 90,
      comment: 'Clear reasoning.'
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="history-review-manual-score"]').setValue('88');
    await wrapper.get('[data-testid="history-review-final-score"]').setValue('90');
    await wrapper.get('[data-testid="history-review-comment"]').setValue('Clear reasoning.');
    await wrapper.get('[data-testid="history-review-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.reviewHomeworkSubmission).toHaveBeenCalledWith(301, {
      manualScore: 88,
      finalScore: 90,
      comment: 'Clear reasoning.'
    });
    expect(homeworkApi.getHomeworkSubmissionReviewLogs).toHaveBeenLastCalledWith(301);
    expect(wrapper.text()).toContain('已批阅');
    expect(wrapper.text()).toContain('批阅');
    expect(wrapper.text()).not.toContain('REVIEWED');
    expect(wrapper.text()).not.toContain('REVIEW');
    expect(wrapper.text()).toContain('Clear reasoning.');
  });

  it('lets a teacher trigger reevaluation and refreshes review logs', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 301, studentId: 601, submitType: 'CODE', evaluationStatus: 'WRONG_ANSWER' })
      ],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      submitType: 'CODE',
      evaluationStatus: 'WRONG_ANSWER'
    }));
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([reviewLog({
        submissionId: 301,
        operationType: 'REJUDGE',
        oldScore: 40,
        newScore: 100,
        reason: 'judge data fixed',
        comment: null
      })]);
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockResolvedValueOnce({
      evaluationId: 502,
      submissionId: 301,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 2,
      totalCases: 2,
      feedback: 'accepted',
      reevaluation: true,
      startedAt: '2026-06-01T11:00:00',
      finishedAt: '2026-06-01T11:00:01'
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      submitType: 'CODE',
      evaluationStatus: 'ACCEPTED',
      autoScore: 100,
      finalScore: 100
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="history-reevaluate-reason"]').setValue('judge data fixed');
    await wrapper.get('[data-testid="history-reevaluate-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.reevaluateHomeworkSubmission).toHaveBeenCalledWith(301, 'judge data fixed');
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenLastCalledWith(301);
    expect(homeworkApi.getHomeworkSubmissionReviewLogs).toHaveBeenLastCalledWith(301);
    expect(wrapper.text()).toContain('重评完成');
    expect(wrapper.text()).toContain('重评');
    expect(wrapper.text()).not.toContain('Reevaluation finished');
    expect(wrapper.text()).not.toContain('REJUDGE');
    expect(wrapper.text()).toContain('judge data fixed');
  });

  it('passes teacher submission filters to the history API', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 301, studentId: 601, answerText: 'student 601 answer' })
      ],
      page: 1,
      size: 20,
      total: 1
    }).mockResolvedValueOnce({
      list: [
        submission({
          submissionId: 302,
          studentId: 602,
          submitStatus: 'LATE',
          evaluationStatus: 'PENDING',
          reviewStatus: 'NEED_REVIEW'
        })
      ],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="history-student-keyword"]').setValue('602');
    await wrapper.get('[data-testid="history-submit-status"]').setValue('LATE');
    await wrapper.get('[data-testid="history-evaluation-status"]').setValue('PENDING');
    await wrapper.get('[data-testid="history-review-status"]').setValue('NEED_REVIEW');
    await wrapper.get('[data-testid="history-apply-filters"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 20,
      studentKeyword: '602',
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      reviewStatus: 'NEED_REVIEW'
    });
    expect(wrapper.text()).toContain('学生 602');
    expect(wrapper.text()).toContain('提交状态：逾期提交');
    expect(wrapper.text()).toContain('评测状态：等待评测');
    expect(wrapper.text()).toContain('复核状态：需批阅');
    expect(wrapper.text()).not.toContain('LATE');
    expect(wrapper.text()).not.toContain('PENDING');
    expect(wrapper.text()).not.toContain('NEED_REVIEW');
  });

  it('renders history filters and detail statuses with business labels', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ submissionId: 301, studentId: 601, submitType: 'CODE', evaluationStatus: 'WRONG_ANSWER' })
      ],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(submission({
      submissionId: 301,
      studentId: 601,
      submitType: 'CODE',
      evaluationStatus: 'WRONG_ANSWER'
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        role: 'teacher'
      }
    });
    await flushPromises();

    const optionLabels = wrapper.findAll('option').map((option) => option.text());
    expect(optionLabels).toEqual(expect.arrayContaining(['已提交', '逾期提交', '等待评测', '答案错误', '需批阅']));
    expect(optionLabels).not.toEqual(expect.arrayContaining(['SUBMITTED', 'LATE', 'PENDING', 'WRONG_ANSWER', 'NEED_REVIEW']));

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('作业类型：代码作业');
    expect(wrapper.text()).toContain('提交状态：已提交');
    expect(wrapper.text()).toContain('评测状态：答案错误');
    expect(wrapper.text()).toContain('复核状态：待批阅');
    expect(wrapper.text()).not.toContain('CODE');
    expect(wrapper.text()).not.toContain('WRONG_ANSWER');
    expect(wrapper.text()).not.toContain('UNREVIEWED');
  });

  it('shows an empty state when there is no submission history', async () => {
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        courseId: 101,
        homeworkId: 12,
        role: 'student'
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无提交记录');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

function reviewLog(overrides: Partial<HomeworkReviewLog> = {}): HomeworkReviewLog {
  return {
    id: 701,
    submissionId: 201,
    homeworkId: 11,
    studentId: 601,
    operationType: 'REVIEW',
    oldScore: null,
    newScore: 90,
    comment: 'Clear reasoning.',
    operatorId: 501,
    reason: null,
    createdAt: '2026-06-01T11:00:00',
    ...overrides
  };
}

function submission(overrides: Partial<HomeworkSubmissionSummary> = {}): HomeworkSubmissionSummary {
  return {
    submissionId: 201,
    homeworkId: 11,
    studentId: 601,
    submitType: 'TEXT',
    answerText: 'answer',
    answerJson: null,
    fileUrl: null,
    language: null,
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'NONE',
    reviewStatus: 'UNREVIEWED',
    autoScore: null,
    manualScore: null,
    finalScore: null,
    comment: null,
    version: 1,
    final: true,
    submittedAt: '2026-06-01T10:00:00',
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
