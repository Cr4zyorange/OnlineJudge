import { request } from '../http';
import type {
  HomeworkDetail,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkStatus,
  HomeworkSubmission,
  HomeworkSubmissionPayload,
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

export function submitHomework(homeworkId: number, payload: HomeworkSubmissionPayload): Promise<HomeworkSubmission> {
  return request<HomeworkSubmission>(`/api/v1/homeworks/${homeworkId}/submissions`, {
    method: 'POST',
    body: payload
  });
}

export function listMyHomeworkSubmissions(homeworkId: number): Promise<HomeworkSubmission[]> {
  return request<HomeworkSubmission[]>(`/api/v1/homeworks/${homeworkId}/my-submissions`);
}

export interface HomeworkSubmissionListQuery {
  studentId?: number;
  submitStatus?: string;
  evaluationStatus?: string;
  reviewStatus?: string;
  page?: number;
  size?: number;
}

export function listHomeworkSubmissions(
  homeworkId: number,
  query: HomeworkSubmissionListQuery = {}
): Promise<PageResponse<HomeworkSubmission>> {
  const params = new URLSearchParams({
    page: String(query.page ?? 1),
    size: String(query.size ?? 20)
  });
  if (query.studentId !== undefined) {
    params.set('studentId', String(query.studentId));
  }
  if (query.submitStatus?.trim()) {
    params.set('submitStatus', query.submitStatus.trim());
  }
  if (query.evaluationStatus?.trim()) {
    params.set('evaluationStatus', query.evaluationStatus.trim());
  }
  if (query.reviewStatus?.trim()) {
    params.set('reviewStatus', query.reviewStatus.trim());
  }
  return request<PageResponse<HomeworkSubmission>>(`/api/v1/homeworks/${homeworkId}/submissions?${params.toString()}`);
}

export function getHomeworkSubmission(submissionId: number): Promise<HomeworkSubmission> {
  return request<HomeworkSubmission>(`/api/v1/submissions/${submissionId}`);
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
