import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, RouterView } from 'vue-router';
import * as crsApi from '../../../src/api/crs/courses';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
import type {
  LabExperimentDetail,
  LabReportSummary,
  LabResult,
  LabScoreSummary,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionResult,
  LabSubmissionSummary
} from '../../../src/types/lab';
import LabStudentView from '../../../src/views/lab/LabStudentView.vue';

vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/crs/courses');
vi.mock('../../../src/api/lrn/learningProgress');
vi.mock('../../../src/api/lrn/learningRecords');

describe('LabStudentView task-flow contract', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-08-18T09:00:00+08:00'));
    window.sessionStorage.clear();
    window.history.replaceState({}, '', '/courses/101/labs/7');
    resetRuntimeContext();
    currentUser.value = {
      id: 601,
      username: 'lab-student',
      displayName: '实验学生',
      userType: 'STUDENT',
      roles: ['STUDENT'],
      permissions: []
    };
    vi.mocked(crsApi.listResources).mockResolvedValue([]);
    vi.mocked(learningProgressApi.saveLearningProgress).mockResolvedValue({
      progressId: 1,
      courseId: 101,
      courseName: '软件工程实践',
      chapterId: 3,
      chapterName: '自动评测',
      sourceModule: 'LAB',
      sourceId: 7,
      progressPercent: 10,
      lastPosition: 'labId=7',
      status: 'IN_PROGRESS',
      continueUrl: '/courses/101/labs/7',
      updatedAt: '2026-08-18T09:00:00'
    });
    vi.mocked(learningRecordsApi.reportLearningRecord).mockResolvedValue({
      id: 1,
      courseId: 101,
      courseName: '软件工程实践',
      sourceModule: 'LAB',
      sourceId: 7,
      actionType: 'ACCESS',
      durationSeconds: 0,
      startedAt: '2026-08-18T09:00:00',
      endedAt: '2026-08-18T09:00:00'
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    resetRuntimeContext();
  });

  it('separates the detail summary from the focused submit workspace and exposes continuous next steps', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView());

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="lab-detail-page"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="lab-student-attachments"]').exists()).toBe(true);
    expect(wrapper.find('[data-action="submit-lab"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="lab-submit-link"]').attributes('href'))
      .toBe('/courses/101/labs/7/submit');
    expect(wrapper.get('[data-testid="lab-result-link"]').attributes('href'))
      .toBe('/courses/101/labs/7/result');
    expect(wrapper.get('[data-testid="lab-history-link"]').attributes('href'))
      .toBe('/courses/101/labs/7/submissions');
    const detailFlowSteps = wrapper.findAll('.lab-flow a');
    expect(detailFlowSteps[0]?.attributes('aria-current')).toBe('step');
    expect(detailFlowSteps[1]?.attributes('aria-current')).toBeUndefined();
    expect(wrapper.text()).toContain('自动评测');
    expect(wrapper.text()).not.toContain('DOCKER_IO');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('ACCEPTED');
    expect(wrapper.text()).not.toContain('UI-LAB');

    await wrapper.setProps({ mode: 'submit' });
    await flushPromises();
    const submitFlowSteps = wrapper.findAll('.lab-flow a');
    expect(submitFlowSteps[0]?.attributes('aria-current')).toBeUndefined();
    expect(submitFlowSteps[1]?.attributes('aria-current')).toBe('step');
  });

  it('renders a manual experiment as teacher-scored in the student detail', async () => {
    stubLab(baseLab({
      evaluationMode: 'MANUAL' as unknown as LabExperimentDetail['evaluationMode'],
      autoEvaluate: false
    }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    const evaluationFact = wrapper.findAll('.detail-grid > div')
      .find((fact) => fact.find('dt').text() === '评测方式');
    expect(evaluationFact?.get('dd').text()).toBe('教师评分');
    expect(evaluationFact?.text()).not.toContain('自动评测');
    expect(evaluationFact?.text()).not.toContain('MANUAL');
  });

  it.each(['SCORE_PUBLISHED', 'ARCHIVED'] as const)(
    'shows an explicit ungraded state when %s experiment scores are published without a student score',
    async (status) => {
      stubLab(baseLab({ status }));
      vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
        submission({ finalScore: null })
      ]);
      vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
      vi.mocked(labApi.getLabResult).mockResolvedValue(resultView({ status }));

      const wrapper = mount(LabStudentView, {
        props: { courseId: 101, labId: 7, mode: 'detail' }
      });
      await flushPromises();

      const summary = wrapper.get('[data-testid="summary-strip"]');
      expect(summary.text()).toContain('成绩已发布，暂无评分');
      expect(summary.text()).not.toContain('尚未发布');
      expect(summary.text()).not.toContain('待发布');
    }
  );

  it.each([
    ['detail published but aggregate unpublished', 'SCORE_PUBLISHED', 'PUBLISHED'],
    ['detail unpublished but aggregate published', 'PUBLISHED', 'SCORE_PUBLISHED']
  ] as const)(
    'fails closed when %s',
    async (_case, detailStatus, aggregateStatus) => {
      stubLab(baseLab({ status: detailStatus }));
      vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
      vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
      const aggregate = resultView({ status: aggregateStatus });
      const leakedScore = publishedScore({ comment: '不应泄露的教师评语' });
      aggregate.latestScore = leakedScore;
      aggregate.submission.latestScore = leakedScore;
      vi.mocked(labApi.getLabResult).mockResolvedValue(aggregate);

      const wrapper = mount(LabStudentView, {
        props: { courseId: 101, labId: 7, mode: 'submit' }
      });
      await flushPromises();

      expect(wrapper.text()).not.toContain('最终得分：97');
      expect(wrapper.text()).not.toContain('不应泄露的教师评语');
    }
  );

  it.each([
    ['aggregate lab', (result: LabResult) => ({ ...result, labId: 8 })],
    ['aggregate student', (result: LabResult) => ({ ...result, studentId: 602 })],
    ['submission lab', (result: LabResult) => ({
      ...result,
      submission: { ...result.submission, labId: 8 }
    })],
    ['submission student', (result: LabResult) => ({
      ...result,
      submission: { ...result.submission, studentId: 602 }
    })],
    ['submission id', (result: LabResult) => ({
      ...result,
      submission: { ...result.submission, submissionId: 99 }
    })],
    ['evaluation submission id', (result: LabResult) => ({
      ...result,
      evaluationResult: { ...result.evaluationResult, submissionId: 99 }
    })]
  ])('rejects a student aggregate with a mismatched %s', async (_case, mismatch) => {
    stubLab(baseLab({ status: 'SCORE_PUBLISHED' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    const aggregate = resultView({ status: 'SCORE_PUBLISHED' });
    const leakedScore = publishedScore({ comment: '不应应用的跨对象评分' });
    aggregate.latestScore = leakedScore;
    aggregate.submission.latestScore = leakedScore;
    vi.mocked(labApi.getLabResult).mockResolvedValue(mismatch(aggregate));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.text()).not.toContain('最终得分：97');
    expect(wrapper.text()).not.toContain('不应应用的跨对象评分');
  });

  it.each([
    ['experiment id', { id: 8, title: '跨实验详情' }],
    ['course id', { courseId: 202, title: '跨课程详情' }]
  ])('rejects a lab detail with a mismatched %s without writing learning records and allows retry', async (_field, mismatch) => {
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(baseLab(mismatch))
      .mockResolvedValueOnce(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('error');
    expect(wrapper.text()).toContain('实验信息与当前课程不匹配');
    expect(wrapper.text()).not.toContain(mismatch.title);
    expect(labApi.listLabSubmissions).not.toHaveBeenCalled();
    expect(learningProgressApi.saveLearningProgress).not.toHaveBeenCalled();
    expect(learningRecordsApi.reportLearningRecord).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('容器输入输出实验');
    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledOnce();
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenCalledOnce();
  });

  it('keeps a terminal direct evaluation when a slower aggregate response still reports pending', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission({ evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(
      evaluation({ evaluationStatus: 'ACCEPTED' })
    );
    const aggregate = deferred<ReturnType<typeof resultView>>();
    vi.mocked(labApi.getLabResult).mockReturnValue(aggregate.promise);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    aggregate.resolve(resultView({ evaluationStatus: 'PENDING' }));
    await flushPromises();

    expect(wrapper.text()).toContain('评测状态：通过');
    expect(wrapper.text()).not.toContain('评测状态：等待评测');
  });

  it('stops evaluation polling after 60 seconds and can recover through retry synchronization', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission({ evaluationStatus: 'PENDING' })
    ]);
    let terminal = false;
    vi.mocked(labApi.getLabSubmissionResult).mockImplementation(async () => (
      evaluation({ evaluationStatus: terminal ? 'ACCEPTED' : 'PENDING' })
    ));
    vi.mocked(labApi.getLabResult).mockResolvedValue(
      resultView({ evaluationStatus: 'PENDING' })
    );

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(60_000);
    await flushPromises();

    expect(wrapper.text()).toContain('评测结果同步超过 60 秒');
    const callsAtLimit = vi.mocked(labApi.getLabSubmissionResult).mock.calls.length;
    await vi.advanceTimersByTimeAsync(5_000);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledTimes(callsAtLimit);

    terminal = true;
    await wrapper.get('.inline-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).not.toContain('评测结果同步超过 60 秒');
    expect(wrapper.text()).toContain('评测状态：通过');
  });

  it('times out a never-settling evaluation request and ignores its late response', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission({ evaluationStatus: 'PENDING' })
    ]);
    const pendingEvaluation = deferred<LabSubmissionResult>();
    vi.mocked(labApi.getLabSubmissionResult).mockReturnValue(pendingEvaluation.promise);
    vi.mocked(labApi.getLabResult).mockResolvedValue(
      resultView({ evaluationStatus: 'PENDING' })
    );

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(60_000);
    await flushPromises();

    expect(wrapper.text()).toContain('评测结果同步超过 60 秒');
    expect(wrapper.text()).toContain('手动重试');

    pendingEvaluation.resolve(evaluation({ evaluationStatus: 'ACCEPTED' }));
    await flushPromises();

    expect(wrapper.text()).not.toContain('评测状态：通过');
    expect(wrapper.text()).toContain('评测结果同步超过 60 秒');
  });

  it('recovers after an evaluation polling request fails', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission({ evaluationStatus: 'PENDING' })
    ]);
    vi.mocked(labApi.getLabSubmissionResult)
      .mockRejectedValueOnce(new Error('评测网络暂时不可用'))
      .mockResolvedValueOnce(evaluation({ evaluationStatus: 'ACCEPTED' }));
    vi.mocked(labApi.getLabResult).mockResolvedValue(
      resultView({ evaluationStatus: 'PENDING' })
    );

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('评测网络暂时不可用');
    await wrapper.get('.inline-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).not.toContain('评测网络暂时不可用');
    expect(wrapper.text()).toContain('评测状态：通过');
  });

  it('disables the primary submit action and explains an expired experiment', async () => {
    stubLab(baseLab({ deadline: '2026-08-17T23:59:59' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="lab-submit-blocked"]').text()).toContain('已截止');
    expect(wrapper.get('[data-testid="submit-lab-button"]').attributes('disabled')).toBeDefined();
    expect(wrapper.text()).not.toContain('UI-LAB');

    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    expect(labApi.submitLab).not.toHaveBeenCalled();
  });

  it('renders an unavailable start action as a native disabled button instead of a focusable link', async () => {
    stubLab(baseLab({ deadline: '2026-08-17T23:59:59' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    const startAction = wrapper.get('.lab-student__next .button--primary');
    expect(startAction.element.tagName).toBe('BUTTON');
    expect(startAction.attributes('disabled')).toBeDefined();
    expect(startAction.attributes('href')).toBeUndefined();

    const pathBeforeClick = window.location.pathname;
    await startAction.trigger('click');
    expect(window.location.pathname).toBe(pathBeforeClick);
  });

  it('keeps an available start action as a real link to the submission workspace', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    const startAction = wrapper.get('[data-testid="lab-start-action"]');
    expect(startAction.element.tagName).toBe('A');
    expect(startAction.attributes('href')).toBe('/courses/101/labs/7/submit');
    expect(startAction.attributes('disabled')).toBeUndefined();
  });

  it('allows only one in-flight submission request', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    let resolveSubmission!: (value: ReturnType<typeof submission>) => void;
    vi.mocked(labApi.submitLab).mockReturnValue(new Promise((resolve) => {
      resolveSubmission = resolve;
    }));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('once')");

    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');

    expect(labApi.submitLab).toHaveBeenCalledTimes(1);
    resolveSubmission(submission());
    await flushPromises();
  });

  it.each([
    ['another experiment', { labId: 8 }],
    ['another student', { studentId: 602 }]
  ])('rejects a confirmed submission response belonging to %s', async (_case, mismatch) => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    vi.mocked(labApi.submitLab).mockResolvedValue(submission(mismatch));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('keep after invalid receipt')");
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('提交回执与当前实验或学生不匹配');
    expect(wrapper.text()).not.toContain('提交成功，版本 1');
    expect((wrapper.get('[name="code"]').element as HTMLTextAreaElement).value)
      .toBe("print('keep after invalid receipt')");
    expect(labApi.getLabSubmissionResult).not.toHaveBeenCalled();
  });

  it('finishes the confirmed submission flow while optional learning records are still pending', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    vi.mocked(labApi.submitLab).mockResolvedValue(submission());
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView());

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    vi.mocked(learningProgressApi.saveLearningProgress).mockImplementationOnce(() => new Promise(() => {}));
    vi.mocked(learningRecordsApi.reportLearningRecord).mockImplementationOnce(() => new Promise(() => {}));
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('non-blocking records')");
    await vi.advanceTimersByTimeAsync(500);
    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7')).not.toBeNull();

    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('提交成功，版本 1');
    expect(wrapper.get('[data-testid="submit-lab-button"]').attributes('disabled')).toBeUndefined();
    expect((wrapper.get('[name="code"]').element as HTMLTextAreaElement).value).toBe('');
    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7')).toBeNull();
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 88);
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledWith(expect.objectContaining({
      progressPercent: 100
    }));
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'SUBMIT'
    }));
  });

  it('blocks submission when both inline code and a source file are present', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    vi.mocked(labApi.submitLab).mockResolvedValue(submission());
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView());

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('choose one')");
    const fileInput = wrapper.get<HTMLInputElement>('[name="file"]');
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['print(1)'], 'solution.py', { type: 'text/x-python' })]
    });
    await fileInput.trigger('change');

    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(labApi.submitLab).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('在线代码和源码文件只能选择一种提交方式');
  });

  it.each([
    ['another experiment', { labId: 8 }],
    ['another student', { studentId: 602 }]
  ])('rejects every latest-history item belonging to %s before loading its result', async (_case, mismatch) => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(),
      submission({ submissionId: 77, version: 0, isLatest: false, ...mismatch })
    ]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('提交历史与当前实验或学生不匹配');
    expect(labApi.getLabSubmissionResult).not.toHaveBeenCalled();
    expect(labApi.getLabResult).not.toHaveBeenCalled();
  });

  it('keeps the newest same-page history retry when overlapping responses arrive out of order', async () => {
    stubLab(baseLab());
    const olderRetry = deferred<LabSubmissionHistoryItem[]>();
    const newerRetry = deferred<LabSubmissionHistoryItem[]>();
    vi.mocked(labApi.listLabSubmissions)
      .mockRejectedValueOnce(new Error('首次同步失败'))
      .mockReturnValueOnce(olderRetry.promise)
      .mockReturnValueOnce(newerRetry.promise);
    vi.mocked(labApi.getLabSubmissionResult).mockImplementation(async (_labId, submissionId) => (
      evaluation({ submissionId, message: `版本 ${submissionId} 的评测` })
    ));
    const latestAggregate = resultView({ submissionId: 103 });
    latestAggregate.submission.version = 3;
    vi.mocked(labApi.getLabResult).mockResolvedValue(latestAggregate);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'detail' }
    });
    await flushPromises();
    expect(wrapper.text()).toContain('首次同步失败');

    const retryButton = wrapper.get('.inline-button');
    retryButton.element.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    retryButton.element.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    newerRetry.resolve([submission({ submissionId: 103, version: 3 })]);
    await flushPromises();
    expect(wrapper.text()).toContain('版本 3');
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 103);

    olderRetry.resolve([submission({ submissionId: 102, version: 2 })]);
    await flushPromises();

    expect(wrapper.text()).toContain('版本 3');
    expect(wrapper.text()).not.toContain('版本 2');
    expect(labApi.getLabSubmissionResult).not.toHaveBeenCalledWith(7, 102);
  });

  it('clears stale source and report validation errors when the student selects valid replacements', async () => {
    stubLab(baseLab({ reportRequired: true }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView());

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');

    const sourceInput = wrapper.get<HTMLInputElement>('[name="file"]');
    setInputFile(sourceInput.element, new File(['bad'], 'solution.txt', { type: 'text/plain' }));
    await sourceInput.trigger('change');
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    expect(wrapper.text()).toContain('仅支持 .py 文件');

    setInputFile(sourceInput.element, new File(["print('ok')"], 'solution.py', { type: 'text/x-python' }));
    await sourceInput.trigger('change');
    expect(wrapper.text()).not.toContain('仅支持 .py 文件');

    const reportInput = wrapper.get<HTMLInputElement>('[name="reportFile"]');
    setInputFile(reportInput.element, new File(['bad'], 'report.txt', { type: 'text/plain' }));
    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    expect(wrapper.text()).toContain('实验报告仅支持 PDF、DOCX 或 ZIP');

    setInputFile(reportInput.element, new File(['pdf'], 'report.pdf', { type: 'application/pdf' }));
    await reportInput.trigger('change');
    expect(wrapper.text()).not.toContain('实验报告仅支持 PDF、DOCX 或 ZIP');
  });

  it('clears both native file controls after confirmed source and report uploads', async () => {
    stubLab(baseLab({ reportRequired: true }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    vi.mocked(labApi.submitLab).mockResolvedValue(submission({ submissionId: 99, version: 2 }));
    vi.mocked(labApi.getLabSubmissionResult).mockImplementation(async (_labId, submissionId) => (
      evaluation({ submissionId })
    ));
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView({ submissionId: 99 }));
    vi.mocked(labApi.uploadLabReport).mockResolvedValue(report({ reportId: 42, version: 2 }));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');

    const sourceInput = wrapper.get<HTMLInputElement>('[name="file"]');
    setInputFile(sourceInput.element, new File(["print('ok')"], 'acceptance.py', { type: 'text/x-python' }));
    await sourceInput.trigger('change');
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(sourceInput.element.value).toBe('');

    const reportInput = wrapper.get<HTMLInputElement>('[name="reportFile"]');
    setInputFile(reportInput.element, new File(['pdf'], 'report.pdf', { type: 'application/pdf' }));
    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    await flushPromises();

    expect(reportInput.element.value).toBe('');
  });

  it('preserves the native report selection after a failed report upload', async () => {
    stubLab(baseLab({ reportRequired: true }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation());
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView());
    vi.mocked(labApi.uploadLabReport).mockRejectedValue(new Error('报告服务暂时不可用'));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    const reportInput = wrapper.get<HTMLInputElement>('[name="reportFile"]');
    setInputFile(reportInput.element, new File(['pdf'], 'report.pdf', { type: 'application/pdf' }));
    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('报告服务暂时不可用');
    expect(reportInput.element.value).toContain('report.pdf');
  });

  it('releases the submit lock when route reuse leaves submit mode and ignores the old response after returning', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    const pendingSubmission = deferred<LabSubmissionSummary>();
    vi.mocked(labApi.submitLab).mockReturnValue(pendingSubmission.promise);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('route reuse')");
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');

    expect(wrapper.get('[data-testid="submit-lab-button"]').attributes('disabled')).toBeDefined();
    await wrapper.setProps({ mode: 'detail' });
    await wrapper.setProps({ mode: 'submit' });
    await flushPromises();

    expect(wrapper.get('[data-testid="submit-lab-button"]').attributes('disabled')).toBeUndefined();

    pendingSubmission.resolve(submission({ submissionId: 109, version: 2 }));
    await flushPromises();

    expect(wrapper.text()).not.toContain('提交成功，版本 2');
    expect(wrapper.get('[data-testid="submit-lab-button"]').attributes('disabled')).toBeUndefined();
  });

  it('auto-saves a user-scoped draft and keeps it after a confirmed submission failure', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    vi.mocked(labApi.submitLab).mockRejectedValue(new Error('服务暂时不可用'));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('keep my draft')");
    await vi.advanceTimersByTimeAsync(500);

    const draftKey = 'oj:draft:v1:601:101:LAB:7';
    expect(window.sessionStorage.getItem(draftKey) ?? '').toContain("print('keep my draft')");
    expect(wrapper.get('[data-testid="lab-draft-status"]').text()).toContain('已自动保存');

    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('服务暂时不可用');
    expect(window.sessionStorage.getItem(draftKey) ?? '').toContain("print('keep my draft')");
  });

  it('does not create or announce a draft for a default language without code or a file', async () => {
    stubLab(baseLab({ allowedLanguages: 'python' }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="lab-draft-status"]').exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(500);

    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7')).toBeNull();
    expect(wrapper.find('[data-testid="lab-draft-status"]').exists()).toBe(false);
  });

  it('protects a selected report file from accidental page unload', async () => {
    stubLab(baseLab({ reportRequired: true }));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([submission()]);
    const addEventListener = vi.spyOn(window, 'addEventListener');

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    const reportInput = wrapper.get<HTMLInputElement>('[name="reportFile"]');
    Object.defineProperty(reportInput.element, 'files', {
      configurable: true,
      value: [new File(['report'], 'lab-report.pdf', { type: 'application/pdf' })]
    });
    await reportInput.trigger('change');

    const unloadEvent = new Event('beforeunload', { cancelable: true });
    const beforeUnloadListener = addEventListener.mock.calls.find(([type]) => type === 'beforeunload')?.[1];
    expect(beforeUnloadListener).toBeTypeOf('function');
    (beforeUnloadListener as EventListener)(unloadEvent);

    expect(unloadEvent.defaultPrevented).toBe(true);
    wrapper.unmount();
    addEventListener.mockRestore();
  });

  it('ignores an old report-upload response after route reuse loads another lab', async () => {
    vi.mocked(labApi.getLabDetail).mockImplementation(async (labId) => baseLab({
      id: labId,
      title: labId === 7 ? '旧实验' : '新实验',
      reportRequired: true
    }));
    vi.mocked(labApi.listLabSubmissions).mockImplementation(async (labId) => [
      submission({ labId, submissionId: labId * 10 })
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockImplementation(async (_labId, submissionId) => (
      evaluation({ submissionId })
    ));
    vi.mocked(labApi.getLabResult).mockRejectedValue(new Error('aggregate unavailable'));
    const pendingReport = deferred<LabReportSummary>();
    vi.mocked(labApi.uploadLabReport).mockReturnValue(pendingReport.promise);

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    const reportInput = wrapper.get<HTMLInputElement>('[name="reportFile"]');
    Object.defineProperty(reportInput.element, 'files', {
      configurable: true,
      value: [new File(['old report'], 'old-report.pdf', { type: 'application/pdf' })]
    });
    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    expect(labApi.uploadLabReport).toHaveBeenCalledWith(7, expect.any(Object));

    await wrapper.setProps({ labId: 8 });
    await flushPromises();
    expect(wrapper.text()).toContain('新实验');

    pendingReport.resolve(report({ fileName: 'old-report.pdf', version: 9 }));
    await flushPromises();

    expect(wrapper.text()).not.toContain('old-report.pdf');
    expect(wrapper.text()).not.toContain('实验报告上传成功，版本 9');
  });

  it('keeps an auto-saved draft when the student cancels in-app navigation', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/lab',
          component: LabStudentView,
          props: { courseId: 101, labId: 7, mode: 'submit' }
        },
        { path: '/away', component: { template: '<p>away</p>' } }
      ]
    });
    await router.push('/lab');
    await router.isReady();
    const wrapper = mount({
      components: { RouterView },
      template: '<RouterView />'
    }, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('stay here')");
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);

    await router.push('/away');
    await flushPromises();

    expect(confirm).toHaveBeenCalledOnce();
    expect(router.currentRoute.value.path).toBe('/lab');
    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7') ?? '')
      .toContain("print('stay here')");
    wrapper.unmount();
    confirm.mockRestore();
  });

  it('reconciles submission history after an interrupted request before inviting a retry', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([submission({
        submissionId: 99,
        version: 1,
        submittedAt: '2026-08-18T09:00:01+08:00'
      })]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValue(submissionDetail({
      submissionId: 99,
      version: 1,
      language: 'python',
      code: "print('maybe accepted')",
      submittedAt: '2026-08-18T09:00:01+08:00'
    }));
    vi.mocked(labApi.submitLab).mockRejectedValue(new TypeError('Failed to fetch'));
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValue(evaluation({ submissionId: 99 }));
    vi.mocked(labApi.getLabResult).mockResolvedValue(resultView({ submissionId: 99 }));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('maybe accepted')");
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="lab-reconcile-message"]').text())
      .toContain('已在提交历史确认版本 1');
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 99);
    expect(labApi.submitLab).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['language', { language: 'java' }],
    ['code', { code: "print('another tab')" }],
    ['submitted time', { submittedAt: '2026-08-18T08:59:00+08:00' }]
  ])('does not reconcile a higher version when submitted %s does not match the code attempt', async (_field, detailOverrides) => {
    stubLab(baseLab());
    const candidate = submission({
      submissionId: 99,
      version: 2,
      submittedAt: '2026-08-18T09:00:01+08:00'
    });
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([candidate]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValue(submissionDetail({
      ...candidate,
      language: 'python',
      code: "print('verify me')",
      ...detailOverrides
    }));
    vi.mocked(labApi.submitLab).mockRejectedValue(new TypeError('Failed to fetch'));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('verify me')");
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 99);
    expect(wrapper.find('[data-testid="lab-reconcile-message"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('无法确认新版本来自本次代码提交');
    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7') ?? '')
      .toContain("print('verify me')");
  });

  it('keeps the draft and selected file when an interrupted file submission cannot be correlated', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([submission({
        submissionId: 99,
        version: 1,
        submittedAt: '2026-08-18T09:00:01+08:00',
        hasFile: true
      })]);
    vi.mocked(labApi.submitLab).mockRejectedValue(new TypeError('Failed to fetch'));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    const fileInput = wrapper.get<HTMLInputElement>('[name="file"]');
    setInputFile(fileInput.element, new File(['print(1)'], 'solution.py', { type: 'text/x-python' }));
    await fileInput.trigger('change');
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(wrapper.find('[data-testid="lab-reconcile-message"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('文件提交结果不确定');
    expect(wrapper.text()).toContain('solution.py');
    expect(fileInput.element.value).toContain('solution.py');
    expect(window.sessionStorage.getItem('oj:draft:v1:601:101:LAB:7') ?? '')
      .toContain('"language":"python"');
  });

  it('does not reconcile an interrupted request to an older submission with a different id', async () => {
    stubLab(baseLab());
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([submission({ submissionId: 88, version: 2 })])
      .mockResolvedValueOnce([submission({ submissionId: 77, version: 1 })]);
    vi.mocked(labApi.submitLab).mockRejectedValue(new Error('连接中断'));

    const wrapper = mount(LabStudentView, {
      props: { courseId: 101, labId: 7, mode: 'submit' }
    });
    await flushPromises();
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('do not accept stale history')");
    await wrapper.get('[data-action="submit-lab"]').trigger('submit');
    await flushPromises();

    expect(wrapper.find('[data-testid="lab-reconcile-message"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('连接中断');
  });
});

function stubLab(lab: LabExperimentDetail) {
  vi.mocked(labApi.getLabDetail).mockResolvedValue(lab);
}

function baseLab(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 3,
    title: '容器输入输出实验',
    description: '实现标准输入输出并通过公开测试。',
    status: 'PUBLISHED',
    deadline: '2026-08-20T23:59:59',
    maxScore: 100,
    attachmentIds: [11],
    allowedLanguages: 'python,java',
    evaluationMode: 'DOCKER_IO',
    autoEvaluate: true,
    reportRequired: false,
    timeLimitMs: 60000,
    memoryLimitKb: 262144,
    deleted: false,
    testcases: [{
      id: 1,
      labId: 7,
      input: '1 2',
      expectedOutput: '3',
      scoreWeight: 100,
      public: true,
      timeLimitMs: 1000,
      memoryLimitKb: 65536,
      orderNum: 1
    }],
    ...overrides
  };
}

function submission(overrides: Partial<LabSubmissionHistoryItem> = {}): LabSubmissionHistoryItem {
  return {
    submissionId: 88,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 92,
    finalScore: null,
    version: 1,
    submittedAt: '2026-08-18T08:30:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: false,
    ...overrides
  };
}

function evaluation(overrides: Partial<LabSubmissionResult> = {}): LabSubmissionResult {
  return {
    submissionId: 88,
    evaluationStatus: 'ACCEPTED' as const,
    score: 92,
    passedCases: 1,
    totalCases: 1,
    message: '全部公开用例通过',
    caseResults: [],
    submittedAt: '2026-08-18T08:30:00',
    finishedAt: '2026-08-18T08:30:05',
    ...overrides
  };
}

function resultView(overrides: {
  submissionId?: number;
  evaluationStatus?: LabSubmissionSummary['evaluationStatus'];
  status?: LabExperimentDetail['status'];
} = {}): LabResult {
  const current = submission({
    submissionId: overrides.submissionId ?? 88,
    evaluationStatus: overrides.evaluationStatus ?? 'ACCEPTED'
  });
  return {
    labId: 7,
    studentId: 601,
    status: overrides.status ?? 'PUBLISHED' as const,
    submission: {
      ...current,
      code: "print('result')",
      sourceFile: null,
      latestReport: null,
      latestScore: null
    },
    evaluationResult: evaluation({
      submissionId: current.submissionId,
      evaluationStatus: overrides.evaluationStatus ?? 'ACCEPTED'
    }),
    latestReport: null,
    latestScore: null,
    publishedAt: null
  };
}

function submissionDetail(overrides: Partial<LabSubmissionDetail> = {}): LabSubmissionDetail {
  return {
    ...submission(overrides),
    code: "print('verified')",
    sourceFile: null,
    latestReport: null,
    latestScore: null,
    ...overrides
  };
}

function report(overrides: Partial<LabReportSummary> = {}): LabReportSummary {
  return {
    reportId: 41,
    submissionId: 88,
    fileName: 'report.pdf',
    fileType: 'PDF',
    fileSize: 1024,
    version: 1,
    score: null,
    comment: null,
    submittedAt: '2026-08-18T09:00:00+08:00',
    downloadUrl: '/reports/41',
    ...overrides
  };
}

function publishedScore(overrides: Partial<LabScoreSummary> = {}): LabScoreSummary {
  return {
    submissionId: 88,
    reportId: null,
    autoScore: 92,
    reportScore: null,
    manualScore: 97,
    finalScore: 97,
    comment: '评分已发布',
    hasChangeLogs: false,
    scoredAt: '2026-08-18T09:00:00+08:00',
    updatedAt: '2026-08-18T09:00:00+08:00',
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

function setInputFile(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file]
  });
  Object.defineProperty(input, 'value', {
    configurable: true,
    writable: true,
    value: `C:\\fakepath\\${file.name}`
  });
}
