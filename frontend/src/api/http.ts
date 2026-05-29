import { readAuthStorage } from './auth/storage';

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
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !isSuccessCode(body.code)) {
    throw new Error(body.message || '接口请求失败');
  }
  return body.data;
}

async function errorMessage(response: Response) {
  try {
    const body = (await response.json()) as Partial<ApiResponse<unknown>>;
    return body.message || '接口请求失败';
  } catch {
    return response.statusText || '接口请求失败';
  }
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
