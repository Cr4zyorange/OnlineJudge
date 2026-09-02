import { request, requestBlob } from '../http';
import type {
  Chapter,
  ChapterPayload,
  Course,
  CourseJoinPayload,
  CourseMember,
  CourseAnnouncement,
  AnnouncementPayload,
  CourseHomeSummary,
  CoursePayload,
  CoursePermission,
  CourseResource,
  PageResponse,
  ResourcePayload
} from '../../types/crs';

export type CourseScope = 'all' | 'mine' | 'managed' | 'archived';

export function listCourses(keyword = '', scope: CourseScope = 'all') {
  const params = new URLSearchParams({ page: '0', size: '20', scope });
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

export function joinCourse(courseId: number, payload?: CourseJoinPayload) {
  return request<CoursePermission>(`/api/v1/courses/${courseId}/join`, {
    method: 'POST',
    body: payload ?? {}
  });
}

export function listCourseMembers(courseId: number, status?: CourseMember['status']) {
  const params = status ? `?status=${status}` : '';
  return request<CourseMember[]>(`/api/v1/courses/${courseId}/members${params}`);
}

export function updateCourseMember(courseId: number, userId: number, payload: Pick<CourseMember, 'status'> & Partial<Pick<CourseMember, 'role'>>) {
  return request<CourseMember>(`/api/v1/courses/${courseId}/members/${userId}`, {
    method: 'PUT',
    body: payload
  });
}

export function removeCourseMember(courseId: number, userId: number) {
  return request<void>(`/api/v1/courses/${courseId}/members/${userId}`, {
    method: 'DELETE'
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

export function downloadResource(courseId: number, resourceId: number) {
  return requestBlob(`/api/v1/courses/${courseId}/resources/${resourceId}/download`);
}

export function listAnnouncements(courseId: number) {
  return request<CourseAnnouncement[]>(`/api/v1/courses/${courseId}/announcements`);
}

export function createAnnouncement(courseId: number, payload: AnnouncementPayload) {
  return request<CourseAnnouncement>(`/api/v1/courses/${courseId}/announcements`, {
    method: 'POST',
    body: payload
  });
}

export function updateAnnouncement(courseId: number, announcementId: number, payload: AnnouncementPayload) {
  return request<CourseAnnouncement>(`/api/v1/courses/${courseId}/announcements/${announcementId}`, {
    method: 'PUT',
    body: payload
  });
}

export function pinAnnouncement(courseId: number, announcementId: number, isTop: boolean) {
  return request<CourseAnnouncement>(`/api/v1/courses/${courseId}/announcements/${announcementId}/top`, {
    method: 'PUT',
    body: { isTop }
  });
}

export function deleteAnnouncement(courseId: number, announcementId: number) {
  return request<void>(`/api/v1/courses/${courseId}/announcements/${announcementId}`, {
    method: 'DELETE'
  });
}

export function getCourseHomeSummary(courseId: number) {
  return request<CourseHomeSummary>(`/api/v1/courses/${courseId}/home-summary`);
}
