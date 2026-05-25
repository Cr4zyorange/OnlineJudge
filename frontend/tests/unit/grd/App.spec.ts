import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../../../src/app/App.vue';
import * as gradeItemApi from '../../../src/api/grd/gradeItems';

vi.mock('../../../src/api/grd/gradeItems');

describe('App', () => {
  const originalLocation = window.location;

  afterEach(() => {
    vi.resetAllMocks();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    });
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
