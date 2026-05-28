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

export interface PageResult<T> {
  records: T[];
  total: number;
}

export interface RolePermission {
  permissionId: number;
  permissionCode: string;
  permissionName: string;
  permissionType?: string;
  moduleCode?: string;
  resourcePattern?: string;
  enabled?: boolean;
}

export interface RoleView {
  roleId: number;
  roleCode: string;
  roleName: string;
  description?: string;
  enabled: boolean;
  permissions: RolePermission[];
}

export interface RolePayload {
  roleCode: string;
  roleName: string;
  description?: string;
  enabled: boolean;
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

export interface AdminUserPayload extends RegisterPayload {
  roleIds?: number[];
}

export interface UserQuery {
  keyword?: string;
  role?: string;
  status?: string;
  page?: number;
  size?: number;
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

export function listUsers(query: UserQuery = {}) {
  const params = new URLSearchParams();
  addQuery(params, 'keyword', query.keyword);
  addQuery(params, 'role', query.role);
  addQuery(params, 'status', query.status);
  addQuery(params, 'page', query.page);
  addQuery(params, 'size', query.size);
  const suffix = params.toString();
  return request<PageResult<AuthUser>>(`/api/v1/admin/users${suffix ? `?${suffix}` : ''}`);
}

export function createAdminUser(payload: AdminUserPayload) {
  return request<AuthUser>('/api/v1/admin/users', {
    method: 'POST',
    body: payload
  });
}

export function updateUserRoles(userId: number, roleIds: number[]) {
  return request<AuthUser>(`/api/v1/admin/users/${userId}/roles`, {
    method: 'PUT',
    body: { roleIds }
  });
}

export function listRoles() {
  return request<RoleView[]>('/api/v1/admin/roles');
}

export function createRole(payload: RolePayload) {
  return request<RoleView>('/api/v1/admin/roles', {
    method: 'POST',
    body: payload
  });
}

export function updateRole(roleId: number, payload: RolePayload) {
  return request<RoleView>(`/api/v1/admin/roles/${roleId}`, {
    method: 'PUT',
    body: payload
  });
}

export function listPermissions() {
  return request<RolePermission[]>('/api/v1/admin/permissions');
}

export function updateRolePermissions(roleId: number, permissionIds: number[]) {
  return request<RoleView>(`/api/v1/admin/roles/${roleId}/permissions`, {
    method: 'PUT',
    body: { permissionIds }
  });
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

function addQuery(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') {
    params.set(key, String(value));
  }
}
