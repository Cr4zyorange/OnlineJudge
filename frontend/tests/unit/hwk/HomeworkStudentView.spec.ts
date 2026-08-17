import { config, flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkSubmissionSummary
} from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');
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

  it('uses a real file picker and blocks FILE submission while the upload API is unavailable', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      type: 'FILE'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: { courseId: 101, homeworkId: 11, mode: 'submit' }
    });
    await flushPromises();

    expect(wrapper.find('input[name="fileIds"]').exists()).toBe(false);
    expect(wrapper.get('input[name="homeworkFile"]').attributes('type')).toBe('file');
    expect(wrapper.get('[data-testid="homework-file-blocker"]').text())
      .toContain('附件上传通道尚未提供');
    expect(wrapper.get('[data-testid="homework-primary-submit"]').attributes('disabled')).toBeDefined();

    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(homeworkApi.submitHomework).not.toHaveBeenCalled();
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

    expect((wrapper.get('[name="answerText"]').element as HTMLTextAreaElement).value)
      .toBe('路由切换后应恢复的草稿');
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
