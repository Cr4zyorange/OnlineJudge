import { request } from '../http';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkEvaluationStatus,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkReviewStatus,
  HomeworkStatus,
  HomeworkSubmissionDetail,
  HomeworkSubmissionPayload,
  HomeworkSubmissionSummary,
  HomeworkSubmitStatus,
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

export interface HomeworkSubmissionListQuery {
  studentKeyword?: string;
  submitStatus?: HomeworkSubmitStatus;
  evaluationStatus?: HomeworkEvaluationStatus;
  reviewStatus?: HomeworkReviewStatus;
  page?: number;
  size?: number;
}

export function listMyHomeworkSubmissions(homeworkId: number): Promise<HomeworkSubmissionSummary[]> {
  return request<HomeworkSubmissionSummary[]>(`/api/v1/homeworks/${homeworkId}/my-submissions`);
}

export function listHomeworkSubmissions(
  homeworkId: number,
  query: HomeworkSubmissionListQuery = {}
): Promise<PageResponse<HomeworkSubmissionSummary>> {
  const params = new URLSearchParams({
    page: String(query.page ?? 1),
    size: String(query.size ?? 20)
  });
  if (query.studentKeyword?.trim()) {
    params.set('studentKeyword', query.studentKeyword.trim());
  }
  if (query.submitStatus) {
    params.set('submitStatus', query.submitStatus);
  }
  if (query.evaluationStatus) {
    params.set('evaluationStatus', query.evaluationStatus);
  }
  if (query.reviewStatus) {
    params.set('reviewStatus', query.reviewStatus);
  }
  return request<PageResponse<HomeworkSubmissionSummary>>(
    `/api/v1/homeworks/${homeworkId}/submissions?${params.toString()}`
  );
}

export function getHomeworkSubmission(submissionId: number): Promise<HomeworkSubmissionDetail> {
  return request<HomeworkSubmissionDetail>(`/api/v1/submissions/${submissionId}`);
}

export function getHomeworkSubmissionEvaluation(submissionId: number): Promise<HomeworkEvaluationResult> {
  return request<HomeworkEvaluationResult>(`/api/v1/submissions/${submissionId}/evaluation`);
}

export function getHomeworkEvaluationLogs(evaluationId: number): Promise<HomeworkEvaluationResult> {
  return request<HomeworkEvaluationResult>(`/api/v1/evaluations/${evaluationId}/logs`);
}

export function reevaluateHomeworkSubmission(submissionId: number): Promise<HomeworkEvaluationResult> {
  return request<HomeworkEvaluationResult>(`/api/v1/submissions/${submissionId}/reevaluate`, {
    method: 'POST'
  });
}
