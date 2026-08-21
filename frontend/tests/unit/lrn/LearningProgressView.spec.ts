import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LearningProgressView from '../../../src/views/lrn/LearningProgressView.vue';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
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
    installLocalStorageMock();
    window.localStorage.clear();
    resetRuntimeContext();
    currentUser.value = user('STUDENT');
    window.history.replaceState({}, '', '/learning/progress');
  });

  it('renders course and chapter progress with a continue learning entry', async () => {
    vi.mocked(learningProgressApi.getLearningProgress).mockResolvedValueOnce(progressOverview);

    const wrapper = mount(LearningProgressView);
    await flushPromises();

    expect(learningProgressApi.getLearningProgress).toHaveBeenCalledWith(undefined);
    expect(wrapper.text()).toContain('学习进度');
    expect(wrapper.find('[data-testid="lrn-home-entry"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('UI-LRN-02');
    expect(wrapper.text()).not.toContain('API-LRN-02');
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

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.getLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Java Programming');
  });

  it('lets teachers query managed course aggregate progress', async () => {
    currentUser.value = user('TEACHER');
    window.localStorage.setItem('onlinejudge.userRole', 'STUDENT');
    window.history.replaceState({}, '', '/learning/progress?courseId=101');
    vi.mocked(learningProgressApi.getLearningProgress).mockResolvedValueOnce({ courses: [], total: 0 });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce({
      courseId: 101,
      courseName: 'Java Programming',
      studentCount: 1,
      averageProgressPercent: 65,
      students: [
        {
          studentId: 601,
          studentName: 'Student 601',
          progressPercent: 65,
          status: 'IN_PROGRESS',
          updatedAt: '2026-06-01 10:00:00'
        }
      ]
    });

    const wrapper = mount(LearningProgressView);
    await flushPromises();

    expect(wrapper.text()).toContain('课程学习统计');
    await wrapper.findAll('button').find((button) => button.text() === '查询')?.trigger('click');
    await flushPromises();

    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.text()).toContain('Student 601');
    expect(wrapper.text()).toContain('65%');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).not.toContain('IN_PROGRESS');
  });

  it('does not allow local storage to elevate a student into teacher statistics', async () => {
    currentUser.value = user('STUDENT');
    window.localStorage.setItem('onlinejudge.userRole', 'TEACHER');
    vi.mocked(learningProgressApi.getLearningProgress).mockResolvedValueOnce({ courses: [], total: 0 });

    const wrapper = mount(LearningProgressView);
    await flushPromises();

    expect(wrapper.text()).not.toContain('课程学习统计');
    expect(learningProgressApi.getTeacherLearningProgress).not.toHaveBeenCalled();
  });
});

function user(role: 'STUDENT' | 'TEACHER') {
  return {
    id: role === 'TEACHER' ? 501 : 601,
    username: role.toLowerCase(),
    userType: role,
    displayName: role === 'TEACHER' ? 'Teacher 501' : 'Student 601',
    roles: [role],
    permissions: []
  };
}

function installLocalStorageMock() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear())
    }
  });
}
