import { request } from '../http';
import type {
  HomeworkDetail,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkStatus,
  HomeworkSubmissionPayload,
  HomeworkSubmissionSummary,
  HomeworkSummary,
  HomeworkTestCasePayload,
  PageResponse
} from '../../types/hwk';

export interface HomeworkListQuery {
  courseId: number;
  status?: HomeworkStatus;
  keyword?: string;
  page?: number;
  size?: number;
}

export function listHomeworks(query: HomeworkListQuery): Promise<PageResponse<HomeworkSummary>> {
  const params = new URLSearchParams({
    courseId: String(query.courseId),
    page: String(query.page ?? 1),
    size: String(query.size ?? 20)
  });
  if (query.status) {
    params.set('status', query.status);
  }
  if (query.keyword?.trim()) {
    params.set('keyword', query.keyword.trim());
  }
  return request<PageResponse<HomeworkSummary>>(`/api/v1/homeworks?${params.toString()}`);
}

export function createHomework(payload: HomeworkPayload): Promise<HomeworkDetail> {
  return request<HomeworkDetail>('/api/v1/homeworks', {
    method: 'POST',
    body: payload
  });
}

export function updateHomework(homeworkId: number, payload: HomeworkPayload): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}`, {
    method: 'PUT',
    body: payload
  });
}

export function getHomeworkDetail(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}`);
}

export function saveHomeworkQuestions(
  homeworkId: number,
  questions: HomeworkQuestionPayload[]
): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/questions`, {
    method: 'PUT',
    body: questions
  });
}

export function saveHomeworkTestCases(
  homeworkId: number,
  testCases: HomeworkTestCasePayload[]
): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/test-cases`, {
    method: 'PUT',
    body: testCases
  });
}

export function publishHomework(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/publish`, {
    method: 'PUT'
  });
}

export function closeHomework(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/close`, {
    method: 'PUT'
  });
}

export function submitHomework(
  homeworkId: number,
  payload: HomeworkSubmissionPayload
): Promise<HomeworkSubmissionSummary> {
  return request<HomeworkSubmissionSummary>(`/api/v1/homeworks/${homeworkId}/submissions`, {
    method: 'POST',
    body: payload
  });
}
