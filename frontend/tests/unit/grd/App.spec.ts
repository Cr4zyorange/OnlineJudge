import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../../src/app/App.vue';
import * as courseApi from '../../../src/api/crs/courses';
import * as gradeItemApi from '../../../src/api/grd/gradeItems';
import * as gradeRecordsApi from '../../../src/api/grd/gradeRecords';

vi.mock('../../../src/api/grd/gradeItems');
vi.mock('../../../src/api/grd/gradeRecords');
vi.mock('../../../src/api/crs/courses');

describe('App', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    installLocalStorageMock();
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
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
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
