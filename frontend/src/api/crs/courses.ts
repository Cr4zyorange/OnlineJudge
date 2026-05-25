import type { ApiResponse, Course, CoursePayload, PageResponse } from '../../types/crs';

const headers = {
  'Content-Type': 'application/json',
  'X-User-Id': '101',
  'X-User-Name': 'Teacher101',
  'X-User-Role': 'TEACHER'
};

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      ...headers,
      ...(init?.headers ?? {})
    }
  });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || body.code !== 200) {
    throw new Error(body.message || '请求失败');
  }
  return body.data;
}

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
    body: JSON.stringify(payload)
  });
}

export function updateCourse(courseId: number, payload: CoursePayload) {
  return request<Course>(`/api/v1/courses/${courseId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function archiveCourse(courseId: number) {
  return request<void>(`/api/v1/courses/${courseId}`, {
    method: 'DELETE'
  });
}
