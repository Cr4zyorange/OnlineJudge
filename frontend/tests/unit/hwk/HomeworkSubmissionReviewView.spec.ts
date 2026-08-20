import { readFileSync } from 'node:fs';
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionReviewView from '../../../src/views/hwk/HomeworkSubmissionReviewView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkReviewLog,
  HomeworkSubmissionDetail,
  HomeworkType
} from '../../../src/types/hwk';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

const useRouteMock = vi.hoisted(() => vi.fn());

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>();
  return { ...actual, useRoute: useRouteMock };
});
vi.mock('../../../src/api/hwk/homeworks');
vi.mock('../../../src/api/lrn/learningProgress');

describe('HomeworkSubmissionReviewView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useRouteMock.mockReturnValue({ query: {} });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework());
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValue(submission());
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValue(evaluation());
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs).mockResolvedValue(reviewLogs());
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads one review with course, homework, student, version, evaluation, scores, and audit logs', async () => {
    const wrapper = mountView();
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(11);
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(301);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(301);
    expect(homeworkApi.getHomeworkSubmissionReviewLogs).toHaveBeenCalledWith(301);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.get('h1').text()).toContain('数据结构作业');
    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('林晓');
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('版本 3');
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('当前有效提交');
    expect(wrapper.get('[data-testid="score-auto"]').text()).toContain('86');
    expect(wrapper.get('[data-testid="score-manual"]').text()).toContain('88');
    expect(wrapper.get('[data-testid="score-final"]').text()).toContain('90');
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain('public class Main');
    expect(wrapper.get('[data-testid="evaluation-summary"]').text()).toContain('4 / 5');
    expect(wrapper.get('[data-testid="review-logs"]').text()).toContain('批阅');
    expect(wrapper.get('[data-testid="review-logs"]').text()).toContain('首次人工复核');
    expect(wrapper.text()).not.toContain('601');
    expect(wrapper.text()).not.toContain('ACCEPTED');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toContainEqual({
      name: 'homework-submission-workspace',
      params: { courseId: 101, homeworkId: 11 },
      query: {}
    });
    expect(links).toContainEqual({
      name: 'homework-manage-detail',
      params: { courseId: 101, homeworkId: 11 }
    });
  });

  it('returns to the queue with the opaque student reference and strips internal student query fields', async () => {
    useRouteMock.mockReturnValue({
      query: {
        keyword: '周然',
        studentRef: '37090d82ef8c0fac',
        submit: 'LATE',
        review: 'UNREVIEWED',
        page: '2',
        studentId: '601',
        studentKeyword: '601',
        unknown: 'discard-me'
      }
    });
    const wrapper = mountView();
    await flushPromises();

    const queueLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'homework-submission-workspace');
    expect(queueLink?.props('to')).toEqual({
      name: 'homework-submission-workspace',
      params: { courseId: 101, homeworkId: 11 },
      query: {
        keyword: '周然',
        studentRef: '37090d82ef8c0fac',
        submit: 'LATE',
        review: 'UNREVIEWED',
        page: '2'
      }
    });
  });

  it.each([
    {
      type: 'TEXT' as HomeworkType,
      detail: submission({ submitType: 'TEXT', answerText: '分层架构的核心是职责分离。', language: null }),
      expected: '分层架构的核心是职责分离。',
      forbidden: 'public class Main'
    },
    {
      type: 'OBJECTIVE' as HomeworkType,
      detail: submission({
        submitType: 'OBJECTIVE',
        answerText: null,
        answerJson: '{"q1":["B"],"q2":["A","C"]}',
        language: null
      }),
      expected: '第 1 题 · 队列的基本特征：选项 B',
      forbidden: 'q1'
    },
    {
      type: 'FILE' as HomeworkType,
      detail: submission({
        submitType: 'FILE',
        answerText: null,
        fileUrl: 'private-file-token-must-not-render',
        language: null
      }),
      expected: '本次提交包含附件',
      forbidden: 'private-file-token-must-not-render'
    }
  ])('renders $type answers as teacher-readable content without raw transport data', async ({
    type,
    detail,
    expected,
    forbidden
  }) => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework({
      type,
      questions: type === 'OBJECTIVE' ? objectiveQuestions() : []
    }));
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(detail);
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="submission-answer"]').text()).toContain(expected);
    expect(wrapper.get('[data-testid="submission-answer"]').text()).not.toContain(forbidden);
    if (type === 'OBJECTIVE') {
      expect(wrapper.get('[data-testid="submission-answer"]').text())
        .toContain('第 2 题 · 可同时选择哪些选项：选项 A、C');
      expect(wrapper.get('[data-testid="submission-answer"]').text()).not.toContain('q2');
    }
    if (type === 'TEXT' || type === 'FILE') {
      expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalled();
      expect(wrapper.get('[data-testid="reevaluation-unavailable"]').text()).toContain('不支持自动重评');
    }
  });

  it('degrades only the student name when LRN fails and never leaks the student identifier', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('学生姓名暂不可用');
    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('学生名单服务暂不可用');
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain('public class Main');
    expect(wrapper.text()).not.toContain('601');
  });

  it('hides the LRN fallback label when it embeds the internal student identifier', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress({
      students: [
        { studentId: 601, studentName: '学生 601', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('学生姓名暂不可用');
    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('未找到该提交对应的学生姓名');
    expect(wrapper.text()).not.toContain('601');
  });

  it('hides a pure numeric LRN fallback from the title and confirmation copy', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress({
      students: [
        { studentId: 601, studentName: '601', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('学生姓名暂不可用');
    expect(wrapper.text()).not.toContain('601');

    await wrapper.get('[data-action="save-review"]').trigger('submit');
    expect(confirmation).toHaveBeenCalledOnce();
    expect(String(confirmation.mock.calls[0]?.[0])).not.toContain('601');
  });

  it('refreshes submission status, scores, and review form after GET evaluation completes a pending judge', async () => {
    vi.mocked(homeworkApi.getHomeworkSubmission)
      .mockResolvedValueOnce(submission({
        evaluationStatus: 'PENDING',
        reviewStatus: 'NEED_REVIEW',
        autoScore: null,
        manualScore: null,
        finalScore: null
      }))
      .mockResolvedValueOnce(submission({
        evaluationStatus: 'ACCEPTED',
        reviewStatus: 'REVIEWED',
        autoScore: 100,
        manualScore: null,
        finalScore: 100
      }));
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 5
    }));

    const wrapper = mountView();
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="score-auto"]').text()).toContain('100');
    expect(wrapper.get('[data-testid="score-final"]').text()).toContain('100');
    expect((wrapper.get('[name="manualScore"]').element as HTMLInputElement).value).toBe('100');
    expect((wrapper.get('[name="finalScore"]').element as HTMLInputElement).value).toBe('100');
    expect(wrapper.text()).not.toContain('等待评测');
  });

  it.each([
    {
      label: '历史版本',
      homeworkDetail: homework(),
      submissionDetail: submission({ final: false }),
      expectedReason: '历史提交版本仅供查看'
    },
    {
      label: '已归档作业',
      homeworkDetail: homework({ status: 'ARCHIVED' }),
      submissionDetail: submission(),
      expectedReason: '已归档作业仅供查看'
    }
  ])('keeps $label read-only and exposes no review or re-evaluation mutation form', async ({
    homeworkDetail,
    submissionDetail,
    expectedReason
  }) => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homeworkDetail);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValue(submissionDetail);
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain('public class Main');
    expect(wrapper.get('[data-testid="review-logs"]').text()).toContain('首次人工复核');
    expect(wrapper.get('[data-testid="review-readonly"]').text()).toContain(expectedReason);
    expect(wrapper.find('[data-action="save-review"]').exists()).toBe(false);
    expect(wrapper.find('[data-action="reevaluate-submission"]').exists()).toBe(false);
    expect(homeworkApi.reviewHomeworkSubmission).not.toHaveBeenCalled();
    expect(homeworkApi.reevaluateHomeworkSubmission).not.toHaveBeenCalled();
  });

  it('requires a review reason and confirmation, exposes pending state, then refreshes the audit log', async () => {
    const saveRequest = deferred<HomeworkSubmissionDetail>();
    const confirmSpy = vi.spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);
    vi.mocked(homeworkApi.reviewHomeworkSubmission).mockReturnValueOnce(saveRequest.promise);
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs)
      .mockResolvedValueOnce(reviewLogs())
      .mockResolvedValueOnce(reviewLogs({ operationType: 'REVIEW', comment: '复核代码边界' }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reviewReason"]').setValue('');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    expect(homeworkApi.reviewHomeworkSubmission).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="review-error"]').text()).toContain('批阅说明不能为空');

    await wrapper.get('[name="manualScore"]').setValue('92');
    await wrapper.get('[name="finalScore"]').setValue('94');
    await wrapper.get('[name="reviewReason"]').setValue('复核代码边界');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    expect(homeworkApi.reviewHomeworkSubmission).not.toHaveBeenCalled();

    await wrapper.get('[data-action="save-review"]').trigger('submit');
    await flushPromises();
    expect(confirmSpy).toHaveBeenLastCalledWith(expect.stringContaining('林晓'));
    expect(confirmSpy).toHaveBeenLastCalledWith(expect.stringContaining('版本 3'));
    expect(wrapper.get('[data-action="save-review"] button').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-action="save-review"] button').text()).toContain('正在保存');

    saveRequest.resolve(submission({ manualScore: 92, finalScore: 94, comment: '复核代码边界' }));
    await flushPromises();

    expect(homeworkApi.reviewHomeworkSubmission).toHaveBeenCalledWith(301, {
      manualScore: 92,
      finalScore: 94,
      comment: '复核代码边界'
    });
    expect(wrapper.get('[data-testid="review-feedback"]').text()).toContain('批阅已保存');
    expect(wrapper.get('[data-testid="review-logs"]').text()).toContain('复核代码边界');
  });

  it('retains review input and enables retry after a confirmed save fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(homeworkApi.reviewHomeworkSubmission).mockRejectedValueOnce(new Error('批阅服务暂不可用'));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="manualScore"]').setValue('92');
    await wrapper.get('[name="finalScore"]').setValue('94');
    await wrapper.get('[name="reviewReason"]').setValue('保留这段批阅说明');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="review-error"]').text()).toContain('批阅服务暂不可用');
    expect((wrapper.get('[name="manualScore"]').element as HTMLInputElement).value).toBe('92');
    expect((wrapper.get('[name="finalScore"]').element as HTMLInputElement).value).toBe('94');
    expect((wrapper.get('[name="reviewReason"]').element as HTMLTextAreaElement).value).toBe('保留这段批阅说明');
    expect(wrapper.get('[data-action="save-review"] button').attributes('disabled')).toBeUndefined();
  });

  it('requires a rejudge reason and confirmation, exposes pending state, and refreshes the same version', async () => {
    const reevaluateRequest = deferred<HomeworkEvaluationResult>();
    const confirmSpy = vi.spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockReturnValueOnce(reevaluateRequest.promise);
    vi.mocked(homeworkApi.getHomeworkSubmission)
      .mockResolvedValueOnce(submission())
      .mockResolvedValueOnce(submission({ evaluationStatus: 'PENDING', autoScore: null }));
    vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs)
      .mockResolvedValueOnce(reviewLogs())
      .mockResolvedValueOnce(reviewLogs({ operationType: 'REJUDGE', reason: '修复测试数据后复核' }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    expect(wrapper.get('[data-testid="reevaluation-error"]').text()).toContain('重评理由不能为空');

    await wrapper.get('[name="reevaluationReason"]').setValue('修复测试数据后复核');
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    expect(homeworkApi.reevaluateHomeworkSubmission).not.toHaveBeenCalled();

    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    await flushPromises();
    expect(confirmSpy).toHaveBeenLastCalledWith(expect.stringContaining('版本 3'));
    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-action="reevaluate-submission"] button').text()).toContain('正在提交重评');

    reevaluateRequest.resolve(evaluation({ evaluationStatus: 'PENDING', score: 0, passedCases: 0 }));
    await flushPromises();

    expect(homeworkApi.reevaluateHomeworkSubmission).toHaveBeenCalledWith(301, '修复测试数据后复核');
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledTimes(3);
    expect(wrapper.get('[data-testid="reevaluation-feedback"]').text()).toContain('重评已成功');
    expect(wrapper.get('[data-testid="review-logs"]').text()).toContain('修复测试数据后复核');
  });

  it('retains the rejudge reason and review content when reevaluation fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockRejectedValueOnce(new Error('重评服务暂不可用'));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reevaluationReason"]').setValue('保留重评原因');
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="reevaluation-error"]').text()).toContain('重评服务暂不可用');
    expect((wrapper.get('[name="reevaluationReason"]').element as HTMLTextAreaElement).value).toBe('保留重评原因');
    expect(wrapper.get('[data-testid="submission-code"]').text()).toContain('public class Main');
    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeUndefined();
  });

  it('blocks reevaluation while a review mutation is pending', async () => {
    const reviewRequest = deferred<HomeworkSubmissionDetail>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(homeworkApi.reviewHomeworkSubmission).mockReturnValueOnce(reviewRequest.promise);
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="manualScore"]').setValue('92');
    await wrapper.get('[name="finalScore"]').setValue('94');
    await wrapper.get('[name="reviewReason"]').setValue('人工复核');
    await wrapper.get('[name="reevaluationReason"]').setValue('不应并发触发');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    expect(homeworkApi.reevaluateHomeworkSubmission).not.toHaveBeenCalled();

    reviewRequest.resolve(submission({ manualScore: 92, finalScore: 94, comment: '人工复核' }));
    await flushPromises();
  });

  it('blocks review while a re-evaluation mutation is pending', async () => {
    const reevaluateRequest = deferred<HomeworkEvaluationResult>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockReturnValueOnce(reevaluateRequest.promise);
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reevaluationReason"]').setValue('重评执行中');
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-action="save-review"] button').attributes('disabled')).toBeDefined();
    await wrapper.get('[name="reviewReason"]').setValue('不应并发保存');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    expect(homeworkApi.reviewHomeworkSubmission).not.toHaveBeenCalled();

    reevaluateRequest.resolve(evaluation());
    await flushPromises();
  });

  it('resets a pending review and old re-evaluation reason when route props switch submissions', async () => {
    const reviewRequest = deferred<HomeworkSubmissionDetail>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    configureSubmissionByIdMocks();
    vi.mocked(homeworkApi.reviewHomeworkSubmission).mockReturnValueOnce(reviewRequest.promise);
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reevaluationReason"]').setValue('上一位学生的重评理由');
    await wrapper.get('[name="reviewReason"]').setValue('延迟保存');
    await wrapper.get('[data-action="save-review"]').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-action="save-review"] button').attributes('disabled')).toBeDefined();

    await wrapper.setProps({ submissionId: 302 });
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(302);
    expect((wrapper.get('[name="reevaluationReason"]').element as HTMLTextAreaElement).value).toBe('');
    expect(wrapper.get('[data-action="save-review"] button').attributes('disabled')).toBeUndefined();

    reviewRequest.resolve(submission({ manualScore: 1, finalScore: 1 }));
    await flushPromises();
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('版本 4');
  });

  it('resets a pending re-evaluation when route props switch submissions', async () => {
    const reevaluateRequest = deferred<HomeworkEvaluationResult>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    configureSubmissionByIdMocks();
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockReturnValueOnce(reevaluateRequest.promise);
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reevaluationReason"]').setValue('延迟重评');
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeDefined();

    await wrapper.setProps({ submissionId: 302 });
    await flushPromises();

    expect((wrapper.get('[name="reevaluationReason"]').element as HTMLTextAreaElement).value).toBe('');
    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeUndefined();

    reevaluateRequest.resolve(evaluation());
    await flushPromises();
    expect(wrapper.get('[data-testid="review-version-context"]').text()).toContain('版本 4');
  });

  it('reports a successful re-evaluation with a failed page refresh as partial success and blocks repeat submission', async () => {
    let mutationCompleted = false;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockImplementation(async () => {
      if (mutationCompleted) {
        throw new Error('提交状态刷新失败');
      }
      return submission();
    });
    vi.mocked(homeworkApi.reevaluateHomeworkSubmission).mockImplementationOnce(async () => {
      mutationCompleted = true;
      return evaluation({ reevaluation: true });
    });
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="reevaluationReason"]').setValue('重新检查测试数据');
    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    await flushPromises();

    expect(wrapper.find('[data-testid="reevaluation-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="reevaluation-feedback"]').text())
      .toContain('重评已成功，页面刷新失败');
    expect(wrapper.get('[data-action="reevaluate-submission"] button').attributes('disabled')).toBeDefined();

    await wrapper.get('[data-action="reevaluate-submission"]').trigger('submit');
    expect(homeworkApi.reevaluateHomeworkSubmission).toHaveBeenCalledTimes(1);
  });

  it('shows a recoverable core error and retries the required review data', async () => {
    vi.mocked(homeworkApi.getHomeworkSubmission)
      .mockRejectedValueOnce(new Error('提交详情服务暂不可用'))
      .mockResolvedValueOnce(submission());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="page-state"]').text()).toContain('提交详情服务暂不可用');
    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledTimes(2);
    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledTimes(3);
    expect(wrapper.get('[data-testid="review-student-name"]').text()).toBe('林晓');
  });

  it('collapses the review grid to one column on phones', () => {
    const source = readFileSync('src/views/hwk/HomeworkSubmissionReviewView.vue', 'utf8');

    expect(source).toMatch(/\.homework-submission-review\s*\{[\s\S]*?width:\s*100%/);
    expect(source).toMatch(/@media\s*\(max-width:\s*760px\)/);
    expect(source).toMatch(
      /@media\s*\(max-width:\s*760px\)[\s\S]*?\.review-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/
    );
  });
});

