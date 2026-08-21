import { publicRequest, request } from '../http';
import { resetRuntimeContext } from '../../app/runtimeContext';
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
  roleId?: number;
  roleCode: string;
  roleName: string;
  description?: string;
  enabled: boolean;
}

export interface LoginPayload {
  account: string;
  password: string;
}

export interface ProfileUpdatePayload {
  displayName: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
}

export interface PasswordChangePayload {
  oldPassword: string;
  newPassword: string;
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

export interface AuditLogQuery {
  operatorId?: number;
  operationType?: string;
  resultStatus?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export interface AuditLogRecord {
  logId: number;
  operatorId?: number | null;
  operationType: string;
  targetType?: string | null;
  targetId?: string | null;
  resultStatus: string;
  failureReason?: string | null;
  clientIp?: string | null;
  userAgent?: string | null;
  createdAt: string;
}

export interface LoginResult {
  token: string;
  expiresAt: string;
  user: AuthUser;
}

export interface PermissionCheckPayload {
  permissionCode: string;
  resourceType?: string;
  resourceId?: string;
}

export interface PermissionCheckResult {
  allowed: boolean;
  permissionCode: string;
  resourceType?: string;
  resourceId?: string;
  reason?: string | null;
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

export function getProfile() {
  return request<AuthUser>('/api/v1/users/me');
}

export function updateProfile(payload: ProfileUpdatePayload) {
  return request<AuthUser>('/api/v1/users/me', {
    method: 'PUT',
    body: payload
  });
}

export function changePassword(payload: PasswordChangePayload) {
  return request<void>('/api/v1/users/me/password', {
    method: 'PUT',
    body: payload
  });
}

export function checkPermission(payload: PermissionCheckPayload) {
  return request<PermissionCheckResult>('/api/v1/auth/check-permission', {
    method: 'POST',
    body: payload
  });
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

export function listAuditLogs(query: AuditLogQuery = {}) {
  const params = new URLSearchParams();
  addQuery(params, 'operatorId', query.operatorId);
  addQuery(params, 'operationType', query.operationType);
  addQuery(params, 'resultStatus', query.resultStatus);
  addQuery(params, 'startTime', query.startTime);
  addQuery(params, 'endTime', query.endTime);
  addQuery(params, 'page', query.page);
  addQuery(params, 'size', query.size);
  const suffix = params.toString();
  return request<PageResult<AuditLogRecord>>(`/api/v1/admin/audit-logs${suffix ? `?${suffix}` : ''}`);
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

export function updateUserStatus(userId: number, accountStatus: string) {
  return request<AuthUser>(`/api/v1/admin/users/${userId}/status`, {
    method: 'PUT',
    body: { accountStatus }
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
  return request<RoleView>('/api/v1/admin/roles', {
    method: 'PUT',
    body: { roleId, ...payload }
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
  resetRuntimeContext();
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
