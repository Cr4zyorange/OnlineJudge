import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkDetail, HomeworkSubmission, HomeworkSummary } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads published homework detail without answers and submits text content', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary()],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce(submission({ answerText: 'My answer' }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ answerText: 'My answer' })
    ]);
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([
      submission({ answerText: 'My answer' })
    ]);

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenCalledWith({ courseId: 101 });
    expect(wrapper.text()).toContain('HWK02 published text');

    await wrapper.get('[data-testid="open-homework-22"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(22);
    expect(wrapper.text()).toContain('Read and answer.');
    expect(wrapper.text()).not.toContain('answerJson');

    await wrapper.get('[name="answerText"]').setValue('My answer');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(22, {
      answerText: 'My answer',
      answerJson: null,
      fileUrl: null,
      codeText: null,
      language: null
    });
    expect(wrapper.text()).toContain('提交成功');
    expect(wrapper.text()).toContain('My answer');
  });

  it('opens the initial homework detail from a direct route entry', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary()],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        initialHomeworkId: 22
      }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(22);
    expect(wrapper.text()).toContain('Read and answer.');
  });

  it('lets the server decide whether a deadline has passed when submitting', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary()],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      deadline: '2026-05-01T23:59:59',
      allowLateSubmit: false
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);
    vi.mocked(homeworkApi.submitHomework).mockRejectedValueOnce(new Error('deadline passed'));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="open-homework-22"]').trigger('click');
    await flushPromises();
    await wrapper.get('[name="answerText"]').setValue('My answer');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(22, expect.objectContaining({
      answerText: 'My answer'
    }));
    expect(wrapper.text()).toContain('deadline passed');
  });

  it('shows a history error instead of treating failed history loads as empty', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [homeworkSummary()],
      page: 1,
      size: 20,
      total: 1
    });
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockRejectedValueOnce(new Error('history unavailable'));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="open-homework-22"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="submission-history-error"]').text()).toContain('history unavailable');
  });
});

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 22,
    courseId: 101,
    title: 'HWK02 published text',
    description: 'Read and answer.',
    type: 'TEXT',
    status: 'PUBLISHED',
    totalScore: 100,
    deadline: '2026-06-30T23:59:59',
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
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: '2026-05-30T12:00:00',
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
    answerText: null,
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
    final: true,
    submittedAt: '2026-05-30T13:00:00',
    createdAt: '2026-05-30T13:00:00',
    updatedAt: '2026-05-30T13:00:00',
    ...overrides
  };
}

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}
