import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionHistoryView from '../../../src/views/hwk/HomeworkSubmissionHistoryView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkSubmissionSummary } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkSubmissionHistoryView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
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
