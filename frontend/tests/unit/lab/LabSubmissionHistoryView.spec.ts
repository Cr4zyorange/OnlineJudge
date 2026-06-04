import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionHistoryView from '../../../src/views/lab/LabSubmissionHistoryView.vue';
import * as labApi from '../../../src/api/lab/labs';

vi.mock('../../../src/api/lab/labs');

describe('LabSubmissionHistoryView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads submission history and opens a submission detail panel', async () => {
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
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
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      },
      {
        submissionId: 199,
        labId: 7,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'WRONG_ANSWER',
        autoScore: 70,
        finalScore: 70,
        version: 1,
        submittedAt: '2026-05-31T18:00:00',
        isLatest: false,
        isFinal: false,
        isScoringBasis: false,
        hasFile: true
      }
    ]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
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
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: false,
      code: "print('history detail')",
      fileId: null,
      latestReport: null
    });

    const wrapper = mount(LabSubmissionHistoryView, {
      props: {
        courseId: 101,
        labId: 7
      }
    });
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain('ACCEPTED');
    expect(wrapper.text()).toContain('当前评分依据');

    await wrapper.get('[data-submission-id="201"] button').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 201);
    expect(wrapper.text()).toContain("print('history detail')");
  });

  it('shows an empty state when the student has no submissions yet', async () => {
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(LabSubmissionHistoryView, {
      props: {
        courseId: 101,
        labId: 8
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('还没有提交记录');
  });

  it('surfaces history loading failures on the page', async () => {
    vi.mocked(labApi.listLabSubmissions).mockRejectedValueOnce(new Error('提交历史加载失败'));

    const wrapper = mount(LabSubmissionHistoryView, {
      props: {
        courseId: 101,
        labId: 9
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('提交历史加载失败');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
