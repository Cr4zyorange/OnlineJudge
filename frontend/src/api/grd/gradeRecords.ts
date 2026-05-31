import type {
  CourseGradeRow,
  CourseGradeTablePage,
  GradeAdjustmentPayload,
  GradeAdjustmentResult,
  FinalScoreAdjustmentResult,
  GradeChangeLogPage,
  GradePublishPayload,
  GradePublishRecordPage,
  GradePublishResult,
  GradeRecalculationResult,
  GradeStatus,
  GradeSyncResult,
  PublishStatus
} from '../../types/grd';
import { configureAuthContext, request } from '../http';

export interface GradeRecordAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'ADMIN';
  manageableCourseIds: Array<number | string> | '*';
}

type GradeRecordAuthContextProvider = () => GradeRecordAuthContext | null;

export interface GradeTableQuery {
  studentKeyword?: string;
  gradeItemId?: number;
  gradeStatus?: GradeStatus;
  publishStatus?: PublishStatus;
  page?: number;
  size?: number;
}

export interface GradeChangeLogQuery {
  studentId?: number;
  gradeItemId?: number;
  page?: number;
  size?: number;
}

export interface GradePublishRecordQuery {
  page?: number;
  size?: number;
}

let authContextProvider: GradeRecordAuthContextProvider | null = null;

export function configureGradeRecordAuthContext(provider: GradeRecordAuthContextProvider | null) {
  authContextProvider = provider;
  configureAuthContext(() => {
    const context = authContextProvider?.();
    if (!context) {
      return null;
    }
    return {
      userId: context.userId,
      role: context.userRole,
      courseIds: context.manageableCourseIds,
      manageableCourseIds: context.manageableCourseIds
    };
  });
}

export async function syncSourceGrades(courseId: number): Promise<GradeSyncResult> {
  return request<GradeSyncResult>(`/api/v1/courses/${courseId}/grades/sync`, {
    method: 'POST'
  });
}

export async function recalculateCourseGrades(courseId: number): Promise<GradeRecalculationResult> {
  return request<GradeRecalculationResult>(`/api/v1/courses/${courseId}/grades/recalculate`, {
    method: 'POST'
  });
}

export async function publishCourseGrades(
  courseId: number,
  payload: GradePublishPayload
): Promise<GradePublishResult> {
  return request<GradePublishResult>(`/api/v1/courses/${courseId}/grades/publish`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export async function listCourseGrades(
  courseId: number,
  query: GradeTableQuery = {}
): Promise<CourseGradeTablePage> {
  const params = new URLSearchParams();
  appendParam(params, 'studentKeyword', query.studentKeyword);
  appendParam(params, 'gradeItemId', query.gradeItemId);
  appendParam(params, 'gradeStatus', query.gradeStatus);
  appendParam(params, 'publishStatus', query.publishStatus);
  appendParam(params, 'page', query.page);
  appendParam(params, 'size', query.size);
  const queryString = params.toString();
  const url = queryString
    ? `/api/v1/courses/${courseId}/grades?${queryString}`
    : `/api/v1/courses/${courseId}/grades`;
  return request<CourseGradeTablePage>(url);
}

export async function getMyPublishedGrades(courseId: number): Promise<CourseGradeRow> {
  return request<CourseGradeRow>(`/api/v1/courses/${courseId}/my-grades`);
}

export async function adjustGradeRecord(
  recordId: number,
  payload: GradeAdjustmentPayload
): Promise<GradeAdjustmentResult> {
  return request<GradeAdjustmentResult>(`/api/v1/grade-records/${recordId}/adjust`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function adjustCourseFinalScore(
  summaryId: number,
  payload: GradeAdjustmentPayload
): Promise<FinalScoreAdjustmentResult> {
  return request<FinalScoreAdjustmentResult>(`/api/v1/course-grade-summaries/${summaryId}/adjust`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function listGradeChangeLogs(
  courseId: number,
  query: GradeChangeLogQuery = {}
): Promise<GradeChangeLogPage> {
  const params = new URLSearchParams();
  appendParam(params, 'studentId', query.studentId);
  appendParam(params, 'gradeItemId', query.gradeItemId);
  appendParam(params, 'page', query.page);
  appendParam(params, 'size', query.size);
  const queryString = params.toString();
  const url = queryString
    ? `/api/v1/courses/${courseId}/grade-change-logs?${queryString}`
    : `/api/v1/courses/${courseId}/grade-change-logs`;
  return request<GradeChangeLogPage>(url);
}

export async function listGradePublishRecords(
  courseId: number,
  query: GradePublishRecordQuery = {}
): Promise<GradePublishRecordPage> {
  const params = new URLSearchParams();
  appendParam(params, 'page', query.page);
  appendParam(params, 'size', query.size);
  const queryString = params.toString();
  const url = queryString
    ? `/api/v1/courses/${courseId}/grade-publish-records?${queryString}`
    : `/api/v1/courses/${courseId}/grade-publish-records`;
  return request<GradePublishRecordPage>(url);
}

function appendParam(params: URLSearchParams, name: string, value: string | number | undefined) {
  if (value === undefined || value === '') {
    return;
  }
  params.append(name, String(value));
}
