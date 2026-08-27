import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LearningTaskCenterView from '../../../src/views/lrn/LearningTaskCenterView.vue';
import * as learningTasksApi from '../../../src/api/lrn/learningTasks';
import type { LearningTaskPage } from '../../../src/types/lrn';

vi.mock('../../../src/api/lrn/learningTasks');

const taskPage: LearningTaskPage = {
  records: [
    {
      taskId: 301,
      taskType: 'EXPERIMENT',
      title: '链表实验',
      courseId: 101,
      courseName: 'Java程序设计',
      deadline: '2026-05-29 23:59:59',
      progress: 0,
      status: 'OVERDUE',
      actionUrl: '/courses/101/labs/301?role=student'
    },
    {
      taskId: 501,
      taskType: 'HOMEWORK',
      title: 'Java作业1',
      courseId: 101,
      courseName: 'Java程序设计',
      deadline: '2026-06-03 23:59:59',
      progress: 25,
      status: 'IN_PROGRESS',
      actionUrl: '/courses/101/homeworks/501'
    }
  ],
  total: 2,
  page: 1,
  size: 20
};

describe('LearningTaskCenterView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders task cards with course, status, deadline, progress and action links', async () => {
    vi.mocked(learningTasksApi.listLearningTasks).mockResolvedValueOnce(taskPage);

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    expect(learningTasksApi.listLearningTasks).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      size: 20,
      sortBy: 'deadline',
      order: 'asc'
    }));
    expect(wrapper.text()).toContain('学习任务中心');
    expect(wrapper.find('[data-testid="lrn-home-entry"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="learning-progress-entry"]').attributes('href')).toBe('/learning/progress');
    expect(wrapper.get('[data-testid="learning-statistics-entry"]').attributes('href')).toBe('/learning/statistics');
    expect(wrapper.get('[data-testid="learning-reminders-entry"]').attributes('href')).toBe('/learning/reminders');
    expect(wrapper.text()).not.toContain('UI-LRN-01');
    expect(wrapper.text()).toContain('链表实验');
    expect(wrapper.text()).toContain('已逾期');
    expect(wrapper.text()).toContain('Java作业1');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('25%');
    expect(wrapper.get('a[href="/courses/101/labs/301"]').text()).toContain('进入任务');
    expect(wrapper.find('a[href*="role="]').exists()).toBe(false);
  });

  it('uses a friendly unavailable state instead of a dead task link', async () => {
    vi.mocked(learningTasksApi.listLearningTasks).mockResolvedValueOnce({
      ...taskPage,
      records: [{ ...taskPage.records[0], actionUrl: null }],
      total: 1
    });

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    expect(wrapper.text()).toContain('入口已失效');
    expect(wrapper.find('a[href="#"]').exists()).toBe(false);
  });

  it('reloads tasks when filters change and shows empty state', async () => {
    vi.mocked(learningTasksApi.listLearningTasks)
      .mockResolvedValueOnce(taskPage)
      .mockResolvedValueOnce({
        records: [],
        total: 0,
        page: 1,
        size: 20
      });

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    await wrapper.get('[name="taskType"]').setValue('HOMEWORK');
    await flushPromises();

    expect(learningTasksApi.listLearningTasks).toHaveBeenLastCalledWith(expect.objectContaining({
      taskType: ['HOMEWORK'],
      page: 1
    }));
    expect(wrapper.text()).toContain('暂无符合条件的学习任务');
  });

  it('shows loading failures and retries the task query', async () => {
    vi.mocked(learningTasksApi.listLearningTasks)
      .mockRejectedValueOnce(new Error('任务列表加载失败'))
      .mockResolvedValueOnce(taskPage);

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    expect(wrapper.text()).toContain('任务列表加载失败');

    await wrapper.get('button[data-testid="retry-tasks"]').trigger('click');
    await flushPromises();

    expect(learningTasksApi.listLearningTasks).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Java作业1');
  });
  it('appends the next task page from the continuous loading control', async () => {
    vi.mocked(learningTasksApi.listLearningTasks)
      .mockResolvedValueOnce({ ...taskPage, total: 45, page: 1, size: 20 })
      .mockResolvedValueOnce({
        records: [{
          ...taskPage.records[1],
          taskId: 777,
          title: '追加加载的作业',
          actionUrl: '/courses/101/homeworks/777'
        }],
        total: 45,
        page: 2,
        size: 20
      });

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    await wrapper.get('[data-testid="load-more-tasks"]').trigger('click');
    await flushPromises();

    expect(learningTasksApi.listLearningTasks).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 2,
      size: 20
    }));

    expect(wrapper.findAll('.task-card')).toHaveLength(3);
  });

  it('keeps appending task pages through one continuous 200-task browsing path', async () => {
    vi.mocked(learningTasksApi.listLearningTasks).mockImplementation(async (query) => {
      const page = query?.page ?? 1;
      return {
        records: Array.from({ length: 20 }, (_, index) => ({
          taskId: ((page - 1) * 20) + index + 1,
        taskType: 'HOMEWORK' as const,
        title: `连续任务 ${((page - 1) * 20) + index + 1}`,
        courseId: 101,
        courseName: 'Java程序设计',
        deadline: '2030-12-31 23:59:59',
        progress: 0,
        status: 'NOT_STARTED' as const,
        actionUrl: `/courses/101/homeworks/${((page - 1) * 20) + index + 1}`
      })),
        total: 200,
        page,
        size: 20
      };
    });

    const wrapper = mount(LearningTaskCenterView);
    await flushPromises();

    for (let page = 2; page <= 10; page += 1) {
      await wrapper.get('[data-testid="load-more-tasks"]').trigger('click');
      await flushPromises();
    }

    expect(wrapper.findAll('.task-card')).toHaveLength(200);
    expect(wrapper.get('[data-testid="loaded-task-count"]').text()).toContain('200 / 200');
    expect(learningTasksApi.listLearningTasks).toHaveBeenCalledTimes(10);
  });
});
