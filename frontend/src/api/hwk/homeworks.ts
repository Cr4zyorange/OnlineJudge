import type {
  HomeworkDetail,
  HomeworkEvaluation,
  HomeworkPayload,
  HomeworkSubmission,
  HomeworkSubmissionPayload,
  HomeworkSummary
} from '../../types/hwk';
import { configureAuthContext, request } from '../http';

export interface HomeworkAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'STUDENT' | 'ADMIN';
  courseIds?: Array<number | string> | '*';
  manageableCourseIds?: Array<number | string> | '*';
}

type HomeworkAuthContextProvider = () => HomeworkAuthContext | null;

let authContextProvider: HomeworkAuthContextProvider | null = null;

export function configureHomeworkAuthContext(provider: HomeworkAuthContextProvider | null) {
  authContextProvider = provider;
  configureAuthContext(() => {
    const context = authContextProvider?.();
    if (!context) {
      return null;
    }
    return {
      userId: context.userId,
      role: context.userRole,
      courseIds: context.courseIds ?? context.manageableCourseIds ?? [],
      manageableCourseIds: context.manageableCourseIds ?? []
    };
  });
}

export async function listHomeworks(courseId: number): Promise<HomeworkSummary[]> {
  return request<HomeworkSummary[]>(`/api/v1/homeworks?courseId=${courseId}`);
}

export async function getHomework(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}`);
}

export async function createHomework(payload: HomeworkPayload): Promise<HomeworkDetail> {
  return request<HomeworkDetail>('/api/v1/homeworks', {
    method: 'POST',
    body: payload
  });
}

export async function updateHomework(homeworkId: number, payload: HomeworkPayload): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}`, {
    method: 'PUT',
    body: payload
  });
}

export async function publishHomework(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/publish`, {
    method: 'PUT'
  });
}

export async function closeHomework(homeworkId: number): Promise<HomeworkDetail> {
  return request<HomeworkDetail>(`/api/v1/homeworks/${homeworkId}/close`, {
    method: 'PUT'
  });
}

export async function submitHomework(
  homeworkId: number,
  payload: HomeworkSubmissionPayload
): Promise<HomeworkSubmission> {
  return request<HomeworkSubmission>(`/api/v1/homeworks/${homeworkId}/submissions`, {
    method: 'POST',
    body: payload
  });
}

export async function listMyHomeworkSubmissions(homeworkId: number): Promise<HomeworkSubmission[]> {
  return request<HomeworkSubmission[]>(`/api/v1/homeworks/${homeworkId}/my-submissions`);
}

export async function listHomeworkSubmissions(homeworkId: number): Promise<HomeworkSubmission[]> {
  return request<HomeworkSubmission[]>(`/api/v1/homeworks/${homeworkId}/submissions`);
}

export async function getHomeworkSubmission(submissionId: number): Promise<HomeworkSubmission> {
  return request<HomeworkSubmission>(`/api/v1/submissions/${submissionId}`);
}

export async function getSubmissionEvaluation(submissionId: number): Promise<HomeworkEvaluation> {
  return request<HomeworkEvaluation>(`/api/v1/submissions/${submissionId}/evaluation`);
}

export async function reevaluateSubmission(submissionId: number): Promise<HomeworkEvaluation> {
  return request<HomeworkEvaluation>(`/api/v1/submissions/${submissionId}/reevaluate`, {
    method: 'POST'
  });
}
