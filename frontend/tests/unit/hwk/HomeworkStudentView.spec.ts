import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import type { HomeworkDetail } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');
vi.mock('../../../src/api/lrn/learningProgress');
vi.mock('../../../src/api/lrn/learningRecords');

describe('HomeworkStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/courses/101/homeworks/11?role=student');
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
    expect(wrapper.text()).toContain('请填写文本答案或附件编号');
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
    deadline: '2026-06-30T23:59:59',
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
