import type {
  LabSubmissionResult,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionListFilters,
  LabExperimentDetail,
  LabExperimentPayload,
  LabExperimentStatus,
  LabExperimentSummary,
  LabSubmissionPayload,
  LabSubmissionSummary
} from '../../types/lab';
import { configureAuthContext, request } from '../http';

export interface LabAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'ADMIN';
  courseIds?: Array<number | string> | '*';
  manageableCourseIds: Array<number | string> | '*';
}

type LabAuthContextProvider = () => LabAuthContext | null;

let authContextProvider: LabAuthContextProvider | null = null;

export function configureLabAuthContext(provider: LabAuthContextProvider | null) {
  authContextProvider = provider;
  configureAuthContext(() => {
    const context = authContextProvider?.();
    if (!context) {
      return null;
    }
    return {
      userId: context.userId,
      role: context.userRole,
      courseIds: context.courseIds ?? context.manageableCourseIds,
      manageableCourseIds: context.manageableCourseIds
    };
  });
}

export async function listLabs(courseId: number, status?: LabExperimentStatus): Promise<LabExperimentSummary[]> {
  const query = status ? `?status=${status}` : '';
  return request<LabExperimentSummary[]>(`/api/v1/courses/${courseId}/labs${query}`);
}

export async function createLab(courseId: number, payload: LabExperimentPayload): Promise<LabExperimentDetail> {
  return request<LabExperimentDetail>(`/api/v1/courses/${courseId}/labs`, {
    method: 'POST',
    body: payload
  });
}

export async function getLabDetail(labId: number): Promise<LabExperimentDetail> {
  return request<LabExperimentDetail>(`/api/v1/labs/${labId}`);
}

export async function updateLab(labId: number, payload: LabExperimentPayload): Promise<LabExperimentDetail> {
  return request<LabExperimentDetail>(`/api/v1/labs/${labId}`, {
    method: 'PUT',
    body: payload
  });
}

export async function deleteLab(labId: number): Promise<LabExperimentSummary> {
  return request<LabExperimentSummary>(`/api/v1/labs/${labId}`, {
    method: 'DELETE'
  });
}

export async function publishLab(labId: number): Promise<LabExperimentSummary> {
  return request<LabExperimentSummary>(`/api/v1/labs/${labId}/publish`, {
    method: 'POST'
  });
}

export async function closeLab(labId: number): Promise<LabExperimentSummary> {
  return request<LabExperimentSummary>(`/api/v1/labs/${labId}/close`, {
    method: 'POST'
  });
}

export async function submitLab(labId: number, payload: LabSubmissionPayload): Promise<LabSubmissionSummary> {
  const formData = new FormData();
  formData.append('language', payload.language);
  if (payload.code) {
    formData.append('code', payload.code);
  }
  if (payload.file) {
    formData.append('file', payload.file);
  }
  return request<LabSubmissionSummary>(`/api/v1/labs/${labId}/submissions`, {
    method: 'POST',
    body: formData
  });
}

export async function listLabSubmissions(
  labId: number,
  filters: LabSubmissionListFilters = {}
): Promise<LabSubmissionHistoryItem[]> {
  const query = new URLSearchParams();
  if (filters.studentId !== undefined) {
    query.set('studentId', String(filters.studentId));
  }
  if (filters.submitStatus) {
    query.set('submitStatus', filters.submitStatus);
  }
  if (filters.evaluationStatus) {
    query.set('evaluationStatus', filters.evaluationStatus);
  }
  if (filters.overdue !== undefined) {
    query.set('overdue', String(filters.overdue));
  }
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return request<LabSubmissionHistoryItem[]>(`/api/v1/labs/${labId}/submissions${suffix}`);
}

export async function getLabSubmissionDetail(labId: number, submissionId: number): Promise<LabSubmissionDetail> {
  return request<LabSubmissionDetail>(`/api/v1/labs/${labId}/submissions/${submissionId}`);
}

export async function getLabSubmissionResult(labId: number, submissionId: number): Promise<LabSubmissionResult> {
  return request<LabSubmissionResult>(`/api/v1/labs/${labId}/submissions/${submissionId}/result`);
}

export async function evaluateLabSubmission(labId: number, submissionId: number): Promise<LabSubmissionResult> {
  return request<LabSubmissionResult>(`/api/v1/labs/${labId}/submissions/${submissionId}/evaluate`, {
    method: 'POST'
  });
}
