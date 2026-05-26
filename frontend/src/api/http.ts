export interface AuthContext {
  userId: number | string;
  username?: string;
  role: string;
  permissions?: string[];
  courseIds?: Array<number | string> | '*';
  manageableCourseIds?: Array<number | string> | '*';
}

export interface RequestOptions {
  method?: string;
  headers?: Record<string, string>;
  body?: unknown;
}

interface ApiResponse<T> {
  code: string | number;
  message: string;
  data: T;
}

type AuthContextProvider = () => AuthContext | null;

let authContextProvider: AuthContextProvider | null = null;

export function configureAuthContext(provider: AuthContextProvider | null) {
  authContextProvider = provider;
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers: {
      ...requestHeaders(options.body),
      ...options.headers
    },
    body: formatBody(options.body)
  });
  return unwrap<T>(response);
}

function requestHeaders(body: unknown) {
  const authContext = resolveAuthContext();
  const headers: Record<string, string> = {
    'X-User-Id': String(authContext.userId),
    'X-Username': authContext.username ?? '',
    'X-User-Role': authContext.role,
    'X-Permissions': (authContext.permissions ?? []).join(','),
    'X-Course-Ids': formatCourseIds(authContext.courseIds ?? authContext.manageableCourseIds ?? []),
    'X-Manageable-Course-Ids': formatCourseIds(authContext.manageableCourseIds ?? [])
  };
  if (!(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

function resolveAuthContext(): AuthContext {
  const configuredContext = authContextProvider?.();
  if (configuredContext) {
    return configuredContext;
  }
  if (typeof window !== 'undefined' && typeof window.localStorage?.getItem === 'function') {
    const userId = window.localStorage.getItem('onlinejudge.userId');
    const role = window.localStorage.getItem('onlinejudge.userRole') ?? window.localStorage.getItem('onlinejudge.role');
    const username = window.localStorage.getItem('onlinejudge.username') ?? undefined;
    const permissions = window.localStorage.getItem('onlinejudge.permissions');
    const courseIds = window.localStorage.getItem('onlinejudge.courseIds');
    const manageableCourseIds = window.localStorage.getItem('onlinejudge.manageableCourseIds');
    if (userId && role) {
      return {
        userId,
        username,
        role,
        permissions: splitCsv(permissions),
        courseIds: parseCourseIds(courseIds ?? manageableCourseIds),
        manageableCourseIds: parseCourseIds(manageableCourseIds)
      };
    }
  }
  throw new Error('当前登录态缺失，无法访问接口');
}

function formatBody(body: unknown) {
  if (body === undefined) {
    return undefined;
  }
  if (body instanceof FormData) {
    return body;
  }
  return typeof body === 'string' ? body : JSON.stringify(body);
}

function formatCourseIds(courseIds: Array<number | string> | '*') {
  if (courseIds === '*') {
    return '*';
  }
  return courseIds.map(String).join(',');
}

function parseCourseIds(value: string | null): Array<string> | '*' {
  if (!value) {
    return [];
  }
  return value === '*' ? '*' : splitCsv(value);
}

function splitCsv(value: string | null) {
  if (!value) {
    return [];
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function isSuccessCode(code: string | number | undefined) {
  return code === '0' || code === 0 || code === '200' || code === 200;
}

async function unwrap<T>(response: Response): Promise<T> {
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !isSuccessCode(body.code)) {
    throw new Error(body.message || '接口请求失败');
  }
  return body.data;
}
