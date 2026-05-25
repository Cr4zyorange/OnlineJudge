import { request } from '../http';
import type { Course, CoursePayload, PageResponse } from '../../types/crs';

export type CourseScope = 'all' | 'managed' | 'archived';

export function listCourses(keyword = '', scope: CourseScope = 'all') {
  const params = new URLSearchParams({ page: '1', size: '20', scope });
  if (keyword.trim()) {
    params.set('keyword', keyword.trim());
  }
  return request<PageResponse<Course>>(`/api/v1/courses?${params.toString()}`);
}

export function createCourse(payload: CoursePayload) {
  return request<Course>('/api/v1/courses', {
    method: 'POST',
    body: payload
  });
}

export function updateCourse(courseId: number, payload: CoursePayload) {
  return request<Course>(`/api/v1/courses/${courseId}`, {
    method: 'PUT',
    body: payload
  });
}

export function archiveCourse(courseId: number) {
  return request<void>(`/api/v1/courses/${courseId}`, {
    method: 'DELETE'
  });
}