function mountView() {
  return mount(HomeworkSubmissionReviewView, {
    props: { courseId: 101, homeworkId: 11, submissionId: 301 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function homework(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 11,
    courseId: 101,
    chapterId: 2,
    judgeConfigId: 7,
    title: '数据结构作业',
    description: '完成队列实现。',
    type: 'CODE',
    status: 'PUBLISHED',
    totalScore: 100,
    deadline: '2026-08-20T23:59:00',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    createdBy: 9,
    publishedAt: '2026-08-18T10:00:00',
    createdAt: '2026-08-17T10:00:00',
    updatedAt: '2026-08-18T10:00:00',
    languageLimitJson: '["java"]',
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    outputCompareMode: 'TRIM',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function submission(overrides: Partial<HomeworkSubmissionDetail> = {}): HomeworkSubmissionDetail {
  return {
    submissionId: 301,
    homeworkId: 11,
    studentId: 601,
    submitType: 'CODE',
    answerText: 'public class Main { public static void main(String[] args) {} }',
    answerJson: null,
    fileUrl: null,
    language: 'java',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    reviewStatus: 'REVIEWED',
    autoScore: 86,
    manualScore: 88,
    finalScore: 90,
    comment: '首次人工复核',
    version: 3,
    final: true,
    submittedAt: '2026-08-20T10:30:00',
    ...overrides
  };
}

function evaluation(overrides: Partial<HomeworkEvaluationResult> = {}): HomeworkEvaluationResult {
  return {
    evaluationId: 701,
    submissionId: 301,
    evaluationStatus: 'ACCEPTED',
    score: 86,
    passedCases: 4,
    totalCases: 5,
    durationMs: 128,
    errorMessage: null,
    feedback: '通过 4 / 5 个测试用例',
    compileLog: '编译成功',
    runLog: '用例 5 输出不一致',
    reevaluation: false,
    triggeredBy: null,
    startedAt: '2026-08-20T10:30:01',
    finishedAt: '2026-08-20T10:30:02',
    ...overrides
  };
}

function reviewLog(overrides: Partial<HomeworkReviewLog> = {}): HomeworkReviewLog {
  return {
    id: 801,
    submissionId: 301,
    homeworkId: 11,
    studentId: 601,
    operationType: 'REVIEW',
    oldScore: 86,
    newScore: 90,
    comment: '首次人工复核',
    operatorId: 9,
    reason: null,
    createdAt: '2026-08-20T11:00:00',
    ...overrides
  };
}

function reviewLogs(overrides: Partial<HomeworkReviewLog> = {}) {
  return [reviewLog(overrides)];
}

function courseProgress(
  overrides: Partial<LearningCourseProgressAggregate> = {}
): LearningCourseProgressAggregate {
  return {
    courseId: 101,
    courseName: '数据结构',
    studentCount: 1,
    averageProgressPercent: 80,
    students: [
      { studentId: 601, studentName: '林晓', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null }
    ],
    ...overrides
  };
}

function objectiveQuestions(): HomeworkDetail['questions'] {
  return [
    {
      id: 502,
      homeworkId: 11,
      questionType: 'MULTIPLE_CHOICE',
      stem: '可同时选择哪些选项',
      optionsJson: '["A","B","C"]',
      answerJson: '["A","C"]',
      score: 50,
      sortOrder: 2
    },
    {
      id: 501,
      homeworkId: 11,
      questionType: 'SINGLE_CHOICE',
      stem: '队列的基本特征',
      optionsJson: '["A","B"]',
      answerJson: '["B"]',
      score: 50,
      sortOrder: 1
    }
  ];
}

function configureSubmissionByIdMocks() {
  vi.mocked(homeworkApi.getHomeworkSubmission).mockImplementation(async (submissionId) => (
    submissionId === 302
      ? submission({ submissionId: 302, studentId: 602, version: 4 })
      : submission()
  ));
  vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockImplementation(async (submissionId) => (
    evaluation({ submissionId })
  ));
  vi.mocked(homeworkApi.getHomeworkSubmissionReviewLogs).mockImplementation(async (submissionId) => (
    reviewLogs({ submissionId, studentId: submissionId === 302 ? 602 : 601 })
  ));
  vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress({
    studentCount: 2,
    students: [
      { studentId: 601, studentName: '林晓', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null },
      { studentId: 602, studentName: '周然', progressPercent: 70, status: 'IN_PROGRESS', updatedAt: null }
    ]
  }));
}

function routeTarget(target: string | Record<string, unknown> | undefined) {
  return typeof target === 'object' && target !== null ? target : {};
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
