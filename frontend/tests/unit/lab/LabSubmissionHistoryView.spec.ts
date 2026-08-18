import { config, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionHistoryView from '../../../src/views/lab/LabSubmissionHistoryView.vue';
import * as labApi from '../../../src/api/lab/labs';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
import type {
  LabExperimentDetail,
  LabSubmissionDetail,
  LabSubmissionHistoryItem
} from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');

describe('LabSubmissionHistoryView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    config.global.stubs.RouterLink = RouterLinkStub;
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

  afterEach(resetRuntimeContext);

  it('loads experiment context, selects the current version, and never exposes raw storage values', async () => {
    const latest = submission({
      submissionId: 201,
      version: 2,
      isLatest: true,
      isFinal: false,
      isScoringBasis: false,
      evaluationStatus: 'ACCEPTED'
    });
    const current = submission({
      submissionId: 199,
      version: 1,
      isLatest: false,
      isFinal: true,
      isScoringBasis: true,
      hasFile: true,
      submitStatus: 'LATE',
      evaluationStatus: 'WRONG_ANSWER',
      autoScore: 70,
      finalScore: 70
    });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([latest, current]);
    vi.mocked(labApi.getLabSubmissionDetail).mockImplementation(async (_labId, submissionId) => (
      detail(submissionId === 199 ? current : latest, {
        code: submissionId === 199 ? "print('current version')" : "print('latest version')",
        fileId: submissionId === 199 ? 'private/lab/199/report.zip' : null
      })
    ));

    const wrapper = mountView();
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7);
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 199);
    expect(wrapper.text()).toContain('数组排序实验');
    expect(wrapper.text()).toContain('截止时间：2026-06-02 18:00');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('自动评测');
    expect(wrapper.text()).toContain('已提交');
    expect(wrapper.text()).toContain('逾期提交');
    expect(wrapper.text()).toContain('通过');
    expect(wrapper.text()).toContain('答案错误');
    expect(wrapper.text()).toContain('Python');
    expect(wrapper.text()).toContain('包含提交文件');
    expect(wrapper.text()).toContain("print('current version')");
    expect(wrapper.get('[data-testid="history-select-199"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-submission-id="199"]').classes()).toContain('lab-history__version--selected');

    for (const rawValue of [
      'PUBLISHED',
      'DOCKER_IO',
      'SUBMITTED',
      'LATE',
      'ACCEPTED',
      'WRONG_ANSWER',
      'python',
      'private/lab/199/report.zip'
    ]) {
      expect(wrapper.text()).not.toContain(rawValue);
    }

    const historicResultLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => link.attributes('data-testid') === 'history-result-199');
    expect(historicResultLink?.props('to')).toEqual({
      name: 'lab-submission-result',
      params: {
        courseId: 101,
        labId: 7,
        submissionId: 199
      }
    });

    await wrapper.get('[data-testid="history-select-201"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenLastCalledWith(7, 201);
    expect(wrapper.get('[data-testid="history-select-201"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-testid="history-select-199"]').attributes('aria-pressed')).toBe('false');
    expect(wrapper.text()).toContain("print('latest version')");
  });

  it('renders a manual experiment as teacher-scored in history context', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab({
      evaluationMode: 'MANUAL' as unknown as LabExperimentDetail['evaluationMode'],
      autoEvaluate: false
    }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('教师评分');
    expect(wrapper.text()).not.toContain('自动评测');
    expect(wrapper.text()).not.toContain('MANUAL');
  });

  it.each([
    ['experiment id', { id: 8, title: '跨实验信息' }],
    ['course id', { courseId: 202, title: '跨课程信息' }]
  ])('rejects experiment context with a mismatched %s and recovers on retry', async (_field, mismatch) => {
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(lab(mismatch))
      .mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.lab-history__context-error').text())
      .toContain('实验信息与当前课程不匹配');
    expect(wrapper.text()).not.toContain(mismatch.title);

    await wrapper.get('.lab-history__context-error button').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('数组排序实验');
    expect(wrapper.find('.lab-history__context-error').exists()).toBe(false);
  });

  it('does not load or retain history before the experiment course context is verified', async () => {
    const pendingExperiment = deferred<LabExperimentDetail>();
    const foreign = submission({ submissionId: 609, version: 9, isLatest: true });
    vi.mocked(labApi.getLabDetail).mockReturnValueOnce(pendingExperiment.promise);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([foreign]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(foreign, {
      code: "print('foreign course history')"
    }));

    const wrapper = mountView();
    await flushPromises();
    pendingExperiment.resolve(lab({ courseId: 202, title: '跨课程实验' }));
    await flushPromises();

    expect(wrapper.get('.lab-history__context-error').text())
      .toContain('实验信息与当前课程不匹配');
    expect(labApi.listLabSubmissions).not.toHaveBeenCalled();
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
    expect(wrapper.find('[data-submission-id="609"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("print('foreign course history')");
  });

  it.each([
    ['another experiment', { labId: 8 }],
    ['another student', { studentId: 999 }]
  ])('rejects the entire history when any item belongs to %s', async (_case, mismatch) => {
    const valid = submission({ submissionId: 610, version: 1 });
    const foreign = submission({
      submissionId: 611,
      version: 2,
      isLatest: true,
      ...mismatch
    });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([valid, foreign]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(foreign, {
      code: "print('foreign history item')"
    }));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.lab-history__list-state').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('提交历史与当前实验或学生不匹配');
    expect(wrapper.find('[data-submission-id="610"]').exists()).toBe(false);
    expect(wrapper.find('[data-submission-id="611"]').exists()).toBe(false);
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
  });

  it.each([
    ['lab id', { labId: 8 }],
    ['submission id', { submissionId: 999 }]
  ])('rejects selected detail with a mismatched %s and recovers on retry', async (_field, mismatch) => {
    const selected = submission({ submissionId: 620, version: 6, isLatest: true });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([selected]);
    vi.mocked(labApi.getLabSubmissionDetail)
      .mockResolvedValueOnce(detail(selected, {
        code: "print('foreign detail')",
        ...mismatch
      }))
      .mockResolvedValueOnce(detail(selected, { code: "print('verified detail')" }));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.lab-history__detail-state').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('提交内容与所选版本不匹配');
    expect(wrapper.text()).not.toContain("print('foreign detail')");

    await wrapper.get('.lab-history__detail-state [data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("print('verified detail')");
  });

  it('falls back to the latest version when there is no current effective version', async () => {
    const older = submission({ submissionId: 301, version: 1, isLatest: false });
    const latest = submission({ submissionId: 302, version: 2, isLatest: true });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([older, latest]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(latest));

    const wrapper = mountView();
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 302);
    expect(wrapper.get('[data-testid="history-select-302"]').attributes('aria-pressed')).toBe('true');
  });

  it('hides final scores until the experiment scores are published', async () => {
    const selected = submission({
      submissionId: 351,
      version: 3,
      isLatest: true,
      isFinal: true,
      autoScore: 72,
      finalScore: 99
    });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab({ status: 'PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([selected]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(selected));

    const hidden = mountView();
    await flushPromises();

    expect(hidden.text()).not.toContain('99 分');
    expect(hidden.text()).toContain('最终得分待发布');
    hidden.unmount();

    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab({ status: 'SCORE_PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([selected]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(selected));
    const published = mountView();
    await flushPromises();

    expect(published.text()).toContain('99 分');
    published.unmount();
  });

  it.each(['SCORE_PUBLISHED', 'ARCHIVED'] as const)(
    'shows an explicit ungraded state in the list and detail when the %s experiment has no score',
    async (status) => {
      const selected = submission({
        submissionId: 352,
        version: 3,
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        finalScore: null
      });
      vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab({ status }));
      vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([selected]);
      vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(selected));

      const wrapper = mountView();
      await flushPromises();

      expect(wrapper.get('[data-testid="history-select-352"]').text()).toContain('最终得分未评分');
      expect(wrapper.get('.lab-history__detail-facts').text()).toContain('最终得分未评分');
      expect(wrapper.get('[data-testid="history-select-352"]').text()).not.toContain('待发布');
      expect(wrapper.get('.lab-history__detail-facts').text()).not.toContain('待发布');
    }
  );

  it('retries a failed history request and auto-selects the recovered version', async () => {
    const recovered = submission({ submissionId: 401, version: 4, isLatest: true });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions)
      .mockRejectedValueOnce(new Error('网络连接失败'))
      .mockResolvedValueOnce([recovered]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(recovered));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.lab-history__list-state').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('网络连接失败');

    await wrapper.get('.lab-history__list-state [data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(2);
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 401);
    expect(wrapper.text()).toContain('版本 4');
  });

  it('retries the selected version detail without reloading the list', async () => {
    const selected = submission({ submissionId: 501, version: 5, isLatest: true });
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([selected]);
    vi.mocked(labApi.getLabSubmissionDetail)
      .mockRejectedValueOnce(new Error('提交内容暂时不可用'))
      .mockResolvedValueOnce(detail(selected, { code: "print('recovered detail')" }));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.lab-history__detail-state').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('提交内容暂时不可用');

    await wrapper.get('.lab-history__detail-state [data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledTimes(2);
    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("print('recovered detail')");
  });

  it('shows an actionable empty state when the student has no submissions yet', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab({ id: 8, title: '空实验' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mountView({ labId: 8 });
    await flushPromises();

    expect(wrapper.text()).toContain('还没有提交记录');
    expect(wrapper.text()).toContain('第一次提交后，每个版本都会保留在这里');
    const submitLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => link.attributes('data-testid') === 'history-empty-submit');
    expect(submitLink?.props('to')).toEqual({
      name: 'lab-submit',
      params: { courseId: 101, labId: 8 }
    });
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
  });

  it('reloads on prop changes, gates history, and ignores a stale experiment response', async () => {
    const oldExperiment = deferred<LabExperimentDetail>();
    const newVersion = submission({
      submissionId: 802,
      labId: 8,
      version: 2,
      isLatest: true
    });
    vi.mocked(labApi.getLabDetail).mockImplementation((labId) => (
      labId === 7
        ? oldExperiment.promise
        : Promise.resolve(lab({ id: 8, title: '新实验标题', deadline: '2026-08-20T20:00:00' }))
    ));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([newVersion]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail(newVersion, {
      code: "print('new lab detail')"
    }));

    const wrapper = mountView();
    await flushPromises();
    await wrapper.setProps({ labId: 8 });
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenNthCalledWith(1, 7);
    expect(labApi.getLabDetail).toHaveBeenNthCalledWith(2, 8);
    expect(labApi.listLabSubmissions).toHaveBeenCalledOnce();
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(8);
    expect(wrapper.text()).toContain('新实验标题');
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain("print('new lab detail')");

    oldExperiment.resolve(lab({ title: '旧实验迟到标题' }));
    await flushPromises();

    expect(wrapper.text()).toContain('新实验标题');
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).not.toContain('旧实验迟到标题');
    expect(wrapper.text()).not.toContain('版本 99');
    expect(labApi.listLabSubmissions).toHaveBeenCalledOnce();
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledTimes(1);
  });
});

function mountView(overrides: Partial<{ courseId: number; labId: number }> = {}) {
  return mount(LabSubmissionHistoryView, {
    props: {
      courseId: 101,
      labId: 7,
      ...overrides
    }
  });
}

function lab(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: null,
    title: '数组排序实验',
    description: '完成排序程序并提交评测。',
    status: 'PUBLISHED',
    deadline: '2026-06-02T18:00:00',
    maxScore: 100,
    attachmentIds: [],
    allowedLanguages: 'python,cpp',
    evaluationMode: 'DOCKER_IO',
    autoEvaluate: true,
    reportRequired: false,
    timeLimitMs: 1000,
    memoryLimitKb: 262144,
    testcases: [],
    deleted: false,
    ...overrides
  };
}

function submission(overrides: Partial<LabSubmissionHistoryItem> = {}): LabSubmissionHistoryItem {
  return {
    submissionId: 201,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 96,
    finalScore: 98,
    version: 2,
    submittedAt: '2026-06-01T10:00:00',
    isLatest: false,
    isFinal: false,
    isScoringBasis: false,
    hasFile: false,
    ...overrides
  };
}

function detail(
  item: LabSubmissionHistoryItem,
  overrides: Partial<LabSubmissionDetail> = {}
): LabSubmissionDetail {
  return {
    ...item,
    code: "print('history detail')",
    fileId: null,
    latestReport: null,
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

async function flushPromises() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve();
  }
}
