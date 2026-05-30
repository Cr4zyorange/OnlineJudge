import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkTeacherView from '../../../src/views/hwk/HomeworkTeacherView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkDetail, HomeworkStatus, HomeworkSummary, HomeworkType } from '../../../src/types/hwk';

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

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
