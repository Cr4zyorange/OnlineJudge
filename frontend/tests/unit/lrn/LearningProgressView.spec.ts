import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LearningProgressView from '../../../src/views/lrn/LearningProgressView.vue';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type { LearningProgressOverview } from '../../../src/types/lrn';

vi.mock('../../../src/api/lrn/learningProgress');

const progressOverview: LearningProgressOverview = {
  total: 1,
  courses: [
    {
      courseId: 101,
      courseName: 'Java Programming',
      progressPercent: 65,
      status: 'IN_PROGRESS',
      lastPosition: 'video_play_time=1234',
      continueUrl: '/courses/101',
      updatedAt: '2026-06-01 10:00:00',
      continueLearning: {
        progressId: 1,
        courseId: 101,
        courseName: 'Java Programming',
        chapterId: 1001,
        chapterName: 'Variables',
        sourceModule: 'CRS',
        sourceId: 701,
        progressPercent: 65,
        lastPosition: 'video_play_time=1234',
        status: 'IN_PROGRESS',
        continueUrl: '/courses/101',
        updatedAt: '2026-06-01 10:00:00'
      },
      chapters: [
        {
          chapterId: 1001,
          chapterName: 'Variables',
          progressPercent: 65,
          status: 'IN_PROGRESS',
          lastPosition: 'video_play_time=1234',
          continueUrl: '/courses/101',
          updatedAt: '2026-06-01 10:00:00',
          records: []
        }
      ]
    }
  ]
};

describe('LearningProgressView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders course and chapter progress with a continue learning entry', async () => {
    vi.mocked(learningProgressApi.getLearningProgress).mockResolvedValueOnce(progressOverview);

    const wrapper = mount(LearningProgressView);
    await flushPromises();

    expect(learningProgressApi.getLearningProgress).toHaveBeenCalledWith(undefined);
    expect(wrapper.text()).toContain('学习进度');
    expect(wrapper.text()).toContain('Java Programming');
    expect(wrapper.text()).toContain('65%');
    expect(wrapper.text()).toContain('Variables');
    expect(wrapper.text()).toContain('video_play_time=1234');
    expect(wrapper.get('a[href="/courses/101"]').text()).toContain('继续学习');
  });

  it('shows loading failures and retries progress loading', async () => {
    vi.mocked(learningProgressApi.getLearningProgress)
      .mockRejectedValueOnce(new Error('进度加载失败'))
      .mockResolvedValueOnce(progressOverview);

    const wrapper = mount(LearningProgressView);
    await flushPromises();

    expect(wrapper.text()).toContain('进度加载失败');

    await wrapper.get('[data-testid="retry-progress"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.getLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Java Programming');
  });
});
