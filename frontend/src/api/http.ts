import { readAuthStorage, removeAuthStorage } from './auth/storage';

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
  auth?: boolean;
}

export interface BlobResponse {
  blob: Blob;
  filename?: string;
}

interface ApiResponse<T> {
  code: string | number;
  message: string;
  data: T;
}

type AuthContextProvider = () => AuthContext | null;

const NAVIGATION_EVENT = 'onlinejudge:navigation';

export function configureAuthContext(_provider: AuthContextProvider | null) {
  // Bearer/session auth is the only runtime identity source.
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers: {
      ...requestHeaders(options.auth !== false, options.body),
      ...options.headers
    },
    body: formatBody(options.body)
  });
  return unwrap<T>(response);
}

export async function requestBlob(url: string, options: RequestOptions = {}): Promise<BlobResponse> {
  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers: {
      ...requestHeaders(options.auth !== false, options.body),
      ...options.headers
    },
    body: formatBody(options.body)
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return {
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get('Content-Disposition'))
  };
}

export function publicRequest<T>(url: string, options: Omit<RequestOptions, 'auth'> = {}): Promise<T> {
  return request<T>(url, {
    ...options,
    auth: false
  });
}

function requestHeaders(requireAuth: boolean, body: unknown) {
  const headers: Record<string, string> = {};
  if (!(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (requireAuth) {
    const token = storedToken();
    if (!token) {
      redirectTo('/login');
      throw new Error('当前登录态缺失，无法访问接口');
    }
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

function storedToken() {
  return readAuthStorage('onlinejudge.authToken');
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

function isSuccessCode(code: string | number | undefined) {
  return code === '0' || code === 0 || code === '200' || code === 200;
}

async function unwrap<T>(response: Response): Promise<T> {
  const body = await readApiResponse<T>(response);
  if (!response.ok || !isSuccessCode(body.code)) {
    handleAuthFailure(body.code);
    throw new Error(body.message || '接口请求失败');
  }
  return body.data;
}

async function errorMessage(response: Response) {
  try {
    const body = await readApiResponse<unknown>(response);
    handleAuthFailure(body.code);
    return body.message || '接口请求失败';
  } catch {
    return response.statusText || '接口请求失败';
  }
}

async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  if (typeof response.text === 'function') {
    const text = await response.text();
    if (!text.trim()) {
      return {
        code: response.ok ? '0' : response.status,
        message: response.statusText,
        data: undefined as T
      };
    }
    try {
      return JSON.parse(text) as ApiResponse<T>;
    } catch {
      return {
        code: response.status,
        message: text,
        data: undefined as T
      };
    }
  }
  return (await response.json()) as ApiResponse<T>;
}

function filenameFromDisposition(disposition: string | null) {
  if (!disposition) {
    return undefined;
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match) {
    return decodeURIComponent(utf8Match[1]);
  }
  const quotedMatch = disposition.match(/filename="([^"]+)"/i);
  if (quotedMatch) {
    return quotedMatch[1];
  }
  const plainMatch = disposition.match(/filename=([^;]+)/i);
  return plainMatch?.[1]?.trim();
}

function handleAuthFailure(code: string | number | undefined) {
  if (code === 'ERR-AUTH-03') {
    clearStoredAuthSession();
    redirectTo('/account-disabled');
    return;
  }
  if (code === 'ERR-AUTH-04') {
    clearStoredAuthSession();
    redirectTo('/session-expired');
    return;
  }
  if (code === 'ERR-AUTH-05') {
    redirectTo('/403');
  }
}

function clearStoredAuthSession() {
  [
    'onlinejudge.authToken',
    'onlinejudge.authExpiresAt',
    'onlinejudge.userId',
    'onlinejudge.username',
    'onlinejudge.userRole',
    'onlinejudge.role',
    'onlinejudge.permissions'
  ].forEach((key) => removeAuthStorage(key));
}

function redirectTo(path: string) {
  if (typeof window === 'undefined') {
    return;
  }
  if (window.location.pathname !== path) {
    window.history.pushState({}, '', path);
  }
  window.dispatchEvent(new Event(NAVIGATION_EVENT));
}
