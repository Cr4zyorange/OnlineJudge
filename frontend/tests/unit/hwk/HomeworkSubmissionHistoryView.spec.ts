import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionHistoryView from '../../../src/views/hwk/HomeworkSubmissionHistoryView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkSubmission } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkSubmissionHistoryView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows teacher submission history with latest and effective markers and opens detail', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce([
      homeworkSubmission({
        id: 2,
        answerText: 'second version',
        isLatest: true,
        isFinal: true,
        submittedAt: '2026-05-27T11:00:00'
      }),
      homeworkSubmission({
        id: 1,
        answerText: 'first version',
        isLatest: false,
        isFinal: false,
        submittedAt: '2026-05-27T10:00:00'
      })
    ]);
    vi.mocked(homeworkApi.getHomeworkSubmission).mockResolvedValueOnce(homeworkSubmission({
      id: 2,
      answerText: 'second version',
      isLatest: true,
      isFinal: true
    }));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        homeworkId: 17
      }
    });
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(17);
    expect(wrapper.text()).toContain('601');
    expect(wrapper.text()).toContain('已提交');
    expect(wrapper.text()).toContain('否');

    await wrapper.findAll('button').find((button) => button.text() === '查看')?.trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkSubmission).toHaveBeenCalledWith(2);
    expect(wrapper.text()).toContain('提交 #2');
    expect(wrapper.text()).toContain('second version');
  });

  it('keeps loading failures visible on the page', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockRejectedValueOnce(new Error('无课程作业管理权限'));

    const wrapper = mount(HomeworkSubmissionHistoryView, {
      props: {
        homeworkId: 17
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('无课程作业管理权限');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

function homeworkSubmission(overrides: Partial<HomeworkSubmission> = {}): HomeworkSubmission {
  return {
    id: 1,
    homeworkId: 17,
    studentId: 601,
    submitType: 'TEXT',
    answerText: 'first version',
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
    isLatest: true,
    isFinal: true,
    submittedAt: '2026-05-27T10:00:00',
    reviewedBy: null,
    reviewedAt: null,
    ...overrides
  };
}
