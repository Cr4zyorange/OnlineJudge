import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, type Router } from 'vue-router';
import App from '../../../src/app/App.vue';
import { createAppRouter } from '../../../src/app/router';
import * as authApi from '../../../src/api/auth/auth';
import * as courseApi from '../../../src/api/crs/courses';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type { AuthUser } from '../../../src/api/auth/auth';
import type { Course } from '../../../src/types/crs';
import type { LabExperimentDetail } from '../../../src/types/lab';

vi.mock('../../../src/api/auth/auth');
vi.mock('../../../src/api/crs/courses');
vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/lrn/learningProgress');

describe('App routed shell integration', () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(user('STUDENT'));
    vi.mocked(courseApi.listCourses).mockResolvedValue({ list: [], total: 0, page: 1, size: 20 });
    vi.mocked(courseApi.getCourse).mockResolvedValue(course());
    vi.mocked(labApi.listLabs).mockResolvedValue([]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    vi.mocked(labApi.getLabDetail).mockResolvedValue(labDetail());
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue({
      courseId: 42,
      courseName: '软件工程实践',
      studentCount: 0,
      averageProgressPercent: 0,
      students: []
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it('renders the platform shell and course center on the root route', async () => {
    const mounted = await mountAt('/');

    expect(mounted.router.currentRoute.value.name).toBe('courses');
    expect(mounted.wrapper.get('[data-testid="skip-to-content"]').attributes('href')).toBe('#main-content');
    expect(mounted.wrapper.find('#main-content').exists()).toBe(true);
    expect(mounted.wrapper.findAll('[data-testid="platform-navigation"]')).toHaveLength(1);
    expect(mounted.wrapper.get('[data-testid="platform-nav-courses"]').attributes('href')).toBe('/courses');
    expect(mounted.wrapper.get('[data-testid="platform-nav-learning"]').attributes('href')).toBe('/learning/tasks');
    expect(mounted.wrapper.find('[data-testid="course-context-navigation"]').exists()).toBe(false);
    expect(mounted.wrapper.text()).toContain('全部课程');
    expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('renders a role-free course shell and the student LAB task list from CRS access', async () => {
    vi.mocked(courseApi.getCourse).mockResolvedValue(course({ manageable: false }));

    const mounted = await mountAt('/courses/42/labs?role=teacher');

    expect(mounted.router.currentRoute.value.fullPath).toBe('/courses/42/labs');
    expect(mounted.wrapper.findAll('[data-testid="platform-navigation"]')).toHaveLength(1);
    expect(mounted.wrapper.findAll('[data-testid="course-context-navigation"]')).toHaveLength(1);
    expect(mounted.wrapper.get('[data-testid="course-nav-labs"]').attributes('href')).toBe('/courses/42/labs');
    expect(mounted.wrapper.get('[data-testid="course-nav-homeworks"]').attributes('href')).toBe('/courses/42/homeworks');
    expect(mounted.wrapper.get('[data-testid="course-nav-grades"]').attributes('href')).toBe('/courses/42/grades');
    expect(mounted.wrapper.text()).toContain('课程实验');
    expect(mounted.wrapper.text()).toContain('当前筛选下没有实验');
    expect(courseApi.getCourse).toHaveBeenCalledWith(42);
  });

  it('renders the dedicated teacher LAB submission workspace for a CRS manager', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(user('TEACHER'));
    vi.mocked(courseApi.getCourse).mockResolvedValue(course({ manageable: true }));

    const mounted = await mountAt('/courses/42/labs/9/manage/submissions');

    expect(mounted.router.currentRoute.value.name).toBe('lab-submission-workspace');
    expect(mounted.wrapper.get('[data-testid="course-nav-labs"]').attributes('href')).toBe('/courses/42/labs/manage');
    expect(mounted.wrapper.text()).toContain('提交队列');
    expect(mounted.wrapper.text()).toContain('暂无符合条件的提交');
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(9, {});
  });

  it('renders 403 instead of a teacher workspace for a course member without manage access', async () => {
    vi.mocked(courseApi.getCourse).mockResolvedValue(course({ manageable: false }));

    const mounted = await mountAt('/courses/42/labs/9/manage/submissions');

    expect(mounted.router.currentRoute.value.name).toBe('forbidden');
    expect(mounted.wrapper.text()).toContain('无权限访问');
    expect(mounted.wrapper.text()).not.toContain('提交队列');
  });

  it('renders an explicit not-found page for unknown URLs', async () => {
    const mounted = await mountAt('/does/not/exist');

    expect(mounted.router.currentRoute.value.name).toBe('not-found');
    expect(mounted.wrapper.text()).toContain('页面不存在');
    expect(authApi.getCurrentUser).not.toHaveBeenCalled();
  });

  it('lets API auth failures hand navigation back to Vue Router', async () => {
    const mounted = await mountAt('/courses');
    window.history.replaceState({}, '', '/403');

    window.dispatchEvent(new Event('onlinejudge:navigation'));
    await flushPromises();

    expect(mounted.router.currentRoute.value.name).toBe('forbidden');
    expect(mounted.wrapper.text()).toContain('无权限访问');
  });

  async function mountAt(path: string) {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: authApi.getCurrentUser,
        loadCourse: courseApi.getCourse
      }
    });
    await router.push(path);
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();
    return { wrapper, router } as { wrapper: VueWrapper; router: Router };
  }
});

function user(userType: string): AuthUser {
  return {
    id: 7,
    username: 'app-user',
    userType,
    displayName: userType === 'TEACHER' ? '测试教师' : '测试学生',
    roles: [userType],
    permissions: []
  };
}

function course(overrides: Partial<Course> = {}): Course {
  return {
    id: 42,
    name: '软件工程实践',
    description: 'App 路由集成测试课程',
    teacherId: 3,
    teacherName: '教师',
    enrollmentMode: 'PUBLIC',
    status: 'ACTIVE',
    memberCount: 20,
    member: true,
    manageable: false,
    createdAt: '2026-08-01T08:00:00',
    updatedAt: '2026-08-15T08:00:00',
    ...overrides
  };
}

function labDetail(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 9,
    courseId: 42,
    chapterId: null,
    title: '实验',
    description: 'App 路由集成测试实验',
    status: 'PUBLISHED',
    deadline: '2026-08-25T23:59:00',
    maxScore: 100,
    attachmentIds: [],
    allowedLanguages: 'python',
    evaluationMode: 'DOCKER_IO',
    autoEvaluate: true,
    reportRequired: false,
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    testcases: [],
    publishedAt: '2026-08-19T08:00:00',
    deleted: false,
    ...overrides
  };
}
