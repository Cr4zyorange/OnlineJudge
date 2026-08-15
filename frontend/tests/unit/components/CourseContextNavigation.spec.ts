import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import CourseContextNavigation from '../../../src/components/CourseContextNavigation.vue';

describe('CourseContextNavigation', () => {
  it('derives student and manager destinations from CRS course access instead of browser role storage', async () => {
    const wrapper = mount(CourseContextNavigation, {
      props: {
        courseId: 42,
        currentPath: '/courses/42',
        manageable: false
      }
    });

    expect(wrapper.get('[data-testid="course-nav-labs"]').attributes('href')).toBe('/courses/42/labs');
    expect(wrapper.get('[data-testid="course-nav-homeworks"]').attributes('href')).toBe('/courses/42/homeworks');
    expect(wrapper.get('[data-testid="course-nav-grades"]').attributes('href')).toBe('/courses/42/grades');

    await wrapper.setProps({ manageable: true });

    expect(wrapper.get('[data-testid="course-nav-labs"]').attributes('href')).toBe('/courses/42/labs/manage');
    expect(wrapper.get('[data-testid="course-nav-homeworks"]').attributes('href')).toBe('/courses/42/homeworks/manage');
    expect(wrapper.get('[data-testid="course-nav-grades"]').attributes('href')).toBe('/courses/42/grades/manage/table');
  });
});
