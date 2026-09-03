import { createMemoryHistory, type RouteLocationNormalized } from 'vue-router';
import { describe, expect, it, vi } from 'vitest';
import type { AuthUser } from '../../../src/api/auth/auth';
import { createAppRouter } from '../../../src/app/router';
import { currentCourse, currentUser } from '../../../src/app/runtimeContext';
import type { Course } from '../../../src/types/crs';

describe('application router access contract', () => {
  it('maps all 50 formal UI identifiers to an addressable route carrier', () => {
    const router = createAppRouter({ history: createMemoryHistory() });
    const actualIds = new Set(
      router.getRoutes().flatMap((route) => route.meta.uiIds ?? [])
    );
    const expectedIds = [
      ...numberedUiIds('AUTH', 11),
      ...numberedUiIds('CRS', 7),
      ...numberedUiIds('LAB', 8),
      ...numberedUiIds('HWK', 9),
      ...numberedUiIds('GRD', 10),
      ...numberedUiIds('LRN', 5)
    ];

    expect(expectedIds).toHaveLength(50);
    expect([...actualIds].sort()).toEqual(expectedIds.sort());
  });

  it('uses /auth/me and CRS membership instead of a role query to enter a student course page', async () => {
    const loadCurrentUser = vi.fn().mockResolvedValue(user('STUDENT'));
    const loadCourse = vi.fn().mockResolvedValue(course({ manageable: false }));
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: { loadCurrentUser, loadCourse }
    });

    await router.push('/courses/42/labs?role=teacher');

    expect(router.currentRoute.value.name).toBe('course-labs');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/labs');
    expect(loadCurrentUser).toHaveBeenCalledTimes(1);
    expect(loadCourse).toHaveBeenCalledWith(42);
    expect(currentUser.value?.userType).toBe('STUDENT');
    expect(currentCourse.value?.manageable).toBe(false);
  });

  it('allows a CRS manager into a teacher workspace without role selectors', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/labs/9/manage/submissions');

    expect(router.currentRoute.value.name).toBe('lab-submission-workspace');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/labs/9/manage/submissions');
  });

  it.each([
    {
      path: '/courses/42/labs/new',
      routeName: 'lab-create',
      uiIds: ['UI-LAB-04'],
      props: { courseId: 42 }
    },
    {
      path: '/courses/42/labs/9/manage',
      routeName: 'lab-manage-detail',
      uiIds: ['UI-LAB-03'],
      props: { courseId: 42, labId: 9 }
    },
    {
      path: '/courses/42/labs/9/edit',
      routeName: 'lab-edit',
      uiIds: ['UI-LAB-04'],
      props: { courseId: 42, labId: 9 }
    },
    {
      path: '/courses/42/labs/9/manage/submissions',
      routeName: 'lab-submission-workspace',
      uiIds: ['UI-LAB-03', 'UI-LAB-06'],
      props: { courseId: 42, labId: 9 }
    },
    {
      path: '/courses/42/labs/9/manage/submissions/5f02eb1c-e1d4-437a-bd05-03b70c9327e9',
      routeName: 'lab-submission-review',
      uiIds: ['UI-LAB-06'],
      props: { courseId: 42, labId: 9, submissionId: '5f02eb1c-e1d4-437a-bd05-03b70c9327e9' }
    },
    {
      path: '/courses/42/labs/9/manage/statistics',
      routeName: 'lab-statistics',
      uiIds: ['UI-LAB-08'],
      props: { courseId: 42, labId: 9 }
    }
  ])('exposes the $routeName LAB teacher route contract', async ({ path, routeName, uiIds, props }) => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push(path);

    expect(router.currentRoute.value.name).toBe(routeName);
    expect(router.currentRoute.value.meta.uiIds).toEqual(uiIds);
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual(props);
    expect(router.currentRoute.value.query.role).toBeUndefined();
  });

  it('routes a course member without manage permission to 403 for teacher workspaces', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
      }
    });

    await router.push('/courses/42/labs/9/manage/submissions');

    expect(router.currentRoute.value.name).toBe('forbidden');
  });

  it('blocks a student from refreshed HWK teacher deep links', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
      }
    });

    await router.push('/courses/42/homeworks/9/manage/submissions/55');

    expect(router.currentRoute.value.name).toBe('forbidden');
  });

  it.each([
    {
      path: '/courses/42/homeworks/manage',
      routeName: 'homework-manage',
      uiIds: ['UI-HWK-01', 'UI-HWK-03'],
      props: { courseId: 42 }
    },
    {
      path: '/courses/42/homeworks/new',
      routeName: 'homework-create',
      uiIds: ['UI-HWK-02'],
      props: { courseId: 42 }
    },
    {
      path: '/courses/42/homeworks/9/manage',
      routeName: 'homework-manage-detail',
      uiIds: ['UI-HWK-03'],
      props: { courseId: 42, homeworkId: 9 }
    },
    {
      path: '/courses/42/homeworks/9/edit',
      routeName: 'homework-edit',
      uiIds: ['UI-HWK-02'],
      props: { courseId: 42, homeworkId: 9 }
    },
    {
      path: '/courses/42/homeworks/9/manage/submissions',
      routeName: 'homework-submission-workspace',
      uiIds: ['UI-HWK-06', 'UI-HWK-08'],
      props: { courseId: 42, homeworkId: 9 }
    },
    {
      path: '/courses/42/homeworks/9/manage/submissions/55',
      routeName: 'homework-submission-review',
      uiIds: ['UI-HWK-08'],
      props: { courseId: 42, homeworkId: 9, submissionId: 55 }
    },
    {
      path: '/courses/42/homeworks/9/manage/statistics',
      routeName: 'homework-statistics',
      uiIds: ['UI-HWK-09'],
      props: { courseId: 42, homeworkId: 9 }
    }
  ])('exposes the $routeName HWK teacher route contract', async ({ path, routeName, uiIds, props }) => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push(path);

    expect(router.currentRoute.value.name).toBe(routeName);
    expect(router.currentRoute.value.meta.uiIds).toEqual(uiIds);
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual(props);
  });

  it('restores the HWK statistics follow-up page from a refreshed deep link', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/homeworks/9/manage/statistics?attention=REVIEW_PENDING&page=3');

    expect(router.currentRoute.value.name).toBe('homework-statistics');
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({
      courseId: 42,
      homeworkId: 9,
      initialAttention: 'REVIEW_PENDING',
      initialPage: 3
    });
  });

  it('ignores unsupported HWK statistics attention values on refresh', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/homeworks/9/manage/statistics?attention=STUDENT_ID_PENDING&page=2');

    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({
      courseId: 42,
      homeworkId: 9,
      initialPage: 2
    });
  });

  it('restores HWK statistics attention and page props across browser history', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/homeworks/9/manage/statistics?attention=EVALUATION_PENDING&page=2');
    await router.push('/courses/42/homeworks/9/manage/statistics?attention=REVIEW_PENDING&page=3');

    router.back();
    await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({
      attention: 'EVALUATION_PENDING',
      page: '2'
    }));
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({
      courseId: 42,
      homeworkId: 9,
      initialAttention: 'EVALUATION_PENDING',
      initialPage: 2
    });

    router.forward();
    await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({
      attention: 'REVIEW_PENDING',
      page: '3'
    }));
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({
      courseId: 42,
      homeworkId: 9,
      initialAttention: 'REVIEW_PENDING',
      initialPage: 3
    });
  });

  it.each([
    '/courses/42/homeworks/9/manage/statistics?attention=REVIEW_PENDING&page=2',
    '/courses/42/homeworks/9/manage/submissions?attention=EVALUATION_PENDING&page=2'
  ])('blocks a student from refreshed HWK attention deep link %s', async (path) => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
      }
    });

    await router.push(path);

    expect(router.currentRoute.value.name).toBe('forbidden');
  });

  it('routes a non-member course visit to 403', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ member: false, manageable: false }))
      }
    });

    await router.push('/courses/42/homeworks');

    expect(router.currentRoute.value.name).toBe('forbidden');
  });

  it('routes an invalid session and an unknown location to explicit status pages', async () => {
    const expired = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockRejectedValue(new Error('expired')),
        loadCourse: vi.fn()
      }
    });
    await expired.push('/learning/tasks');
    expect(expired.currentRoute.value.name).toBe('session-expired');

    const unknown = createAppRouter({ history: createMemoryHistory() });
    await unknown.push('/missing/page');
    expect(unknown.currentRoute.value.name).toBe('not-found');
  });

  it('preserves the HTTP login redirect for a first-time visitor without a token', async () => {
    window.history.replaceState({}, '', '/');
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockImplementation(async () => {
          window.history.pushState({}, '', '/login');
          throw new Error('当前登录态缺失，无法访问接口');
        }),
        loadCourse: vi.fn()
      }
    });

    try {
      await router.push('/learning/tasks');
      expect(router.currentRoute.value.name).toBe('login');
    } finally {
      window.history.replaceState({}, '', '/');
    }
  });

  it('redirects legacy grade links into the formal route tree and drops role', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/grd/grade-items?courseId=42&role=student');

    expect(router.currentRoute.value.name).toBe('grade-items-manage');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/grades/manage/items');
  });

  it('preserves formal deep links across history traversal and a recreated router', async () => {
    const services = {
      loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
      loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
    };
    const router = createAppRouter({ history: createMemoryHistory(), services });

    await router.push('/courses/42/labs');
    await router.push('/courses/42/homeworks');
    router.back();
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('course-labs'));

    router.forward();
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('course-homeworks'));

    const refreshed = createAppRouter({ history: createMemoryHistory(), services });
    await refreshed.push(router.currentRoute.value.fullPath);
    expect(refreshed.currentRoute.value.fullPath).toBe('/courses/42/homeworks');
  });

  it('keeps the current page title when an unsaved-draft guard cancels navigation', async () => {
    const router = createAppRouter({ history: createMemoryHistory() });
    await router.push('/login');
    expect(document.title).toBe('登录｜学知实训平台');

    router.beforeEach((to) => to.path !== '/register');
    await router.push('/register');

    expect(router.currentRoute.value.fullPath).toBe('/login');
    expect(document.title).toBe('登录｜学知实训平台');
  });

  it('exposes distinct homework detail, submit, latest-result and historic-result route contracts', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
      }
    });

    await router.push('/courses/42/homeworks/9');
    expect(router.currentRoute.value.name).toBe('homework-detail');
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({ courseId: 42, homeworkId: 9, mode: 'detail' });

    await router.push('/courses/42/homeworks/9/submit');
    expect(router.currentRoute.value.name).toBe('homework-submit');
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({ courseId: 42, homeworkId: 9, mode: 'submit' });

    await router.push('/courses/42/homeworks/9/result');
    expect(router.currentRoute.value.name).toBe('homework-latest-result');
    expect(router.currentRoute.value.meta.uiIds).toEqual(['UI-HWK-07']);
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({ courseId: 42, homeworkId: 9 });

    await router.push('/courses/42/homeworks/9/submissions/55/result');
    expect(router.currentRoute.value.name).toBe('homework-submission-result');
    expect(router.currentRoute.value.meta.uiIds).toEqual(['UI-HWK-07']);
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual({
      courseId: 42,
      homeworkId: 9,
      submissionId: 55
    });
  });

  it.each([
    {
      label: 'detail',
      path: '/courses/42/labs/9',
      routeName: 'lab-detail',
      uiIds: ['UI-LAB-02'],
      props: { courseId: 42, labId: 9, mode: 'detail' }
    },
    {
      label: 'submit',
      path: '/courses/42/labs/9/submit',
      routeName: 'lab-submit',
      uiIds: ['UI-LAB-02'],
      props: { courseId: 42, labId: 9, mode: 'submit' }
    },
    {
      label: 'latest result',
      path: '/courses/42/labs/9/result',
      routeName: 'lab-latest-result',
      uiIds: ['UI-LAB-07'],
      props: { courseId: 42, labId: 9 }
    },
    {
      label: 'historic result',
      path: '/courses/42/labs/9/submissions/0d25ce84-3a65-4dc8-8a82-7333f55c9143/result',
      routeName: 'lab-submission-result',
      uiIds: ['UI-LAB-07'],
      props: { courseId: 42, labId: 9, submissionId: '0d25ce84-3a65-4dc8-8a82-7333f55c9143' }
    }
  ])('exposes the $label LAB student route contract', async ({ path, routeName, uiIds, props }) => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: false }))
      }
    });

    await router.push(path);

    expect(router.currentRoute.value.name).toBe(routeName);
    expect(router.currentRoute.value.meta.uiIds).toEqual(uiIds);
    expect(resolveDefaultProps(router.currentRoute.value)).toEqual(props);
  });

  it('redirects a course manager from the student latest-result route to submission management', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('STUDENT')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/homeworks/9/result');

    expect(router.currentRoute.value.name).toBe('homework-submission-workspace');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/homeworks/9/manage/submissions');
  });

  it('redirects a course manager from the legacy student submission-history route to the review workspace', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/homeworks/9/submissions');

    expect(router.currentRoute.value.name).toBe('homework-submission-workspace');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/homeworks/9/manage/submissions');
  });

  it('redirects a course manager from the latest LAB student-result route to the submission workspace', async () => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      services: {
        loadCurrentUser: vi.fn().mockResolvedValue(user('TEACHER')),
        loadCourse: vi.fn().mockResolvedValue(course({ manageable: true }))
      }
    });

    await router.push('/courses/42/labs/9/result');

    expect(router.currentRoute.value.name).toBe('lab-submission-workspace');
    expect(router.currentRoute.value.fullPath).toBe('/courses/42/labs/9/manage/submissions');
  });
});

function numberedUiIds(module: string, count: number) {
  return Array.from({ length: count }, (_, index) => `UI-${module}-${String(index + 1).padStart(2, '0')}`);
}

function resolveDefaultProps(route: RouteLocationNormalized) {
  const propContract = route.matched.at(-1)?.props.default;
  return typeof propContract === 'function' ? propContract(route) : propContract;
}

function user(userType: string): AuthUser {
  return {
    id: 7,
    username: 'route-user',
    userType,
    displayName: '路由测试用户',
    roles: [userType],
    permissions: []
  };
}

function course(overrides: Partial<Course> = {}): Course {
  return {
    id: 42,
    name: '软件工程实践',
    description: '路由权限测试课程',
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
