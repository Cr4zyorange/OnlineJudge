import { nextTick } from 'vue';
import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw,
  type RouteRecordRedirectOption,
  type RouterHistory
} from 'vue-router';
import { getCurrentUser, type AuthUser } from '../api/auth/auth';
import { getCourse } from '../api/crs/courses';
import type { Course } from '../types/crs';
import AppShell from './AppShell.vue';
import CourseShell from './CourseShell.vue';
import { currentCourse, currentUser, resetRuntimeContext } from './runtimeContext';

export interface RouterServices {
  loadCurrentUser: () => Promise<AuthUser>;
  loadCourse: (courseId: number) => Promise<Course>;
}

export interface AppRouterOptions {
  history?: RouterHistory;
  services?: Partial<RouterServices>;
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/auth/AuthView.vue'),
    props: { initialMode: 'login' },
    meta: { title: '登录', shell: 'public' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/auth/AuthView.vue'),
    props: { initialMode: 'register' },
    meta: { title: '注册', shell: 'public' }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../views/auth/AuthStatusView.vue'),
    props: { kind: 'forbidden' },
    meta: { title: '无权限访问', shell: 'public' }
  },
  {
    path: '/session-expired',
    name: 'session-expired',
    component: () => import('../views/auth/AuthStatusView.vue'),
    props: { kind: 'expired' },
    meta: { title: '登录已失效', shell: 'public' }
  },
  {
    path: '/account-disabled',
    name: 'account-disabled',
    component: () => import('../views/auth/AuthStatusView.vue'),
    props: { kind: 'account-disabled' },
    meta: { title: '账号状态异常', shell: 'public' }
  },
  {
    path: '/404',
    name: 'not-found',
    component: () => import('../views/auth/AuthStatusView.vue'),
    props: { kind: 'not-found' },
    meta: { title: '页面不存在', shell: 'public' }
  },
  {
    path: '/grd/grade-items',
    name: 'legacy-grade-items',
    redirect: legacyGradeRedirect('items'),
    meta: { title: '成绩项配置', shell: 'course', legacy: true }
  },
  {
    path: '/grd/grades',
    name: 'legacy-grade-table',
    redirect: legacyGradeRedirect('table'),
    meta: { title: '成绩管理', shell: 'course', legacy: true }
  },
  {
    path: '/',
    component: AppShell,
    meta: { title: '学知实训平台', shell: 'platform', requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/courses',
        meta: { title: '课程中心', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('../views/crs/CourseManagementView.vue'),
        meta: { title: '课程中心', shell: 'platform', requiresAuth: true, uiIds: ['UI-CRS-01'] }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/auth/AuthProfileView.vue'),
        meta: { title: '个人中心', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'profile/password',
        name: 'profile-password',
        component: () => import('../views/auth/AuthProfileView.vue'),
        meta: { title: '修改密码', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'admin/auth',
        name: 'auth-admin',
        component: () => import('../views/auth/AuthAdminView.vue'),
        meta: {
          title: '认证与权限管理',
          shell: 'platform',
          requiresAuth: true,
          platformRoles: ['ADMIN']
        }
      },
      {
        path: 'learning',
        redirect: '/learning/tasks',
        meta: { title: '学习任务', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'learning/tasks',
        name: 'learning-tasks',
        component: () => import('../views/lrn/LearningTaskCenterView.vue'),
        meta: { title: '学习任务中心', shell: 'platform', requiresAuth: true, uiIds: ['UI-LRN-01'] }
      },
      {
        path: 'learning/progress',
        name: 'learning-progress',
        component: () => import('../views/lrn/LearningProgressView.vue'),
        meta: { title: '学习进度', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'learning/statistics',
        name: 'learning-statistics',
        component: () => import('../views/lrn/LearningStatisticsView.vue'),
        meta: { title: '学习统计', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'learning/reminders',
        name: 'learning-reminders',
        component: () => import('../views/lrn/ReminderRuleSettingsView.vue'),
        meta: { title: '提醒设置', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('../views/lrn/NotificationCenterView.vue'),
        meta: { title: '通知中心', shell: 'platform', requiresAuth: true }
      },
      {
        path: 'courses/:courseId',
        component: CourseShell,
        meta: {
          title: '课程',
          shell: 'course',
          requiresAuth: true,
          courseAccess: 'member'
        },
        children: [
          {
            path: '',
            name: 'course-home',
            component: () => import('../views/crs/CourseManagementView.vue'),
            meta: {
              title: '课程主页',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-CRS-02']
            }
          },
          {
            path: 'labs',
            name: 'course-labs',
            component: () => import('./CourseLabIndexView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '课程实验',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-LAB-01']
            }
          },
          {
            path: 'labs/manage',
            name: 'lab-manage',
            component: () => import('../views/lab/LabTeacherView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '实验管理',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage',
              uiIds: ['UI-LAB-03']
            }
          },
          {
            path: 'labs/:labId',
            name: 'lab-detail',
            component: () => import('../views/lab/LabStudentView.vue'),
            props: numericProps('courseId', 'labId'),
            meta: {
              title: '实验详情与提交',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-LAB-02']
            }
          },
          {
            path: 'labs/:labId/submissions',
            name: 'lab-submission-history',
            component: () => import('../views/lab/LabSubmissionHistoryView.vue'),
            props: numericProps('courseId', 'labId'),
            meta: {
              title: '实验提交历史',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-LAB-05']
            }
          },
          {
            path: 'labs/:labId/manage/submissions',
            name: 'lab-submission-workspace',
            component: () => import('../views/lab/LabSubmissionWorkspaceView.vue'),
            props: numericProps('courseId', 'labId'),
            meta: {
              title: '实验提交工作台',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage',
              uiIds: ['UI-LAB-03', 'UI-LAB-06']
            }
          },
          {
            path: 'homeworks',
            name: 'course-homeworks',
            component: () => import('./CourseHomeworkIndexView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '课程作业',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-01']
            }
          },
          {
            path: 'homeworks/manage',
            name: 'homework-manage',
            component: () => import('../views/hwk/HomeworkTeacherView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '作业管理',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage',
              uiIds: ['UI-HWK-01', 'UI-HWK-02']
            }
          },
          {
            path: 'homeworks/:homeworkId',
            name: 'homework-detail',
            component: () => import('../views/hwk/HomeworkStudentView.vue'),
            props: (route) => ({ ...numberParams(route, 'courseId', 'homeworkId'), mode: 'detail' }),
            meta: {
              title: '作业详情',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-04']
            }
          },
          {
            path: 'homeworks/:homeworkId/submit',
            name: 'homework-submit',
            component: () => import('../views/hwk/HomeworkStudentView.vue'),
            props: (route) => ({ ...numberParams(route, 'courseId', 'homeworkId'), mode: 'submit' }),
            meta: {
              title: '提交作业',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-05']
            }
          },
          {
            path: 'homeworks/:homeworkId/submissions',
            name: 'homework-submission-history',
            component: () => import('../views/hwk/HomeworkSubmissionHistoryView.vue'),
            props: (route) => ({ ...numberParams(route, 'courseId', 'homeworkId'), role: 'student' }),
            meta: {
              title: '作业提交历史',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-06']
            }
          },
          {
            path: 'homeworks/:homeworkId/result',
            name: 'homework-latest-result',
            component: () => import('../views/hwk/HomeworkSubmissionResultView.vue'),
            props: numericProps('courseId', 'homeworkId'),
            beforeEnter: (to) =>
              currentCourse.value?.manageable
                ? {
                    name: 'homework-submission-manage',
                    params: { courseId: to.params.courseId, homeworkId: to.params.homeworkId },
                    replace: true
                  }
                : true,
            meta: {
              title: '作业评测结果',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-07']
            }
          },
          {
            path: 'homeworks/:homeworkId/submissions/:submissionId/result',
            name: 'homework-submission-result',
            component: () => import('../views/hwk/HomeworkSubmissionResultView.vue'),
            props: numericProps('courseId', 'homeworkId', 'submissionId'),
            meta: {
              title: '作业评测结果',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member',
              uiIds: ['UI-HWK-07']
            }
          },
          {
            path: 'homeworks/:homeworkId/manage/submissions',
            name: 'homework-submission-manage',
            component: () => import('../views/hwk/HomeworkSubmissionHistoryView.vue'),
            props: (route) => ({ ...numberParams(route, 'courseId', 'homeworkId'), role: 'teacher' }),
            meta: {
              title: '作业批阅',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage',
              uiIds: ['UI-HWK-06', 'UI-HWK-07']
            }
          },
          {
            path: 'grades',
            name: 'student-grades',
            component: () => import('../views/grd/StudentGradeView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '我的成绩',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'member'
            }
          },
          {
            path: 'grades/manage/items',
            name: 'grade-items-manage',
            component: () => import('../views/grd/GradeItemConfigView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '成绩项配置',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage'
            }
          },
          {
            path: 'grades/manage/table',
            name: 'grade-table-manage',
            component: () => import('../views/grd/TeacherGradeTableView.vue'),
            props: numericProps('courseId'),
            meta: {
              title: '成绩管理',
              shell: 'course',
              requiresAuth: true,
              courseAccess: 'manage'
            }
          },
          {
            path: 'grd/grade-items',
            redirect: (to) => ({ path: `/courses/${to.params.courseId}/grades/manage/items`, replace: true }),
            meta: { title: '成绩项配置', shell: 'course', requiresAuth: true, courseAccess: 'manage', legacy: true }
          },
          {
            path: 'grd/grades',
            redirect: (to) => ({ path: `/courses/${to.params.courseId}/grades/manage/table`, replace: true }),
            meta: { title: '成绩管理', shell: 'course', requiresAuth: true, courseAccess: 'manage', legacy: true }
          }
        ]
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { title: '页面不存在', shell: 'public' }
  }
];

export function createAppRouter(options: AppRouterOptions = {}) {
  resetRuntimeContext();
  const services: RouterServices = {
    loadCurrentUser: options.services?.loadCurrentUser ?? getCurrentUser,
    loadCourse: options.services?.loadCourse ?? getCourse
  };
  const router = createRouter({
    history: options.history ?? createWebHistory(),
    routes,
    ...(options.history ? {} : { scrollBehavior: () => ({ top: 0 }) })
  });

  router.beforeEach(async (to) => {
    const roleFreeQuery = { ...to.query };
    if ('role' in roleFreeQuery) {
      delete roleFreeQuery.role;
      return { path: to.path, query: roleFreeQuery, hash: to.hash, replace: true };
    }

    if (!to.meta.requiresAuth) {
      return true;
    }

    try {
      if (currentUser.value === null) {
        currentUser.value = await services.loadCurrentUser();
      }
    } catch {
      return authFailureRoute();
    }

    if (to.meta.platformRoles && !hasPlatformRole(currentUser.value, to.meta.platformRoles)) {
      return { name: 'forbidden', replace: true };
    }

    if (to.meta.courseAccess) {
      const courseId = positiveInteger(to.params.courseId);
      if (courseId === null) {
        return { name: 'not-found', replace: true };
      }
      try {
        if (currentCourse.value?.id !== courseId) {
          currentCourse.value = await services.loadCourse(courseId);
        }
      } catch {
        return authOrMissingCourseRoute();
      }
      const course = currentCourse.value;
      if (!course || (!course.member && !course.manageable)) {
        return { name: 'forbidden', replace: true };
      }
      if (to.meta.courseAccess === 'manage' && !course.manageable) {
        return { name: 'forbidden', replace: true };
      }
    } else {
      currentCourse.value = null;
    }

    return true;
  });

  router.afterEach((to, _from, failure) => {
    if (failure) {
      return;
    }
    document.title = `${to.meta.title}｜学知实训平台`;
    void nextTick(() => {
      const heading = document.querySelector<HTMLElement>('h1');
      if (heading) {
        heading.tabIndex = -1;
        heading.focus({ preventScroll: true });
      }
    });
  });

  return router;
}

function numericProps(...keys: string[]) {
  return (route: RouteLocationNormalized) => numberParams(route, ...keys);
}

function numberParams(route: RouteLocationNormalized, ...keys: string[]) {
  return Object.fromEntries(keys.map((key) => [key, Number(route.params[key])]));
}

function positiveInteger(value: unknown) {
  const parsed = Number(Array.isArray(value) ? value[0] : value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function hasPlatformRole(user: AuthUser | null, accepted: string[]) {
  if (!user) {
    return false;
  }
  const roles = new Set([user.userType, ...user.roles].map((role) => role.toUpperCase()));
  return accepted.some((role) => roles.has(role.toUpperCase()));
}

function authFailureRoute() {
  if (typeof window !== 'undefined' && window.location.pathname === '/login') {
    return { name: 'login', replace: true };
  }
  if (typeof window !== 'undefined' && window.location.pathname === '/account-disabled') {
    return { name: 'account-disabled', replace: true };
  }
  if (typeof window !== 'undefined' && window.location.pathname === '/403') {
    return { name: 'forbidden', replace: true };
  }
  return { name: 'session-expired', replace: true };
}

function authOrMissingCourseRoute() {
  if (typeof window !== 'undefined' && window.location.pathname === '/session-expired') {
    return { name: 'session-expired', replace: true };
  }
  if (typeof window !== 'undefined' && window.location.pathname === '/account-disabled') {
    return { name: 'account-disabled', replace: true };
  }
  if (typeof window !== 'undefined' && window.location.pathname === '/403') {
    return { name: 'forbidden', replace: true };
  }
  return { name: 'not-found', replace: true };
}

function legacyGradeRedirect(destination: 'items' | 'table'): RouteRecordRedirectOption {
  return (to) => {
    const courseId = positiveInteger(to.query.courseId);
    if (courseId === null) {
      return { name: 'courses', replace: true };
    }
    return {
      path: destination === 'items'
        ? `/courses/${courseId}/grades/manage/items`
        : `/courses/${courseId}/grades/manage/table`,
      query: {},
      replace: true
    };
  };
}
