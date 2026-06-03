import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LearningStatisticsView from '../../../src/views/lrn/LearningStatisticsView.vue';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import type { LearningStatisticsOverview } from '../../../src/types/lrn';

vi.mock('../../../src/api/lrn/learningRecords');

const statistics: LearningStatisticsOverview = {
  summary: {
    totalDurationSeconds: 420,
    resourceAccessCount: 3,
    completedTaskCount: 1,
    submittedTaskCount: 1,
    totalRecordCount: 5
  },
  trends: [
    { date: '2026-05-27', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-28', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-29', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-30', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-31', durationSeconds: 180, resourceAccessCount: 1, completedTaskCount: 1 },
    { date: '2026-06-01', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-06-02', durationSeconds: 240, resourceAccessCount: 2, completedTaskCount: 0 }
  ],
  recentRecords: [
    {
      id: 3,
      courseId: 101,
      courseName: 'Java Programming',
      sourceModule: 'LAB',
      sourceId: 301,
      actionType: 'SUBMIT',
      durationSeconds: 240,
      startedAt: '2026-06-02 10:00:00',
      endedAt: '2026-06-02 10:04:00'
    }
  ]
};

describe('LearningStatisticsView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/learning/statistics');
  });

  it('renders personal behavior dashboard summary, trend and recent records', async () => {
    vi.mocked(learningRecordsApi.getLearningStatistics).mockResolvedValueOnce(statistics);

    const wrapper = mount(LearningStatisticsView);
    await flushPromises();

    expect(learningRecordsApi.getLearningStatistics).toHaveBeenCalledWith(undefined);
    expect(wrapper.text()).toContain('学习行为仪表盘');
    expect(wrapper.text()).toContain('7分钟');
    expect(wrapper.text()).toContain('3');
    expect(wrapper.text()).toContain('1');
    expect(wrapper.findAll('.trend-bar')).toHaveLength(7);
    expect(wrapper.text()).toContain('Java Programming');
    expect(wrapper.text()).toContain('提交任务');
  });

  it('shows cached dashboard state and retries loading', async () => {
    vi.mocked(learningRecordsApi.getLearningStatistics)
      .mockResolvedValueOnce({ ...statistics, fromCache: true })
      .mockResolvedValueOnce(statistics);

    const wrapper = mount(LearningStatisticsView);
    await flushPromises();

    expect(wrapper.text()).toContain('当前展示本地缓存数据');

    await wrapper.get('[data-testid="retry-statistics"]').trigger('click');
    await flushPromises();

    expect(learningRecordsApi.getLearningStatistics).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).not.toContain('当前展示本地缓存数据');
  });

  it('shows loading failures and keeps a retry action', async () => {
    vi.mocked(learningRecordsApi.getLearningStatistics).mockRejectedValueOnce(new Error('统计加载失败'));

    const wrapper = mount(LearningStatisticsView);
    await flushPromises();

    expect(wrapper.text()).toContain('统计加载失败');
    expect(wrapper.find('[data-testid="retry-statistics"]').exists()).toBe(true);
  });
});
