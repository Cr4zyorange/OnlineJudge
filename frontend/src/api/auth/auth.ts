import { publicRequest, request } from '../http';

export interface AuthUser {
  id: number;
  username: string;
  userType: string;
  displayName: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  accountStatus?: string;
  roles: string[];
  permissions: string[];
}

export interface LoginPayload {
  account: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  userType: string;
  displayName: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
}

export interface LoginResult {
  token: string;
  expiresAt: string;
  user: AuthUser;
}

export async function login(payload: LoginPayload) {
  const result = await publicRequest<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: payload
  });
  persistAuthSession(result);
  return result;
}

export function register(payload: RegisterPayload) {
  return publicRequest<AuthUser>('/api/v1/auth/register', {
    method: 'POST',
    body: payload
  });
}

export function getCurrentUser() {
  return request<AuthUser>('/api/v1/auth/me');
}

export async function logout() {
  try {
    await request<void>('/api/v1/auth/logout', { method: 'POST' });
  } finally {
    clearAuthSession();
  }
}

export function clearAuthSession() {
  if (typeof window === 'undefined') {
    return;
  }
  [
    'onlinejudge.authToken',
    'onlinejudge.authExpiresAt',
    'onlinejudge.userId',
    'onlinejudge.username',
    'onlinejudge.userRole',
    'onlinejudge.role',
    'onlinejudge.permissions'
  ].forEach((key) => window.localStorage.removeItem(key));
}

function persistAuthSession(result: LoginResult) {
  window.localStorage.setItem('onlinejudge.authToken', result.token);
  window.localStorage.setItem('onlinejudge.authExpiresAt', result.expiresAt);
  window.localStorage.setItem('onlinejudge.userId', String(result.user.id));
  window.localStorage.setItem('onlinejudge.username', result.user.username);
  const primaryRole = result.user.roles[0] ?? result.user.userType;
  window.localStorage.setItem('onlinejudge.userRole', primaryRole);
  window.localStorage.setItem('onlinejudge.role', primaryRole);
  window.localStorage.setItem('onlinejudge.permissions', result.user.permissions.join(','));
}
