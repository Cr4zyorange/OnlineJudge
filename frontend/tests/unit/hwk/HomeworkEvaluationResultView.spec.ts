import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkEvaluationResultView from '../../../src/views/hwk/HomeworkEvaluationResultView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkEvaluation } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkEvaluationResultView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows automatic evaluation score and case summary', async () => {
    vi.mocked(homeworkApi.getSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      status: 'ACCEPTED',
      score: '100.00',
      passedCount: 2,
      totalCount: 2,
      message: '客观题自动评分完成'
    }));

    const wrapper = mount(HomeworkEvaluationResultView, {
      props: {
        submissionId: 88
      }
    });
    await flushPromises();

    expect(homeworkApi.getSubmissionEvaluation).toHaveBeenCalledWith(88);
    expect(wrapper.text()).toContain('通过');
    expect(wrapper.text()).toContain('100.00 / 100.00');
    expect(wrapper.text()).toContain('2 / 2');
    expect(wrapper.text()).toContain('客观题自动评分完成');
  });

  it('lets teachers trigger reevaluation', async () => {
    vi.mocked(homeworkApi.getSubmissionEvaluation).mockResolvedValueOnce(evaluation({
      status: 'WRONG_ANSWER',
      score: '0.00',
      passedCount: 0
    }));
    vi.mocked(homeworkApi.reevaluateSubmission).mockResolvedValueOnce(evaluation({
      id: 10,
      status: 'ACCEPTED',
      score: '100.00',
      passedCount: 1,
      totalCount: 1
    }));

    const wrapper = mount(HomeworkEvaluationResultView, {
      props: {
        submissionId: 88,
        manageable: true
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '重评')?.trigger('click');
    await flushPromises();

    expect(homeworkApi.reevaluateSubmission).toHaveBeenCalledWith(88);
    expect(wrapper.text()).toContain('重评已完成');
    expect(wrapper.text()).toContain('通过');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

function evaluation(overrides: Partial<HomeworkEvaluation> = {}): HomeworkEvaluation {
  return {
    id: 9,
    homeworkId: 17,
    submissionId: 88,
    evaluatorType: 'OBJECTIVE',
    status: 'ACCEPTED',
    score: '100.00',
    totalScore: '100.00',
    passedCount: 2,
    totalCount: 2,
    caseResultsJson: '[{"accepted":true}]',
    message: '客观题自动评分完成',
    startedAt: '2026-05-27T10:30:00',
    finishedAt: '2026-05-27T10:30:01',
    ...overrides
  };
}
