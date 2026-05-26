import type { CourseGradeRow, GradeRecalculationResult, GradeSyncResult } from '../../types/grd';
import { configureAuthContext, request } from '../http';

export interface GradeRecordAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'ADMIN';
  manageableCourseIds: Array<number | string> | '*';
}

type GradeRecordAuthContextProvider = () => GradeRecordAuthContext | null;

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

export async function listCourseGrades(courseId: number): Promise<CourseGradeRow[]> {
  return request<CourseGradeRow[]>(`/api/v1/courses/${courseId}/grades`);
}
