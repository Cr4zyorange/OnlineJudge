import { configureAuthContext, type AuthContext } from '../api/http';

export function configureDefaultAuthContext() {
  configureAuthContext(() => resolveAuthContext(window.location));
}

export function resolveAuthContext(locationLike: Pick<Location, 'search' | 'pathname'>): AuthContext {
  const params = new URLSearchParams(locationLike.search);
  const userId = params.get('userId') ?? window.localStorage.getItem('onlinejudge.userId');
  const username = params.get('username') ?? window.localStorage.getItem('onlinejudge.username') ?? undefined;
  const role = params.get('role') ?? window.localStorage.getItem('onlinejudge.userRole');
  if (!userId || !role) {
    throw new Error('当前登录态缺失，无法访问接口');
  }
  const pathCourseId = locationLike.pathname.match(/\/courses\/(\d+)(?:\/|$)/)?.[1] ?? null;
  const queryCourseId = params.get('courseId');
  const activeCourseId = queryCourseId ?? pathCourseId;
  const manageableCourseIds = activeCourseId ? [activeCourseId] : '*';

  return {
    userId,
    username,
    role,
    permissions: ['course:manage'],
    courseIds: activeCourseId ? [activeCourseId] : '*',
    manageableCourseIds
  };
}
