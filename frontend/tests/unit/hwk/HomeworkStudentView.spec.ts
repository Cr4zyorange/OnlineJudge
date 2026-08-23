import { config, flushPromises, mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import { currentUser } from '../../../src/app/runtimeContext';
import type {
  HomeworkAttachmentUpload,
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkSubmissionSummary
} from '../../../src/types/hwk';

const attachmentApiMocks = vi.hoisted(() => ({
  uploadHomeworkAttachment: vi.fn(),
  getHomeworkAttachment: vi.fn(),
  deleteHomeworkAttachment: vi.fn()
}));

vi.mock('../../../src/api/hwk/homeworks', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../src/api/hwk/homeworks')>();
  return {
    ...actual,
    getHomeworkDetail: vi.fn(),
    getHomeworkSubmissionEvaluation: vi.fn(),
    listMyHomeworkSubmissions: vi.fn(),
    submitHomework: vi.fn(),
    ...attachmentApiMocks
  };
});
vi.mock('../../../src/api/lrn/learningProgress');
vi.mock('../../../src/api/lrn/learningRecords');

config.global.stubs = {
  ...config.global.stubs,
  RouterLink: RouterLinkStub
};

describe('HomeworkStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useRealTimers();
    window.sessionStorage.clear();
    currentUser.value = null;
    window.history.replaceState({}, '', '/courses/101/homeworks/11?role=student');
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValue([]);
    vi.mocked(learningRecordsApi.reportLearningRecord).mockResolvedValue({
      id: 1,
      courseId: 101,
      courseName: '软件工程基础',
      sourceModule: 'HWK',
      sourceId: 11,
      actionType: 'ACCESS',
      durationSeconds: 0,
      startedAt: '2026-06-01 10:00:00',
      endedAt: '2026-06-01 10:00:00'
    });
    vi.mocked(learningProgressApi.saveLearningProgress).mockResolvedValue({
      progressId: 1,
      courseId: 101,
      courseName: '软件工程基础',
      chapterId: 1001,
      chapterName: '课程导论',
      sourceModule: 'HWK',
      sourceId: 11,
      progressPercent: 20,
      lastPosition: 'homeworkId=11',
      status: 'IN_PROGRESS',
      continueUrl: '/courses/101/homeworks/11?role=student',
      updatedAt: '2026-06-01 10:00:00'
    });
  });

  it('loads published homework detail and submits a text answer', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 91,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(11);
    expect(wrapper.text()).toContain('HWK02 text homework');
    expect(wrapper.text()).toContain('Explain your algorithm.');
    expect(wrapper.text()).toContain('文本作业');
    expect(wrapper.text()).toContain('已发布');
    expect(wrapper.text()).not.toContain('TEXT');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'HWK',
      sourceId: 11,
      actionType: 'ACCESS'
    }));

    await wrapper.get('[name="answerText"]').setValue('Use dynamic programming.');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      answerText: 'Use dynamic programming.'
    }));
    expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalled();
    expect(learningProgressApi.saveLearningProgress).toHaveBeenLastCalledWith({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'HWK',
      sourceId: 11,
      progressPercent: 100,
      lastPosition: 'homeworkId=11;submitted=91'
    });
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenLastCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'HWK',
      sourceId: 11,
      actionType: 'SUBMIT'
    }));
    expect(wrapper.text()).toContain('提交编号 91');
    expect(wrapper.text()).toContain('提交状态：已提交');
    expect(wrapper.text()).toContain('评测状态：未评测');
    expect(wrapper.text()).toContain('批阅状态：待批阅');
    expect(wrapper.text()).not.toContain('Submission');
    expect(wrapper.text()).not.toContain('SUBMITTED');
    expect(wrapper.text()).not.toContain('UNREVIEWED');
  });

  it('restores the latest submission and visible evaluation when reopening the homework', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      title: '数据结构第二次作业',
      type: 'CODE',
      languageLimitJson: '["python"]'
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      {
        submissionId: 95,
        homeworkId: 11,
        studentId: 601,
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'WRONG_ANSWER',
        reviewStatus: 'UNREVIEWED',
        version: 2,
        final: false,
        submittedAt: '2026-06-01T09:00:00'
      },
      {
        submissionId: 96,
        homeworkId: 11,
        studentId: 601,
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        reviewStatus: 'REVIEWED',
        finalScore: 98,
        version: 3,
        final: true,
        submittedAt: '2026-06-01T10:00:00'
      }
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce({
      evaluationId: 802,
      submissionId: 96,
      evaluationStatus: 'ACCEPTED',
      score: 98,
      passedCases: 8,
      totalCases: 8,
      durationMs: 42,
      feedback: '全部测试通过',
      reevaluation: false,
      startedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:01'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(homeworkApi.listMyHomeworkSubmissions).toHaveBeenCalledWith(11);
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(96);
    expect(wrapper.get('[data-testid="homework-status-summary"]').text()).toContain('已发布');
    expect(wrapper.get('[data-testid="homework-deadline-summary"]').text()).toContain('截止时间');
    expect(wrapper.get('[data-testid="homework-submission-summary"]').text()).toContain('版本 3');
    expect(wrapper.get('[data-testid="homework-submission-summary"]').text()).toContain('已提交');
    expect(wrapper.get('[data-testid="homework-submission-summary"]').text()).toContain('通过');
    expect(wrapper.get('[data-testid="homework-history-link"]').attributes('href'))
      .toBe('/courses/101/homeworks/11/submissions');
    expect(wrapper.find('.homework-student__workspace').exists()).toBe(true);
    expect(wrapper.find('.homework-student__submission-pane').exists()).toBe(true);
    expect(wrapper.get('[aria-label="评测结果"]').text()).toContain('全部测试通过');
  });

  it('keeps evaluation and scores hidden until the homework permits result visibility', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      showEvaluationBeforePublish: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([{
      submissionId: 97,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      reviewStatus: 'REVIEWED',
      finalScore: 99,
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    }]);

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="homework-submission-summary"]').text()).toContain('评测结果待发布');
    expect(wrapper.find('[aria-label="评测结果"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).not.toContain('得分 99');
  });

  it('keeps published evaluation and final score visible after the homework is archived', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      status: 'ARCHIVED',
      showEvaluationBeforePublish: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([{
      submissionId: 98,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      reviewStatus: 'REVIEWED',
      finalScore: 99,
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    }]);
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce({
      evaluationId: 803,
      submissionId: 98,
      evaluationStatus: 'ACCEPTED',
      score: 99,
      passedCases: 1,
      totalCases: 1,
      reevaluation: false,
      startedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:01'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'detail' }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(98);
    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).toContain('得分 99');
  });

  it('blocks submission after the deadline when late submission is disabled', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      deadline: '2020-06-30T23:59:59',
      allowLateSubmit: false
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-status-summary"]').text()).toContain('已截止');
    expect(wrapper.text()).toContain('已超过截止时间，当前不允许提交');
    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeDefined();

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已超过截止时间，当前不允许提交');
  });

  it('renders localized page loading and failure states', async () => {
    let rejectDetail: ((reason?: unknown) => void) | undefined;
    vi.mocked(homeworkApi.getHomeworkDetail).mockImplementationOnce(() => new Promise((_, reject) => {
      rejectDetail = reject;
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });

    expect(wrapper.get('[data-testid="homework-page-loading"]').text()).toContain('正在加载作业');

    rejectDetail?.(null);
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-page-error"]').text()).toContain('作业详情加载失败');
  });

  it('shows validation errors before sending an empty text submission', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请填写文本答案');
    expect(wrapper.text()).not.toContain('附件 ID');
    expect(wrapper.text()).not.toContain('Answer content is required');
  });

  it('renders score published homework metadata with localized labels', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      status: 'SCORE_PUBLISHED'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('文本作业');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).not.toContain('TEXT');
    expect(wrapper.text()).not.toContain('SCORE_PUBLISHED');
  });

  it('renders objective homework options and submission fields without implementation labels', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      title: 'HWK02 objective homework',
      type: 'OBJECTIVE',
      questions: [{
        id: 301,
        homeworkId: 11,
        questionType: 'SINGLE_CHOICE',
        stem: 'Which operation is O(1)?',
        optionsJson: '["A. stack push","B. full table scan"]',
        score: 100,
        sortOrder: 1
      }]
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('A. stack push');
    expect(wrapper.text()).toContain('B. full table scan');
    expect(wrapper.text()).toContain('客观题答案');
    expect(wrapper.text()).not.toContain('客观题答案 JSON');
    expect(wrapper.text()).not.toContain('["A. stack push","B. full table scan"]');

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请填写客观题答案');
    expect(wrapper.text()).not.toContain('请填写客观题答案 JSON');
  });

  it('renders configured code languages and submits only the selected language', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      title: 'HWK02 code homework',
      type: 'CODE',
      languageLimitJson: '["python","java"]',
      testCases: [{
        id: 1,
        homeworkId: 11,
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 92,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'PENDING',
      reviewStatus: 'NEED_REVIEW',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    const languageSelect = wrapper.get('select[name="language"]');
    const options = languageSelect.findAll('option').map((option) => option.text());
    expect(options).toEqual(['python', 'java']);

    await languageSelect.setValue('java');
    await wrapper.get('[name="codeText"]').setValue('public class Main {}');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      codeText: 'public class Main {}',
      language: 'java'
    }));
    expect(wrapper.text()).toContain('评测状态：等待评测');
    expect(wrapper.text()).toContain('批阅状态：需批阅');
    expect(wrapper.text()).not.toContain('PENDING');
    expect(wrapper.text()).not.toContain('NEED_REVIEW');
  });

  it('loads and displays the evaluation result after submitting code homework', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      title: 'HWK04 code evaluation',
      type: 'CODE',
      languageLimitJson: '["python"]',
      testCases: [{
        id: 1,
        homeworkId: 11,
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 94,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'PENDING',
      reviewStatus: 'NEED_REVIEW',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce({
      evaluationId: 801,
      submissionId: 94,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 1,
      totalCases: 1,
      durationMs: 50,
      errorMessage: null,
      feedback: 'accepted',
      compileLog: null,
      runLog: null,
      reevaluation: false,
      triggeredBy: null,
      startedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:01'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    await wrapper.get('[name="codeText"]').setValue('print(input())');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(94);
    expect(wrapper.text()).toContain('评测结果');
    expect(wrapper.text()).toContain('通过');
    expect(wrapper.text()).toContain('100');
    expect(wrapper.text()).toContain('通过用例 1 / 1');
    expect(wrapper.text()).toContain('accepted');
    expect(wrapper.text()).not.toContain('Evaluation result');
    expect(wrapper.text()).not.toContain('ACCEPTED');
  });

  it('submits the default language when code homework has a single configured language', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      title: 'HWK02 python homework',
      type: 'CODE',
      languageLimitJson: '["python"]',
      testCases: [{
        id: 1,
        homeworkId: 11,
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 93,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'PENDING',
      reviewStatus: 'NEED_REVIEW',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11
      }
    });
    await flushPromises();

    const languageSelect = wrapper.get('select[name="language"]');
    expect(languageSelect.findAll('option').map((option) => option.text())).toEqual(['python']);

    await wrapper.get('[name="codeText"]').setValue('print(input())');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      codeText: 'print(input())',
      language: 'python'
    }));
    expect(wrapper.text()).toContain('评测状态：等待评测');
    expect(wrapper.text()).not.toContain('PENDING');
  });

  it('records homework progress when a student opens and completes homework', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      id: 501,
      chapterId: 1001,
      title: '作业一',
      description: '完成第一章练习'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 501
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('作业一');
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledWith({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'HWK',
      sourceId: 501,
      progressPercent: 20,
      lastPosition: 'homeworkId=501'
    });

    await wrapper.get('[data-testid="complete-homework"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.saveLearningProgress).toHaveBeenLastCalledWith({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'HWK',
      sourceId: 501,
      progressPercent: 100,
      lastPosition: 'homeworkId=501;completed=true'
    });
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenLastCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'HWK',
      sourceId: 501,
      actionType: 'COMPLETE'
    }));
    expect(wrapper.text()).toContain('已记录完成进度');
  });

  it('shows the restored homework breakpoint from the resume query', async () => {
    window.history.replaceState({}, '', '/courses/101/homeworks/501?role=student&resume=question%3D2');
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      id: 501,
      title: '作业一',
      description: '完成第一章练习'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 501
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('已恢复上次断点：question=2');
  });

  it('keeps the detail route read-only and links every next step through the student flow', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 11,
        mode: 'detail'
      },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    });
    await flushPromises();

    expect(wrapper.find('form').exists()).toBe(false);
    expect(wrapper.get('[data-testid="homework-submit-link"]').attributes('href'))
      .toBe('/courses/101/homeworks/11/submit');
    expect(wrapper.get('[data-testid="homework-history-link"]').attributes('href'))
      .toBe('/courses/101/homeworks/11/submissions');
    expect(wrapper.get('[data-testid="homework-result-link"]').attributes('href'))
      .toBe('/courses/101/homeworks/11/result');
  });

  it('uses structured objective controls and serializes answers with stable question keys', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      type: 'OBJECTIVE',
      questions: [
        {
          id: 301,
          homeworkId: 11,
          questionType: 'SINGLE_CHOICE',
          stem: '1 + 1 = ?',
          optionsJson: '["1","2"]',
          score: 40,
          sortOrder: 1
        },
        {
          id: 302,
          homeworkId: 11,
          questionType: 'MULTIPLE_CHOICE',
          stem: '选出质数',
          optionsJson: '{"A":"2","B":"3","C":"4"}',
          score: 60,
          sortOrder: 2
        }
      ]
    }));
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 99,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      reviewStatus: 'REVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockResolvedValueOnce({
      evaluationId: 809,
      submissionId: 99,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 2,
      totalCases: 2,
      reevaluation: false,
      startedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:01'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.find('textarea[name="answerJson"]').exists()).toBe(false);
    const firstAnswer = wrapper.get('input[name="objective-301"][value="2"]');
    await firstAnswer.setValue(true);
    await wrapper.get('input[name="objective-302"][value="B"]').setValue(true);
    await wrapper.get('input[name="objective-302"][value="A"]').setValue(true);
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      answerJson: JSON.stringify({ q1: ['2'], q2: ['A', 'B'] })
    }));
  });

  it('renders boolean controls for both TRUE_FALSE and JUDGE objective question aliases', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      type: 'OBJECTIVE',
      questions: [
        {
          id: 303,
          homeworkId: 11,
          questionType: 'TRUE_FALSE',
          stem: '二叉树可能为空。',
          optionsJson: null,
          score: 50,
          sortOrder: 1
        },
        {
          id: 304,
          homeworkId: 11,
          questionType: 'JUDGE',
          stem: '所有图都是连通图。',
          optionsJson: null,
          score: 50,
          sortOrder: 2
        }
      ]
    }));
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 101,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.get('input[name="objective-303"][value="true"]').setValue(true);
    await wrapper.get('input[name="objective-304"][value="false"]').setValue(true);
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      answerJson: JSON.stringify({ q1: ['true'], q2: ['false'] })
    }));
  });

  it('uses a real file picker and exposes the completed attachment upload workflow', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      type: 'FILE'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.find('input[name="fileIds"]').exists()).toBe(false);
    expect(wrapper.get('input[name="homeworkFile"]').attributes('type')).toBe('file');
    expect(wrapper.find('[data-testid="homework-file-blocker"]').exists()).toBe(false);
    expect(wrapper.get('[data-action="upload-homework-file"]').text()).toContain('上传附件');
    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeDefined();
  });

  it('uploads one FILE attachment and submits only the returned opaque file id', async () => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce(submissionReceipt());
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    const file = new File(['private report bytes'], '课程报告.pdf', { type: 'application/pdf' });
    await chooseFile(wrapper, file);
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();

    expect(attachmentApiMocks.uploadHomeworkAttachment).toHaveBeenCalledWith(11, file);
    expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('课程报告.pdf');
    const storageKey = 'oj:hwk-file-upload:v1:601:101:11';
    const persisted = window.sessionStorage.getItem(storageKey);
    expect(persisted).toContain('85c3d5a0-2140-4d80-9000-000000000011');
    expect(persisted).toContain('课程报告.pdf');
    expect(persisted).not.toContain('private report bytes');
    expect(persisted).not.toContain('fileUrl');
    expect(persisted).not.toContain('storageKey');

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      fileIds: ['85c3d5a0-2140-4d80-9000-000000000011']
    }));
    expect(window.sessionStorage.getItem(storageKey)).toBeNull();
    expect((wrapper.get('input[name="homeworkFile"]').element as HTMLInputElement).value).toBe('');
  });

  it('treats an uploaded but unsubmitted attachment as unsaved work when the tab closes', async () => {
    currentUser.value = studentUser();
    const addListener = vi.spyOn(window, 'addEventListener');
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['unsubmitted'], '待提交报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();

    const unloadHandler = addListener.mock.calls.find(([eventName]) => eventName === 'beforeunload')?.[1];
    expect(unloadHandler).toEqual(expect.any(Function));
    const event = new Event('beforeunload', { cancelable: true });
    (unloadHandler as EventListener)(event);

    expect(event.defaultPrevented).toBe(true);
    expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toContain('000000000011');
    wrapper.unmount();
  });

  it('retains the selected File after upload failure and retries the same file', async () => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment
      .mockRejectedValueOnce(new Error('附件存储服务暂不可用'))
      .mockResolvedValueOnce(attachmentUpload());
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    const file = new File(['retry bytes'], '可重试报告.pdf', { type: 'application/pdf' });
    await chooseFile(wrapper, file);
    const upload = wrapper.get('[data-action="upload-homework-file"]');
    await upload.trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text()).toContain('附件存储服务暂不可用');
    expect((wrapper.get('input[name="homeworkFile"]').element as HTMLInputElement).files?.[0]).toBe(file);

    await upload.trigger('click');
    await flushPromises();
    expect(attachmentApiMocks.uploadHomeworkAttachment).toHaveBeenNthCalledWith(2, 11, file);
    expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('课程报告.pdf');
  });

  it.each([
    ['HWK_4131', '附件大小超过 10 MiB，请重新选择较小的文件'],
    ['HWK_4151', '不支持该附件类型，请重新选择允许的文件'],
    ['HWK_4005', '附件为空或文件内容无效，请重新选择有效文件']
  ])('maps deterministic upload error %s and requires a new file selection', async (code, expectedMessage) => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockRejectedValueOnce(codedError(code, 'backend english error'));
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['server validated bytes'], '待校验报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text()).toContain(expectedMessage);
    expect(wrapper.text()).not.toContain('backend english error');
    expect(wrapper.text()).not.toContain('已选择：待校验报告.pdf');
    expect(wrapper.get('[data-action="upload-homework-file"]').attributes('disabled')).toBeDefined();
  });

  it('shows the FILE constraints and rejects an oversized selection before upload', async () => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    const input = wrapper.get('input[name="homeworkFile"]');
    expect(input.attributes('accept')).toBe('.pdf,.zip,.docx,.xlsx,.pptx,.txt,.md,.csv,.png,.jpg,.jpeg');
    expect(wrapper.text()).toContain('单个附件，最大 10 MiB');

    const oversized = new File([new Uint8Array(10 * 1024 * 1024 + 1)], '过大报告.pdf', {
      type: 'application/pdf'
    });
    await chooseFile(wrapper, oversized);

    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text())
      .toContain('附件大小超过 10 MiB');
    expect(wrapper.text()).not.toContain('已选择：过大报告.pdf');
    expect(attachmentApiMocks.uploadHomeworkAttachment).not.toHaveBeenCalled();
  });

  it.each([
    ['HWK_4042', '附件不存在或不属于当前作业，请重新选择并上传', true],
    ['HWK_4091', '附件已过期或不可用，请重新选择并上传', true],
    ['HWK_4092', '附件已被提交绑定，不能重复使用，请重新选择并上传', true],
    ['HWK_5002', '附件存储暂时不可用，请稍后重试提交', false]
  ])('handles FILE submission attachment error %s without unsafe DELETE', async (code, expectedMessage, shouldClear) => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    vi.mocked(homeworkApi.submitHomework).mockRejectedValueOnce(codedError(code, 'attachment conflict'));
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['report'], '课程报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-submit-error"]').text()).toContain(expectedMessage);
    expect(attachmentApiMocks.deleteHomeworkAttachment).not.toHaveBeenCalled();
    if (shouldClear) {
      expect(wrapper.find('[data-testid="homework-file-name"]').exists()).toBe(false);
      expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toBeNull();
      expect(wrapper.get('input[name="homeworkFile"]').attributes('disabled')).toBeUndefined();
    } else {
      expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('课程报告.pdf');
      expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toContain('000000000011');
      expect(wrapper.get('input[name="homeworkFile"]').attributes('disabled')).toBeDefined();
    }
  });

  it('expires an uploaded attachment in place without attempting DELETE', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-23T09:59:59.900+08:00'));
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload({
      expiresAt: '2026-08-23T10:00:00+08:00'
    }));
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['report'], '课程报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="homework-file-name"]').exists()).toBe(true);

    await vi.advanceTimersByTimeAsync(101);

    expect(wrapper.find('[data-testid="homework-file-name"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text()).toContain('附件已过期，请重新选择并上传');
    expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toBeNull();
    expect(attachmentApiMocks.deleteHomeworkAttachment).not.toHaveBeenCalled();
  });

  it('clears a server-gone upload after one failed DELETE so removal cannot loop', async () => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    attachmentApiMocks.deleteHomeworkAttachment.mockRejectedValueOnce(codedError('HWK_4092', 'already bound'));
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['report'], '课程报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-action="remove-homework-file"]').trigger('click');
    await flushPromises();

    expect(attachmentApiMocks.deleteHomeworkAttachment).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-action="remove-homework-file"]').exists()).toBe(false);
    expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toBeNull();
  });

  it('removes an unbound upload through the server before clearing local metadata', async () => {
    currentUser.value = studentUser();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    attachmentApiMocks.deleteHomeworkAttachment.mockResolvedValueOnce(null);
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['remove me'], '课程报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-action="remove-homework-file"]').trigger('click');
    await flushPromises();

    expect(attachmentApiMocks.deleteHomeworkAttachment).toHaveBeenCalledWith(
      11,
      '85c3d5a0-2140-4d80-9000-000000000011'
    );
    expect(window.sessionStorage.getItem('oj:hwk-file-upload:v1:601:101:11')).toBeNull();
    expect(wrapper.find('[data-testid="homework-file-name"]').exists()).toBe(false);
  });

  it('restores only safe upload metadata after a server GET revalidation', async () => {
    currentUser.value = studentUser();
    const storageKey = 'oj:hwk-file-upload:v1:601:101:11';
    window.sessionStorage.setItem(storageKey, JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      attachment: attachmentUpload()
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.getHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(attachmentApiMocks.getHomeworkAttachment).toHaveBeenCalledWith(
      11,
      '85c3d5a0-2140-4d80-9000-000000000011'
    );
    expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('课程报告.pdf');
    expect(wrapper.get('[data-testid="homework-file-restore-status"]').text()).toContain('已恢复');
    expect(attachmentApiMocks.uploadHomeworkAttachment).not.toHaveBeenCalled();
  });

  it('forgets a restored file id when server revalidation rejects it', async () => {
    currentUser.value = studentUser();
    const storageKey = 'oj:hwk-file-upload:v1:601:101:11';
    window.sessionStorage.setItem(storageKey, JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      attachment: attachmentUpload()
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.getHomeworkAttachment.mockRejectedValueOnce(
      codedError('HWK_4042', '附件不存在或不属于当前学生')
    );

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(attachmentApiMocks.getHomeworkAttachment).toHaveBeenCalledWith(
      11,
      '85c3d5a0-2140-4d80-9000-000000000011'
    );
    expect(window.sessionStorage.getItem(storageKey)).toBeNull();
    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text())
      .toContain('附件不存在或不属于当前学生');
    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeDefined();
  });

  it('preserves restored file metadata when server revalidation has a retryable storage failure', async () => {
    currentUser.value = studentUser();
    const storageKey = 'oj:hwk-file-upload:v1:601:101:11';
    window.sessionStorage.setItem(storageKey, JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      attachment: attachmentUpload()
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({ type: 'FILE' }));
    attachmentApiMocks.getHomeworkAttachment.mockRejectedValueOnce(
      codedError('HWK_5002', 'attachment storage failure')
    );

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(attachmentApiMocks.getHomeworkAttachment).toHaveBeenCalledWith(
      11,
      '85c3d5a0-2140-4d80-9000-000000000011'
    );
    expect(window.sessionStorage.getItem(storageKey)).toContain('85c3d5a0-2140-4d80-9000-000000000011');
    expect(wrapper.get('[data-testid="homework-file-upload-error"]').text())
      .toContain('附件存储暂时不可用，已保留恢复信息，请稍后刷新页面重试验证');
    expect(wrapper.find('[data-testid="homework-file-name"]').exists()).toBe(false);
    expect(wrapper.get('input[name="homeworkFile"]').attributes('disabled')).toBeUndefined();
  });

  it('restores a fresh draft and preserves it when submission fails', async () => {
    vi.useFakeTimers();
    window.sessionStorage.setItem('oj:draft:v1:anonymous:101:HWK:11', JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      homeworkType: 'TEXT',
      answerText: '草稿中的动态规划解法',
      objectiveAnswers: {},
      codeText: '',
      language: ''
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework).mockRejectedValueOnce(new Error('network failure'));

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('草稿中的动态规划解法');
    expect(wrapper.get('[data-testid="homework-draft-status"]').text()).toContain('已恢复');

    await wrapper.get('form').trigger('submit');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(500);

    expect(wrapper.text()).toContain('作业提交失败');
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11'))
      .toContain('草稿中的动态规划解法');
  });

  it('auto-saves edited text after 500ms and clears the draft only after a successful submission', async () => {
    vi.useFakeTimers();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce({
      submissionId: 100,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.get('[name="answerText"]').setValue('待提交的文本草稿');
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11')).toBeNull();
    await vi.advanceTimersByTimeAsync(500);
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11'))
      .toContain('待提交的文本草稿');

    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11')).toBeNull();
  });

  it('offers a retry action when loading the homework detail fails', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockRejectedValueOnce(new Error('network failure'))
      .mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11 }
    });
    await flushPromises();

    await wrapper.get('[data-testid="homework-load-retry"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('HWK02 text homework');
  });

  it('restores the draft when the router reuses the detail component for the submit route', async () => {
    window.sessionStorage.setItem('oj:draft:v1:anonymous:101:HWK:11', JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      homeworkType: 'TEXT',
      answerText: '路由切换后应恢复的草稿',
      objectiveAnswers: {},
      codeText: '',
      language: ''
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'detail' }
    });
    await flushPromises();
    expect(wrapper.find('form').exists()).toBe(false);

    await wrapper.setProps({ mode: 'submit' });
    await flushPromises();

    await vi.waitFor(() => {
      expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
        .toBe('路由切换后应恢复的草稿');
    });
  });

  it('removes the unload guard when a reused component leaves submit mode', async () => {
    const removeListener = vi.spyOn(window, 'removeEventListener');
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.setProps({ mode: 'detail' });
    await flushPromises();

    expect(removeListener).toHaveBeenCalledWith('beforeunload', expect.any(Function));
    wrapper.unmount();
  });

  it('reloads the homework when a reused route changes its homework id', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homeworkDetail({ id: 11, title: '第一份作业' }))
      .mockResolvedValueOnce(homeworkDetail({ id: 12, title: '第二份作业' }));

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'detail' }
    });
    await flushPromises();
    expect(wrapper.text()).toContain('第一份作业');

    await wrapper.setProps({ homeworkId: 12 });
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenLastCalledWith(12);
    expect(wrapper.text()).toContain('第二份作业');
  });

  it('ignores a stale evaluation response after route reuse loads another homework and submission', async () => {
    const staleEvaluation = deferred<HomeworkEvaluationResult>();
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homeworkDetail({ id: 11, title: '第一份作业' }))
      .mockResolvedValueOnce(homeworkDetail({ id: 12, title: '第二份作业' }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions)
      .mockResolvedValueOnce([{
        submissionId: 111,
        homeworkId: 11,
        studentId: 601,
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'RUNNING',
        reviewStatus: 'UNREVIEWED',
        version: 1,
        final: true,
        submittedAt: '2026-06-01T09:00:00'
      }])
      .mockResolvedValueOnce([{
        submissionId: 222,
        homeworkId: 12,
        studentId: 601,
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'NONE',
        reviewStatus: 'UNREVIEWED',
        version: 1,
        final: true,
        submittedAt: '2026-06-01T10:00:00'
      }]);
    vi.mocked(homeworkApi.getHomeworkSubmissionEvaluation).mockReturnValueOnce(staleEvaluation.promise);

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'detail' }
    });
    await flushPromises();
    expect(homeworkApi.getHomeworkSubmissionEvaluation).toHaveBeenCalledWith(111);

    await wrapper.setProps({ homeworkId: 12 });
    await flushPromises();
    expect(wrapper.text()).toContain('第二份作业');
    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).toContain('提交编号 222');

    staleEvaluation.resolve({
      evaluationId: 811,
      submissionId: 111,
      evaluationStatus: 'ACCEPTED',
      score: 17,
      passedCases: 1,
      totalCases: 1,
      feedback: '旧作业迟到结果',
      reevaluation: false,
      startedAt: '2026-06-01T09:00:00',
      finishedAt: '2026-06-01T09:00:01'
    });
    await flushPromises();

    expect(wrapper.text()).toContain('第二份作业');
    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).toContain('提交编号 222');
    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).toContain('评测状态：未评测');
    expect(wrapper.text()).not.toContain('旧作业迟到结果');
  });

  it('ignores a stale submit response after route reuse switches to another homework', async () => {
    const staleSubmission = deferred<HomeworkSubmissionSummary>();
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homeworkDetail({ id: 11, title: '第一份待提交作业' }))
      .mockResolvedValueOnce(homeworkDetail({ id: 12, title: '第二份待提交作业' }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([]);
    vi.mocked(homeworkApi.submitHomework).mockReturnValueOnce(staleSubmission.promise);

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.get('[name="answerText"]').setValue('第一份作业的答案');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.any(Object));

    window.sessionStorage.setItem('oj:draft:v1:anonymous:101:HWK:12', JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      homeworkType: 'TEXT',
      answerText: '第二份作业不能被清除的草稿',
      objectiveAnswers: {},
      codeText: '',
      language: ''
    }));
    await wrapper.setProps({ homeworkId: 12 });
    await flushPromises();

    expect(wrapper.text()).toContain('第二份待提交作业');
    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('第二份作业不能被清除的草稿');

    staleSubmission.resolve({
      submissionId: 111,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'RUNNING',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T09:00:00'
    });
    await flushPromises();

    expect(wrapper.text()).toContain('第二份待提交作业');
    expect(wrapper.find('[data-testid="homework-latest-submission"]').exists()).toBe(false);
    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('第二份作业不能被清除的草稿');
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:12'))
      .toContain('第二份作业不能被清除的草稿');
    expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalledWith(111);
    expect(learningProgressApi.saveLearningProgress).not.toHaveBeenCalledWith(expect.objectContaining({
      sourceId: 12,
      lastPosition: 'homeworkId=12;submitted=111'
    }));
    expect(learningRecordsApi.reportLearningRecord).not.toHaveBeenCalledWith(expect.objectContaining({
      sourceId: 12,
      actionType: 'SUBMIT'
    }));
  });

  it('clears only the submitted FILE upload metadata when its success response arrives after route reuse', async () => {
    currentUser.value = studentUser();
    const staleSubmission = deferred<HomeworkSubmissionSummary>();
    const nextAttachment = attachmentUpload({
      fileId: '85c3d5a0-2140-4d80-9000-000000000012',
      originalFilename: '第二份报告.pdf'
    });
    const oldStorageKey = 'oj:hwk-file-upload:v1:601:101:11';
    const nextStorageKey = 'oj:hwk-file-upload:v1:601:101:12';
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homeworkDetail({ id: 11, type: 'FILE', title: '第一份文件作业' }))
      .mockResolvedValueOnce(homeworkDetail({ id: 12, type: 'FILE', title: '第二份文件作业' }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([]);
    attachmentApiMocks.uploadHomeworkAttachment.mockResolvedValueOnce(attachmentUpload());
    attachmentApiMocks.getHomeworkAttachment.mockResolvedValueOnce(nextAttachment);
    vi.mocked(homeworkApi.submitHomework).mockReturnValueOnce(staleSubmission.promise);

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await chooseFile(wrapper, new File(['first report'], '第一份报告.pdf', { type: 'application/pdf' }));
    await wrapper.get('[data-action="upload-homework-file"]').trigger('click');
    await flushPromises();
    expect(window.sessionStorage.getItem(oldStorageKey)).toContain('000000000011');

    await wrapper.get('form').trigger('submit');
    await flushPromises();
    window.sessionStorage.setItem(nextStorageKey, JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      attachment: nextAttachment
    }));
    await wrapper.setProps({ homeworkId: 12 });
    await flushPromises();
    expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('第二份报告.pdf');

    staleSubmission.resolve(submissionReceipt());
    await flushPromises();

    expect(window.sessionStorage.getItem(oldStorageKey)).toBeNull();
    expect(window.sessionStorage.getItem(nextStorageKey)).toContain('000000000012');
    expect(wrapper.get('[data-testid="homework-file-name"]').text()).toContain('第二份报告.pdf');
    expect(attachmentApiMocks.deleteHomeworkAttachment).not.toHaveBeenCalled();
  });

  it('invalidates a pending submit when the same homework leaves and re-enters submit mode', async () => {
    const staleSubmission = deferred<HomeworkSubmissionSummary>();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework).mockReturnValueOnce(staleSubmission.promise);

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.get('[name="answerText"]').setValue('离开前的旧答案');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    await wrapper.setProps({ mode: 'detail' });
    window.sessionStorage.setItem('oj:draft:v1:anonymous:101:HWK:11', JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      homeworkType: 'TEXT',
      answerText: '回到提交页后新写的草稿',
      objectiveAnswers: {},
      codeText: '',
      language: ''
    }));
    await wrapper.setProps({ mode: 'submit' });
    await flushPromises();
    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('回到提交页后新写的草稿');
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11'))
      .toContain('回到提交页后新写的草稿');

    staleSubmission.resolve({
      submissionId: 311,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'RUNNING',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T09:00:00'
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="homework-latest-submission"]').exists()).toBe(false);
    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('回到提交页后新写的草稿');
    expect(window.sessionStorage.getItem('oj:draft:v1:anonymous:101:HWK:11'))
      .toContain('回到提交页后新写的草稿');
    expect(homeworkApi.getHomeworkSubmissionEvaluation).not.toHaveBeenCalledWith(311);
    expect(learningProgressApi.saveLearningProgress).not.toHaveBeenCalledWith(expect.objectContaining({
      lastPosition: 'homeworkId=11;submitted=311'
    }));
    expect(learningRecordsApi.reportLearningRecord).not.toHaveBeenCalledWith(expect.objectContaining({
      sourceId: 11,
      actionType: 'SUBMIT'
    }));
  });

  it('keeps a newer submit busy when an older request finishes on the same editor generation', async () => {
    const olderSubmission = deferred<HomeworkSubmissionSummary>();
    const newerSubmission = deferred<HomeworkSubmissionSummary>();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework)
      .mockReturnValueOnce(olderSubmission.promise)
      .mockReturnValueOnce(newerSubmission.promise);

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    await wrapper.get('[name="answerText"]').setValue('并发提交保护');
    await wrapper.get('form').trigger('submit');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(homeworkApi.submitHomework).toHaveBeenCalledTimes(2);

    olderSubmission.resolve({
      submissionId: 411,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 1,
      final: false,
      submittedAt: '2026-06-01T09:00:00'
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="homework-latest-submission"]').exists()).toBe(false);

    newerSubmission.resolve({
      submissionId: 412,
      homeworkId: 11,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 2,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="homework-latest-submission"]').text()).toContain('提交编号 412');
    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeUndefined();
  });
});

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 11,
    courseId: 101,
    chapterId: 1001,
    title: 'HWK02 text homework',
    description: 'Explain your algorithm.',
    type: 'TEXT',
    status: 'PUBLISHED',
    deadline: '2099-06-30T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: '2026-06-01T09:00:00',
    deleted: false,
    createdAt: '2026-05-30T12:00:00',
    updatedAt: '2026-06-01T09:00:00',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function attachmentUpload(overrides: Partial<HomeworkAttachmentUpload> = {}): HomeworkAttachmentUpload {
  return {
    fileId: '85c3d5a0-2140-4d80-9000-000000000011',
    originalFilename: '课程报告.pdf',
    contentType: 'application/pdf',
    fileSize: 20,
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    status: 'UPLOADED',
    uploadedAt: '2026-08-22T10:00:00+08:00',
    ...overrides
  };
}

function codedError(code: string, message: string) {
  return Object.assign(new Error(message), { code });
}

function submissionReceipt(): HomeworkSubmissionSummary {
  return {
    submissionId: 214,
    homeworkId: 11,
    studentId: 601,
    submitType: 'FILE',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'NONE',
    reviewStatus: 'UNREVIEWED',
    version: 1,
    final: true,
    submittedAt: '2026-08-22T10:05:00+08:00'
  };
}

function studentUser() {
  return {
    id: 601,
    username: 'student601',
    displayName: '林晓',
    userType: 'STUDENT',
    roles: ['STUDENT'],
    permissions: []
  };
}

async function chooseFile(wrapper: VueWrapper, file: File) {
  const input = wrapper.get('input[name="homeworkFile"]');
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [file]
  });
  await input.trigger('change');
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
