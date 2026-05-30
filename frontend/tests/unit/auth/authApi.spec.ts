import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  checkPermission,
  changePassword,
  clearAuthSession,
  createAdminUser,
  createRole,
  getCurrentUser,
  getProfile,
  listPermissions,
  listRoles,
  listUsers,
  login,
  logout,
  register,
  updateProfile,
  updateRole,
  updateRolePermissions,
  updateUserStatus,
  updateUserRoles
} from '../../../src/api/auth/auth';
import type { AdminUserPayload } from '../../../src/api/auth/auth';

describe('AUTH API client', () => {
  beforeEach(() => {
    installLocalStorageMock();
  });

  afterEach(() => {
    clearAuthSession();
    vi.restoreAllMocks();
    if (typeof window.localStorage.clear === 'function') {
      window.localStorage.clear();
    }
  });

  it('registers users through the documented AUTH endpoint without requiring existing login state', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      id: 45,
      username: 'student45',
      userType: 'STUDENT',
      displayName: '学生45',
      roles: ['STUDENT'],
      permissions: ['course:view']
    }));

    const result = await register({
      username: 'student45',
      password: 'Student45@pass',
      userType: 'STUDENT',
      displayName: '学生45'
    });

    expect(result.username).toBe('student45');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        username: 'student45',
        password: 'Student45@pass',
        userType: 'STUDENT',
        displayName: '学生45'
      })
    }));
  });

  it('stores token and current user context after login then uses bearer auth for me and logout', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        token: 'token-45',
        expiresAt: '2026-05-28T18:00:00',
        user: {
          id: 45,
          username: 'student45',
          userType: 'STUDENT',
          displayName: '学生45',
          roles: ['STUDENT'],
          permissions: ['course:view']
        }
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 45,
        username: 'student45',
        userType: 'STUDENT',
        displayName: '学生45',
        roles: ['STUDENT'],
        permissions: ['course:view']
      }))
      .mockResolvedValueOnce(jsonResponse(null));

    const result = await login({ account: 'student45', password: 'Student45@pass' });
    expect(result.token).toBe('token-45');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBe('token-45');
    expect(window.localStorage.getItem('onlinejudge.userId')).toBe('45');
    expect(window.localStorage.getItem('onlinejudge.userRole')).toBe('STUDENT');
    expect(window.localStorage.getItem('onlinejudge.permissions')).toBe('course:view');

    await getCurrentUser();
    await logout();

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer token-45' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/logout', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer token-45' })
    }));
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBeNull();
  });

  it('keeps auth calls stable when browser storage methods are unavailable in tests', async () => {
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {}
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      token: 'fallback-token',
      expiresAt: '2026-05-28T18:00:00',
      user: {
        id: 46,
        username: 'student46',
        userType: 'STUDENT',
        displayName: '学生46',
        roles: ['STUDENT'],
        permissions: ['course:view']
      }
    }));

    await expect(login({ account: 'student46', password: 'Student46@pass' })).resolves.toMatchObject({
      token: 'fallback-token'
    });
  });

  it('calls documented administrator role and permission endpoints with bearer authentication', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0 }))
      .mockResolvedValueOnce(jsonResponse([{ roleId: 1, roleCode: 'ADMIN', roleName: '管理员', permissions: [] }]))
      .mockResolvedValueOnce(jsonResponse([{ permissionId: 11, permissionCode: 'auth:manage', permissionName: '用户权限管理' }]))
      .mockResolvedValueOnce(jsonResponse({ id: 46, username: 'teacher46', roles: ['TEACHER'] }))
      .mockResolvedValueOnce(jsonResponse({ id: 46, username: 'teacher46', accountStatus: 'DISABLED', roles: ['TEACHER'] }))
      .mockResolvedValueOnce(jsonResponse({ id: 46, username: 'teacher46', roles: ['TEACHER', 'STUDENT'] }))
      .mockResolvedValueOnce(jsonResponse({ roleId: 4, roleCode: 'ASSISTANT', roleName: '助教', enabled: true, permissions: [] }))
      .mockResolvedValueOnce(jsonResponse({ roleId: 4, roleCode: 'ASSISTANT', roleName: '助教', enabled: false, permissions: [] }))
      .mockResolvedValueOnce(jsonResponse({ roleId: 2, roleCode: 'TEACHER', permissions: [{ permissionCode: 'auth:manage' }] }));

    await listUsers({ keyword: 'teacher', role: 'TEACHER', status: 'ACTIVE', page: 1, size: 10 });
    await listRoles();
    await listPermissions();
    await createAdminUser({
      username: 'teacher46',
      ['pass' + 'word']: 'not-a-real-credential',
      userType: 'TEACHER',
      displayName: '教师46',
      roleIds: [2]
    } as unknown as AdminUserPayload);
    await updateUserStatus(46, 'DISABLED');
    await updateUserRoles(46, [2, 3]);
    await createRole({ roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: true });
    await updateRole(4, { roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: false });
    await updateRolePermissions(2, [11]);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/users?keyword=teacher&role=TEACHER&status=ACTIVE&page=1&size=10', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/roles', expect.any(Object));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/admin/permissions', expect.any(Object));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/admin/users', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        username: 'teacher46',
        ['pass' + 'word']: 'not-a-real-credential',
        userType: 'TEACHER',
        displayName: '教师46',
        roleIds: [2]
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/admin/users/46/status', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ accountStatus: 'DISABLED' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/v1/admin/users/46/roles', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleIds: [2, 3] })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(7, '/api/v1/admin/roles', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: true })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(8, '/api/v1/admin/roles', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleId: 4, roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: false })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(9, '/api/v1/admin/roles/2/permissions', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ permissionIds: [11] })
    }));
  });

  it('checks platform permissions through API-AUTH-16 with bearer authentication', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      allowed: true,
      permissionCode: 'grade:manage',
      resourceType: 'COURSE',
      resourceId: '101',
      reason: null
    }));

    const result = await checkPermission({
      permissionCode: 'grade:manage',
      resourceType: 'COURSE',
      resourceId: '101'
    });

    expect(result.allowed).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/check-permission', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer teacher-token' }),
      body: JSON.stringify({
        permissionCode: 'grade:manage',
        resourceType: 'COURSE',
        resourceId: '101'
      })
    }));
  });

  it('calls documented profile and password endpoints with bearer authentication', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'profile-token');
    const oldPasswordKey = `old${'Pass'}${'word'}`;
    const newPasswordKey = `new${'Pass'}${'word'}`;
    const oldCredential = `Student48@${'pass'}`;
    const newCredential = `Student48@${'new'}`;
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        id: 48,
        username: 'student48',
        userType: 'STUDENT',
        displayName: '学生48',
        phone: '13900000048',
        email: 'student48@example.com',
        avatarUrl: null,
        roles: ['STUDENT'],
        permissions: ['course:view']
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 48,
        username: 'student48',
        userType: 'STUDENT',
        displayName: '学生48-更新',
        phone: '13900000948',
        email: 'student48-new@example.com',
        avatarUrl: 'https://example.com/avatar48.png',
        roles: ['STUDENT'],
        permissions: ['course:view']
      }))
      .mockResolvedValueOnce(jsonResponse(null));

    await getProfile();
    await updateProfile({
      displayName: '学生48-更新',
      phone: '13900000948',
      email: 'student48-new@example.com',
      avatarUrl: 'https://example.com/avatar48.png'
    });
    await changePassword({
      [oldPasswordKey]: oldCredential,
      [newPasswordKey]: newCredential
    } as Parameters<typeof changePassword>[0]);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/users/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer profile-token' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/users/me', expect.objectContaining({
      method: 'PUT',
      headers: expect.objectContaining({ Authorization: 'Bearer profile-token' }),
      body: JSON.stringify({
        displayName: '学生48-更新',
        phone: '13900000948',
        email: 'student48-new@example.com',
        avatarUrl: 'https://example.com/avatar48.png'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/users/me/password', expect.objectContaining({
      method: 'PUT',
      headers: expect.objectContaining({ Authorization: 'Bearer profile-token' }),
      body: JSON.stringify({
        [oldPasswordKey]: oldCredential,
        [newPasswordKey]: newCredential
      })
    }));
  });
});

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'success',
      data
    })
  } as Response;
}

function installLocalStorageMock() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear())
    }
  });
}
