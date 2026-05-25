import type {
  CreateGradeItemPayload,
  GradeItem,
  GradeRuleValidationResult,
  UpdateGradeItemPayload
} from '../../types/grd';

interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface GradeItemAuthContext {
  userId: number | string;
  userRole: 'TEACHER' | 'ADMIN';
  manageableCourseIds: Array<number | string> | '*';
}

type GradeItemAuthContextProvider = () => GradeItemAuthContext | null;

let authContextProvider: GradeItemAuthContextProvider | null = null;

export function configureGradeItemAuthContext(provider: GradeItemAuthContextProvider | null) {
  authContextProvider = provider;
}

export async function listGradeItems(courseId: number): Promise<GradeItem[]> {
  const response = await fetch(`/api/v1/courses/${courseId}/grade-items`, {
    headers: jsonHeaders()
  });
  return unwrap<GradeItem[]>(response);
}

export async function createGradeItem(courseId: number, payload: CreateGradeItemPayload): Promise<GradeItem> {
  const response = await fetch(`/api/v1/courses/${courseId}/grade-items`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify(payload)
  });
  return unwrap<GradeItem>(response);
}

export async function updateGradeItem(gradeItemId: number, payload: UpdateGradeItemPayload): Promise<GradeItem> {
  const response = await fetch(`/api/v1/grade-items/${gradeItemId}`, {
    method: 'PUT',
    headers: jsonHeaders(),
    body: JSON.stringify(payload)
  });
  return unwrap<GradeItem>(response);
}

export async function deleteGradeItem(gradeItemId: number): Promise<GradeItem> {
  const response = await fetch(`/api/v1/grade-items/${gradeItemId}`, {
    method: 'DELETE',
    headers: jsonHeaders()
  });
  return unwrap<GradeItem>(response);
}

export async function validateGradeRules(
  courseId: number,
  gradeItems?: CreateGradeItemPayload[]
): Promise<GradeRuleValidationResult> {
  const response = await fetch(`/api/v1/courses/${courseId}/grade-rules/validate`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ gradeItems: gradeItems ?? [] })
  });
  return unwrap<GradeRuleValidationResult>(response);
}

function jsonHeaders() {
  const authContext = resolveAuthContext();
  return {
    'Content-Type': 'application/json',
    'X-User-Id': String(authContext.userId),
    'X-User-Role': authContext.userRole,
    'X-Manageable-Course-Ids': formatManageableCourseIds(authContext.manageableCourseIds)
  };
}

function resolveAuthContext(): GradeItemAuthContext {
  const configuredContext = authContextProvider?.();
  if (configuredContext) {
    return configuredContext;
  }
  if (typeof window !== 'undefined' && typeof window.localStorage?.getItem === 'function') {
    const userId = window.localStorage.getItem('onlinejudge.userId');
    const userRole = window.localStorage.getItem('onlinejudge.userRole');
    const manageableCourseIds = window.localStorage.getItem('onlinejudge.manageableCourseIds');
    if ((userRole === 'TEACHER' || userRole === 'ADMIN') && userId && manageableCourseIds) {
      return {
        userId,
        userRole,
        manageableCourseIds: manageableCourseIds === '*' ? '*' : manageableCourseIds.split(',').map((id) => id.trim())
      };
    }
  }
  throw new Error('当前登录态缺失，无法访问成绩项接口');
}

function formatManageableCourseIds(courseIds: Array<number | string> | '*') {
  if (courseIds === '*') {
    return '*';
  }
  return courseIds.map(String).join(',');
}

async function unwrap<T>(response: Response): Promise<T> {
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || body.code !== '0') {
    throw new Error(body.message || '成绩项请求失败');
  }
  return body.data;
}
