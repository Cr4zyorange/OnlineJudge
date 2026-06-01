import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkDetail } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
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
    expect(wrapper.text()).toContain('TEXT');

    await wrapper.get('[name="answerText"]').setValue('Use dynamic programming.');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(11, expect.objectContaining({
      answerText: 'Use dynamic programming.'
    }));
    expect(wrapper.text()).toContain('Submission 91');
    expect(wrapper.text()).toContain('SUBMITTED');
    expect(wrapper.text()).toContain('UNREVIEWED');
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
    expect(wrapper.text()).toContain('Answer content is required');
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
    expect(wrapper.text()).toContain('PENDING');
    expect(wrapper.text()).toContain('NEED_REVIEW');
  });
});

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 11,
    courseId: 101,
    chapterId: null,
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

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}
