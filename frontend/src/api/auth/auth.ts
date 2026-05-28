import { publicRequest, request } from '../http';
import { removeAuthStorage, writeAuthStorage } from './storage';

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

function persistAuthSession(result: LoginResult) {
  writeAuthStorage('onlinejudge.authToken', result.token);
  writeAuthStorage('onlinejudge.authExpiresAt', result.expiresAt);
  writeAuthStorage('onlinejudge.userId', String(result.user.id));
  writeAuthStorage('onlinejudge.username', result.user.username);
  const primaryRole = result.user.roles[0] ?? result.user.userType;
  writeAuthStorage('onlinejudge.userRole', primaryRole);
  writeAuthStorage('onlinejudge.role', primaryRole);
  writeAuthStorage('onlinejudge.permissions', result.user.permissions.join(','));
}
