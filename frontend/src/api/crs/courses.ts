import { request } from '../http';
import type { Chapter, ChapterPayload, Course, CoursePayload, CourseResource, PageResponse, ResourcePayload } from '../../types/crs';

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

export function getCourse(courseId: number) {
  return request<Course>(`/api/v1/courses/${courseId}`);
}

export function joinCourse(courseId: number) {
  return request<void>(`/api/v1/courses/${courseId}/join`, {
    method: 'POST'
  });
}

export function listChapters(courseId: number) {
  return request<Chapter[]>(`/api/v1/courses/${courseId}/chapters`);
}

export function createChapter(courseId: number, payload: ChapterPayload) {
  return request<Chapter>(`/api/v1/courses/${courseId}/chapters`, {
    method: 'POST',
    body: payload
  });
}

export function updateChapter(chapterId: number, payload: ChapterPayload) {
  return request<Chapter>(`/api/v1/chapters/${chapterId}`, {
    method: 'PUT',
    body: payload
  });
}

export function deleteChapter(chapterId: number) {
  return request<void>(`/api/v1/chapters/${chapterId}`, {
    method: 'DELETE'
  });
}

export function listResources(courseId: number) {
  return request<CourseResource[]>(`/api/v1/courses/${courseId}/resources`);
}

export function uploadResource(courseId: number, payload: ResourcePayload, file: File) {
  const formData = new FormData();
  formData.set('file', file);
  formData.set('name', payload.name);
  formData.set('resourceType', payload.resourceType);
  formData.set('visibility', payload.visibility);
  if (payload.chapterId != null) {
    formData.set('chapterId', String(payload.chapterId));
  }
  if (payload.publishAt) {
    formData.set('publishAt', payload.publishAt);
  }
  return request<CourseResource>(`/api/v1/courses/${courseId}/resources`, {
    method: 'POST',
    body: formData
  });
}

export function updateResource(courseId: number, resourceId: number, payload: ResourcePayload) {
  return request<CourseResource>(`/api/v1/courses/${courseId}/resources/${resourceId}`, {
    method: 'PUT',
    body: payload
  });
}

export function deleteResource(courseId: number, resourceId: number) {
  return request<void>(`/api/v1/courses/${courseId}/resources/${resourceId}`, {
    method: 'DELETE'
  });
}
