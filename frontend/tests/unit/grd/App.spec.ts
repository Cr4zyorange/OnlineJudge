import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../../src/app/App.vue';
import * as courseApi from '../../../src/api/crs/courses';
import * as gradeItemApi from '../../../src/api/grd/gradeItems';
import * as gradeRecordsApi from '../../../src/api/grd/gradeRecords';
import * as labApi from '../../../src/api/lab/labs';

vi.mock('../../../src/api/grd/gradeItems');
vi.mock('../../../src/api/grd/gradeRecords');
vi.mock('../../../src/api/crs/courses');
vi.mock('../../../src/api/lab/labs');

describe('App', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    window.localStorage.setItem('onlinejudge.userId', '101');
    window.localStorage.setItem('onlinejudge.userRole', 'TEACHER');
    window.localStorage.setItem('onlinejudge.username', 'Teacher101');
  });

  afterEach(() => {
    vi.resetAllMocks();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    });
  });

  it('renders the merged course management page on the course route', async () => {
    vi.mocked(courseApi.listCourses).mockResolvedValueOnce({ list: [], total: 0, page: 1, size: 20 });
    vi.mocked(courseApi.listCourses).mockResolvedValueOnce({ list: [], total: 0, page: 1, size: 20 });
    vi.mocked(courseApi.listCourses).mockResolvedValueOnce({ list: [], total: 0, page: 1, size: 20 });
    vi.mocked(courseApi.listCourses).mockResolvedValueOnce({ list: [], total: 0, page: 1, size: 20 });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/courses')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(courseApi.listCourses).toHaveBeenCalledWith('', 'all');
    expect(wrapper.text()).toContain('全部课程');
  });

  it('passes course id from route query into the grade item configuration page', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/grd/grade-items?courseId=303')
    });

    mount(App);
    await flushPromises();

    expect(gradeItemApi.listGradeItems).toHaveBeenCalledWith(303);
  });

  it('passes course id from course route path into the grade item configuration page', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/courses/404/grd/grade-items')
    });

    mount(App);
    await flushPromises();

    expect(gradeItemApi.listGradeItems).toHaveBeenCalledWith(404);
  });

  it('routes course grade table paths to the teacher grade table page', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/courses/505/grd/grades')
    });

    mount(App);
    await flushPromises();

    expect(gradeRecordsApi.listCourseGrades).toHaveBeenCalledWith(505, { page: 1, size: 20 });
    expect(gradeItemApi.listGradeItems).not.toHaveBeenCalled();
  });

  it('keeps the student grade page on course grade paths when role is student', async () => {
    window.localStorage.setItem('onlinejudge.userRole', 'STUDENT');
    vi.mocked(gradeRecordsApi.getMyPublishedGrades).mockResolvedValueOnce({
      studentId: 101,
      records: [],
      summary: null
    });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/courses/505/grades?role=student')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(gradeRecordsApi.getMyPublishedGrades).toHaveBeenCalledWith(505);
    expect(gradeRecordsApi.listCourseGrades).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="final-score"]').exists()).toBe(true);
  });

  it('routes logged-in students from the lab detail path to the student lab page', async () => {
    window.localStorage.setItem('onlinejudge.userRole', 'STUDENT');
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([]);
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      chapterId: null,
      title: '学生实验详情',
      description: '从登录角色进入学生提交页',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'java,python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/courses/101/labs/7')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(labApi.listLabs).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('学生实验详情');
  });

  it('does not load grade items without an active course context', async () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/grd/grade-items')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(gradeItemApi.listGradeItems).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('缺少课程上下文');
  });

  it('checks the current user before rendering the administrator AUTH page', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        id: 1,
        username: 'admin',
        userType: 'ADMIN',
        displayName: '管理员',
        roles: ['ADMIN'],
        permissions: ['auth:manage']
      }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0 }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/admin/auth')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' })
    }));
    expect(wrapper.text()).toContain('用户权限管理');
  });

  it('shows a 403 state instead of the administrator AUTH page for non-admin users', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      id: 2,
      username: 'teacher',
      userType: 'TEACHER',
      displayName: '教师',
      roles: ['TEACHER'],
      permissions: ['course:manage']
    }));
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/admin/auth')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('无权限访问');
    expect(wrapper.text()).not.toContain('用户权限管理');
  });

  it('switches the mounted administrator page to the expired session view when the request is rejected', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'expired-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(errorResponse('ERR-AUTH-04', '登录已失效，请重新登录'));
    window.history.pushState({}, '', '/admin/auth');

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('[data-status-kind="expired"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('登录状态已失效');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBeNull();
  });

  it('switches the mounted administrator page to the account status view when the account is disabled', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'locked-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(errorResponse('ERR-AUTH-03', '账号已被禁用、冻结或锁定'));
    window.history.pushState({}, '', '/admin/auth');

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('[data-status-kind="account-disabled"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('账号状态异常');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBeNull();
  });

  it('renders the forbidden access page for unauthorized routes', async () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/403')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('无权限访问');
    expect(wrapper.text()).toContain('返回课程首页');
  });

  it('renders the expired session page and clears local auth state', async () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost/session-expired')
    });

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('登录状态已失效');
    expect(wrapper.text()).toContain('重新登录');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBeNull();
  });
});

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
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

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'success',
      data
    })
  } as Response;
}

function errorResponse(code: string, message: string) {
  return {
    ok: false,
    json: async () => ({
      code,
      message,
      data: null
    })
  } as Response;
}
