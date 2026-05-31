import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkTeacherView from '../../../src/views/hwk/HomeworkTeacherView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkDetail, HomeworkStatus, HomeworkSubmission, HomeworkSummary, HomeworkType } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkTeacherView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('creates a draft objective homework and refreshes the teacher list', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 });
    vi.mocked(homeworkApi.createHomework).mockResolvedValueOnce(homeworkDetail({ id: 1 }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 1, title: 'HWK01 objective draft' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无作业');

    await wrapper.get('[name="title"]').setValue('HWK01 objective draft');
    await wrapper.get('[name="description"]').setValue('Answer basics.');
    await wrapper.get('[name="deadline"]').setValue('2026-06-30T23:59');
    await wrapper.get('[name="totalScore"]').setValue('100');
    await wrapper.get('[name="question-stem-0"]').setValue('1 + 1 = ?');
    await wrapper.get('[name="question-options-0"]').setValue('["1","2"]');
    await wrapper.get('[name="question-answer-0"]').setValue('["2"]');
    await wrapper.get('[name="question-score-0"]').setValue('100');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      title: 'HWK01 objective draft',
      description: 'Answer basics.',
      type: 'OBJECTIVE',
      totalScore: 100,
      allowResubmit: true,
      allowLateSubmit: false,
      showEvaluationBeforePublish: true,
      questions: [
        expect.objectContaining({
          stem: '1 + 1 = ?',
          optionsJson: '["1","2"]',
          answerJson: '["2"]',
          score: 100
        })
      ]
    }));
    expect(wrapper.text()).toContain('保存成功');
    expect(wrapper.text()).toContain('HWK01 objective draft');
    expect(wrapper.text()).toContain('DRAFT');
  });

  it('validates code homework test cases before sending create requests', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[name="type"]').setValue('CODE');
    await wrapper.get('[name="title"]').setValue('Code homework');
    await wrapper.get('[name="description"]').setValue('Implement addition.');
    await wrapper.get('[name="deadline"]').setValue('2026-06-30T23:59');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('代码题至少配置一个测试用例');
  });

  it('publishes and closes homework from the management table', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 7, title: 'Draft homework', status: 'DRAFT' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.publishHomework).mockResolvedValueOnce(homeworkDetail({ id: 7, status: 'PUBLISHED' }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 7, title: 'Draft homework', status: 'PUBLISHED' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.closeHomework).mockResolvedValueOnce(homeworkDetail({ id: 7, status: 'CLOSED' }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 7, title: 'Draft homework', status: 'CLOSED' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '发布')?.trigger('click');
    await flushPromises();
    expect(homeworkApi.publishHomework).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('发布成功');

    await wrapper.findAll('button').find((button) => button.text() === '关闭')?.trigger('click');
    await flushPromises();
    expect(homeworkApi.closeHomework).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('关闭成功');
  });

  it('loads a draft homework into the form and updates it', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 7, title: 'Draft homework', status: 'DRAFT' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      id: 7,
      title: 'Draft homework',
      questions: [
        {
          id: 70,
          homeworkId: 7,
          questionType: 'SINGLE_CHOICE',
          stem: '1 + 1 = ?',
          optionsJson: '["1","2"]',
          answerJson: '["2"]',
          score: 100,
          sortOrder: 1
        }
      ]
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(homeworkDetail({ id: 7, title: 'Draft homework updated' }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 7, title: 'Draft homework updated', status: 'DRAFT' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="edit-homework-7"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(7);
    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('Draft homework');
    expect((wrapper.get('[name="question-answer-0"]').element as HTMLInputElement).value).toBe('["2"]');

    await wrapper.get('[name="title"]').setValue('Draft homework updated');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(7, expect.objectContaining({
      courseId: 101,
      title: 'Draft homework updated'
    }));
    expect(homeworkApi.createHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('Draft homework updated');
  });

  it('preserves code judge config when editing a draft homework', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 8, title: 'Code draft', status: 'DRAFT', type: 'CODE' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      id: 8,
      title: 'Code draft',
      type: 'CODE',
      languageLimitJson: '["python"]',
      timeLimitMs: 2000,
      memoryLimitKb: 131072,
      outputCompareMode: 'TRIM',
      testCases: [
        {
          id: 80,
          homeworkId: 8,
          inputData: '1 2',
          expectedOutput: '3',
          scoreWeight: 100,
          hidden: false,
          timeLimitMs: 2000,
          memoryLimitKb: 131072,
          sortOrder: 1
        }
      ]
    } as Partial<HomeworkDetail>));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(homeworkDetail({ id: 8, title: 'Code draft updated', type: 'CODE' }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 8, title: 'Code draft updated', status: 'DRAFT', type: 'CODE' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="edit-homework-8"]').trigger('click');
    await flushPromises();
    await wrapper.get('[name="title"]').setValue('Code draft updated');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(8, expect.objectContaining({
      title: 'Code draft updated',
      type: 'CODE',
      languageLimitJson: '["python"]',
      timeLimitMs: 2000,
      memoryLimitKb: 131072,
      outputCompareMode: 'TRIM'
    }));
  });

  it('loads teacher submission history from the homework management table', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 22, title: 'Published homework', status: 'PUBLISHED' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [
        submission({ id: 92, answerText: 'second answer', final: true, latest: true }),
        submission({ id: 91, answerText: 'first answer', final: false, latest: false })
      ],
      page: 1,
      size: 20,
      total: 2
    });
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(
      submission({ id: 92, answerText: 'second answer detail', final: true, latest: true })
    );

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="load-submissions-22"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(22, {
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('second answer');
    expect(wrapper.text()).toContain('first answer');
    expect(wrapper.text()).toContain('最新提交');
    expect(wrapper.text()).toContain('当前有效');

    await wrapper.get('[data-testid="open-submission-92"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(92);
    expect(wrapper.text()).toContain('second answer detail');
  });

  it('filters teacher submission history by student and statuses', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 22, title: 'Published homework', status: 'PUBLISHED' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [],
      page: 1,
      size: 20,
      total: 0
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [submission({ id: 93, studentId: 602, submitStatus: 'LATE', evaluationStatus: 'PENDING' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="load-submissions-22"]').trigger('click');
    await flushPromises();
    await wrapper.get('[name="submissionStudentId"]').setValue('602');
    await wrapper.get('[name="submissionSubmitStatus"]').setValue('LATE');
    await wrapper.get('[name="submissionEvaluationStatus"]').setValue('PENDING');
    await wrapper.get('[name="submissionReviewStatus"]').setValue('UNREVIEWED');
    await wrapper.get('[data-testid="filter-submissions"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(22, {
      studentId: 602,
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      reviewStatus: 'UNREVIEWED',
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('602');
  });

  it('paginates teacher submission history and resets to the first page when filtering', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary({ id: 22, title: 'Published homework', status: 'PUBLISHED' })],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [submission({ id: 91, answerText: 'page one answer' })],
      page: 1,
      size: 20,
      total: 21
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [submission({ id: 94, answerText: 'page two answer' })],
      page: 2,
      size: 20,
      total: 21
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce({
      list: [submission({ id: 95, answerText: 'filtered answer', submitStatus: 'LATE' })],
      page: 1,
      size: 20,
      total: 1
    });

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="load-submissions-22"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="submission-page-summary"]').text()).toContain('1 / 2');
    expect(wrapper.get('[data-testid="submission-prev-page"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="submission-next-page"]').attributes('disabled')).toBeUndefined();

    await wrapper.get('[data-testid="submission-next-page"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(22, {
      page: 2,
      size: 20
    });
    expect(wrapper.text()).toContain('page two answer');

    await wrapper.get('[name="submissionSubmitStatus"]').setValue('LATE');
    await wrapper.get('[data-testid="filter-submissions"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(22, {
      submitStatus: 'LATE',
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('filtered answer');
  });
});

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 1,
    courseId: 101,
    title: 'HWK01 objective draft',
    description: 'Answer basics.',
    type: 'OBJECTIVE' as HomeworkType,
    status: 'DRAFT' as HomeworkStatus,
    deadline: '2026-06-30T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    ...overrides
  };
}

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    ...homeworkSummary(),
    chapterId: null,
    description: 'Answer basics.',
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: null,
    createdAt: '2026-05-30T12:00:00',
    updatedAt: '2026-05-30T12:00:00',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function submission(overrides: Partial<HomeworkSubmission> = {}): HomeworkSubmission {
  return {
    id: 91,
    homeworkId: 22,
    studentId: 601,
    submitType: 'TEXT',
    answerText: 'first answer',
    answerJson: null,
    fileUrl: null,
    language: null,
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'NOT_REQUIRED',
    reviewStatus: 'UNREVIEWED',
    autoScore: null,
    manualScore: null,
    finalScore: null,
    comment: null,
    final: false,
    latest: false,
    submittedAt: '2026-05-30T13:00:00',
    createdAt: '2026-05-30T13:00:00',
    updatedAt: '2026-05-30T13:00:00',
    ...overrides
  };
}

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
