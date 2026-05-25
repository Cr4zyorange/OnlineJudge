import type {
  CreateGradeItemPayload,
  GradeItem,
  GradeRuleValidationResult,
  UpdateGradeItemPayload
} from '../../types/grd';
import { configureAuthContext, request } from '../http';

export interface GradeItemAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'ADMIN';
  manageableCourseIds: Array<number | string> | '*';
}

type GradeItemAuthContextProvider = () => GradeItemAuthContext | null;

let authContextProvider: GradeItemAuthContextProvider | null = null;

export function configureGradeItemAuthContext(provider: GradeItemAuthContextProvider | null) {
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

export async function listGradeItems(courseId: number): Promise<GradeItem[]> {
  return request<GradeItem[]>(`/api/v1/courses/${courseId}/grade-items`);
}

export async function createGradeItem(courseId: number, payload: CreateGradeItemPayload): Promise<GradeItem> {
  return request<GradeItem>(`/api/v1/courses/${courseId}/grade-items`, {
    method: 'POST',
    body: payload
  });
}

export async function updateGradeItem(gradeItemId: number, payload: UpdateGradeItemPayload): Promise<GradeItem> {
  return request<GradeItem>(`/api/v1/grade-items/${gradeItemId}`, {
    method: 'PUT',
    body: payload
  });
}

export async function deleteGradeItem(gradeItemId: number): Promise<GradeItem> {
  return request<GradeItem>(`/api/v1/grade-items/${gradeItemId}`, {
    method: 'DELETE'
  });
}

export async function validateGradeRules(
  courseId: number,
  gradeItems?: CreateGradeItemPayload[]
): Promise<GradeRuleValidationResult> {
  return request<GradeRuleValidationResult>(`/api/v1/courses/${courseId}/grade-rules/validate`, {
    method: 'POST',
    body: { gradeItems: gradeItems ?? [] }
  });
}
