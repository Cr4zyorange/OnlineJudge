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

  it('loads only student-visible homework into the shared responsive list and links to details', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({
      list: [
        homeworkSummary({ id: 11, title: 'HWK02 published homework', status: 'PUBLISHED' }),
        homeworkSummary({ id: 12, title: 'HWK02 closed homework', status: 'CLOSED' }),
        homeworkSummary({ id: 13, title: 'HWK02 score homework', status: 'SCORE_PUBLISHED' }),
        homeworkSummary({ id: 14, title: 'HWK02 archived homework', status: 'ARCHIVED' }),
        homeworkSummary({ id: 15, title: 'teacher draft', status: 'DRAFT' }),
        homeworkSummary({ id: 16, title: 'deleted homework', deleted: true }),
        homeworkSummary({ id: 17, title: 'derived not-open label', status: 'NOT_OPEN' })
      ],
      page: 1,
      size: 20,
      total: 7
    });

    const wrapper = mountView();

    expect(wrapper.text()).toContain('正在加载作业');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenCalledWith({ courseId: 101, page: 1, size: 20 });
    expect(wrapper.text()).toContain('HWK02 published homework');
    expect(wrapper.text()).toContain('HWK02 closed homework');
    expect(wrapper.text()).toContain('HWK02 score homework');
    expect(wrapper.text()).toContain('HWK02 archived homework');
    expect(wrapper.text()).not.toContain('teacher draft');
    expect(wrapper.text()).not.toContain('deleted homework');
    expect(wrapper.text()).not.toContain('derived not-open label');
    expect(wrapper.text()).toContain('文本作业');
    expect(wrapper.text()).toContain('已发布');
    expect(wrapper.text()).toContain('已关闭');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).toContain('已归档');
    expect(wrapper.text()).toContain('截止 2026-06-30 23:59');
    expect(wrapper.text()).not.toContain('TEXT');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('CLOSED');
    expect(wrapper.text()).not.toContain('due');
    expect(wrapper.get('[data-testid="open-homework-11"]').text()).toBe('查看');
    expect(wrapper.get('[data-testid="open-homework-11"]').attributes('href'))
      .toBe('/courses/101/homeworks/11');
    expect(wrapper.find('[data-testid="summary-strip"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="data-table-desktop"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="data-table-mobile"]').attributes('data-mobile-layout')).toBe('cards');

    const scoreSummary = wrapper.findAll('[data-testid="summary-strip"] > div')
      .find((item) => item.text().includes('成绩可查看'));
    expect(scoreSummary?.text()).toContain('2');
    expect(scoreSummary?.text()).toContain('含成绩已发布与归档作业');
  });

  it('offers only persisted student-visible lifecycle statuses as server filters', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 });

    const wrapper = mountView();
    await flushPromises();

    const statusOptions = wrapper.findAll('select[name="status"] option')
      .map((option) => option.attributes('value'));
    expect(statusOptions).toEqual(['', 'PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED', 'ARCHIVED']);
    expect(wrapper.find('select[name="status"] option[value="NOT_OPEN"]').exists()).toBe(false);
  });

  it('submits keyword, lifecycle status and page size to the server and keeps them while paging', async () => {
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 })
      .mockResolvedValueOnce({
        list: [homeworkSummary({ id: 21, title: '数组作业', status: 'CLOSED' })],
        page: 1,
        size: 10,
        total: 21
      })
      .mockResolvedValueOnce({
        list: [homeworkSummary({ id: 22, title: '数组作业二', status: 'CLOSED' })],
        page: 2,
        size: 10,
        total: 21
      });

    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('input[name="keyword"]').setValue('  数组  ');
    await wrapper.get('select[name="status"]').setValue('CLOSED');
    await wrapper.get('select[name="size"]').setValue('10');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenNthCalledWith(2, {
      courseId: 101,
      keyword: '数组',
      status: 'CLOSED',
      page: 1,
      size: 10
    });
    expect(wrapper.text()).toContain('共 21 项');
    expect(wrapper.text()).toContain('第 1 / 3 页');

    await wrapper.get('[data-testid="next-homework-page"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenNthCalledWith(3, {
      courseId: 101,
      keyword: '数组',
      status: 'CLOSED',
      page: 2,
      size: 10
    });
    expect(wrapper.text()).toContain('数组作业二');
    expect(wrapper.text()).toContain('第 2 / 3 页');
  });

  it('retries the same server query after a list request fails', async () => {
    vi.mocked(homeworkApi.listHomeworks)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce({
        list: [homeworkSummary({ title: '重试后可见的作业' })],
        page: 1,
        size: 20,
        total: 1
      });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('作业列表加载失败');
    expect(wrapper.text()).toContain('网络暂时不可用');
    await wrapper.get('[data-testid="retry-homework-list"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('重试后可见的作业');
  });

  it('renders a shared empty state when no homework is visible', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce({ list: [], page: 1, size: 20, total: 0 });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('暂无可见作业');
    expect(wrapper.text()).not.toContain('No visible homework');
    expect(wrapper.get('[data-testid="page-state"]').attributes('data-state')).toBe('empty');
  });
});

function mountView() {
  return mount(HomeworkStudentListView, {
    props: {
      courseId: 101
    },
    global: {
      stubs: {
        RouterLink: {
          name: 'RouterLink',
          props: ['to'],
          template: '<a :href="to"><slot /></a>'
        }
      }
    }
  });
}

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
