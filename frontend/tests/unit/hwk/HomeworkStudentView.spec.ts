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

  it('loads published homework detail, submits objective answers, and refreshes own history', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([
      homeworkSummary({
        id: 17,
        title: '第一次作业',
        type: 'OBJECTIVE'
      })
    ]);
    vi.mocked(homeworkApi.getHomework).mockResolvedValueOnce(homeworkDetail({
      id: 17,
      title: '第一次作业',
      type: 'OBJECTIVE'
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        homeworkSubmission({
          id: 88,
          homeworkId: 17,
          submitType: 'OBJECTIVE',
          answerJson: '{"1":["A"]}'
        })
      ]);
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce(homeworkSubmission({
      id: 88,
      homeworkId: 17,
      submitType: 'OBJECTIVE',
      answerJson: '{"1":["A"]}'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('第一次作业');
    expect(wrapper.text()).toContain('2 + 3 = ?');
    expect(wrapper.text()).not.toContain('answerJson');

    await wrapper.get('[name="answerJson"]').setValue('{"1":["A"]}');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(17, {
      answerJson: '{"1":["A"]}'
    });
    expect(wrapper.text()).toContain('作业提交成功');
    expect(wrapper.text()).toContain('已提交');
  });

  it('submits code homework with language and code text', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([
      homeworkSummary({
        id: 19,
        type: 'CODE',
        title: '代码作业'
      })
    ]);
    vi.mocked(homeworkApi.getHomework).mockResolvedValueOnce(homeworkDetail({
      id: 19,
      type: 'CODE',
      title: '代码作业',
      questions: []
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValue([]);
    vi.mocked(homeworkApi.submitHomework).mockResolvedValueOnce(homeworkSubmission({
      id: 90,
      homeworkId: 19,
      submitType: 'CODE',
      language: 'java'
    }));

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    await wrapper.get('[name="language"]').setValue('java');
    await wrapper.get('[name="codeText"]').setValue('class Main {}');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).toHaveBeenCalledWith(19, {
      codeText: 'class Main {}',
      language: 'java'
    });
  });

  it('keeps invalid student submissions on the page', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([
      homeworkSummary({
        id: 20,
        type: 'FILE',
        title: '文件作业'
      })
    ]);
    vi.mocked(homeworkApi.getHomework).mockResolvedValueOnce(homeworkDetail({
      id: 20,
      type: 'FILE',
      title: '文件作业',
      questions: []
    }));
    vi.mocked(homeworkApi.listMyHomeworkSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.submitHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('文件作业需提交文本或附件');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 17,
    courseId: 202,
    chapterId: 9,
    title: '第一次作业',
    description: '完成第一章',
    type: 'OBJECTIVE',
    status: 'PUBLISHED',
    totalScore: '100.00',
    deadline: '2099-07-01T23:59:00',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: false,
    createdBy: 501,
    publishedAt: '2026-05-27T10:00:00',
    createdAt: '2026-05-27T10:00:00',
    updatedAt: '2026-05-27T10:00:00',
    ...overrides
  };
}

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    ...homeworkSummary(overrides),
    questions: [
      {
        id: 1,
        homeworkId: 17,
        questionType: 'SINGLE_CHOICE',
        stem: '2 + 3 = ?',
        optionsJson: '["3","4","5","6"]',
        score: '100.00',
        sortOrder: 1
      }
    ],
    testCases: [],
    ...overrides
  };
}

function homeworkSubmission(overrides: Partial<HomeworkSubmission> = {}): HomeworkSubmission {
  return {
    id: 88,
    homeworkId: 17,
    studentId: 601,
    submitType: 'OBJECTIVE',
    answerText: null,
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
    submittedAt: '2026-05-27T10:30:00',
    reviewedBy: null,
    reviewedAt: null,
    ...overrides
  };
}
