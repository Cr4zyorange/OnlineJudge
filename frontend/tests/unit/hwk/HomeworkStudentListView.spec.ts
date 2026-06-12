import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentListView from '../../../src/views/hwk/HomeworkStudentListView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkSummary } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkStudentListView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads visible homework list and links students to detail pages', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [
        homeworkSummary({ id: 11, title: 'HWK02 published homework', status: 'PUBLISHED' }),
        homeworkSummary({ id: 12, title: 'HWK02 closed homework', status: 'CLOSED' })
      ],
      page: 1,
      size: 20,
      total: 2
    });

    const wrapper = mount(HomeworkStudentListView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenCalledWith({ courseId: 101, page: 1, size: 20 });
    expect(wrapper.text()).toContain('HWK02 published homework');
    expect(wrapper.text()).toContain('HWK02 closed homework');
    expect(wrapper.text()).toContain('文本作业');
    expect(wrapper.text()).toContain('已发布');
    expect(wrapper.text()).toContain('已关闭');
    expect(wrapper.text()).toContain('截止 2026-06-30 23:59');
    expect(wrapper.text()).not.toContain('TEXT');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('CLOSED');
    expect(wrapper.text()).not.toContain('due');
    expect(wrapper.get('[data-testid="open-homework-11"]').text()).toBe('查看');
    expect(wrapper.get('[data-testid="open-homework-11"]').attributes('href'))
      .toBe('/courses/101/homeworks/11?role=student');
  });

  it('renders an empty state when no homework is visible', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 });

    const wrapper = mount(HomeworkStudentListView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无可见作业');
    expect(wrapper.text()).not.toContain('No visible homework');
  });
});

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 11,
    courseId: 101,
    title: 'HWK02 published homework',
    description: 'Read and submit.',
    type: 'TEXT',
    status: 'PUBLISHED',
    deadline: '2026-06-30T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    ...overrides
  };
}

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}
