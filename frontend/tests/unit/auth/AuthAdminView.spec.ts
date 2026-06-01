import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AuthAdminView from '../../../src/views/auth/AuthAdminView.vue';

describe('AuthAdminView', () => {
  beforeEach(() => {
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'admin-token');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('renders admin user role and role permission management states backed by AUTH APIs', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        records: [{
          id: 46,
          username: 'teacher46',
          userType: 'TEACHER',
          displayName: '教师46',
          accountStatus: 'ACTIVE',
          roles: ['TEACHER'],
          permissions: ['course:manage']
        }],
        total: 1
      }))
      .mockResolvedValueOnce(jsonResponse([
        { roleId: 1, roleCode: 'STUDENT', roleName: '学生', enabled: true, permissions: [] },
        { roleId: 2, roleCode: 'TEACHER', roleName: '教师', enabled: true, permissions: [{ permissionId: 12, permissionCode: 'course:manage', permissionName: '管理课程' }] },
        { roleId: 3, roleCode: 'ADMIN', roleName: '管理员', enabled: true, permissions: [{ permissionId: 11, permissionCode: 'auth:manage', permissionName: '用户权限管理' }] }
      ]))
      .mockResolvedValueOnce(jsonResponse([
        { permissionId: 11, permissionCode: 'auth:manage', permissionName: '用户权限管理', moduleCode: 'AUTH' },
        { permissionId: 12, permissionCode: 'course:manage', permissionName: '管理课程', moduleCode: 'CRS' }
      ]))
      .mockResolvedValueOnce(jsonResponse({
        records: [{
          logId: 50,
          operatorId: 1,
          operationType: 'LOGIN_FAILURE',
          targetType: 'AUTH_USER',
          targetId: 'teacher46',
          resultStatus: 'FAILURE',
          failureReason: '账号或密码错误',
          clientIp: '203.0.113.50',
          userAgent: 'AuditTest/50',
          createdAt: '2026-06-01T12:00:00'
        }],
        total: 1
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 46,
        username: 'teacher46',
        userType: 'TEACHER',
        displayName: '教师46',
        roles: ['STUDENT', 'TEACHER'],
        permissions: ['course:manage']
      }))
      .mockResolvedValueOnce(jsonResponse({
        roleId: 4,
        roleCode: 'ASSISTANT',
        roleName: '助教',
        description: '课程助教',
        enabled: true,
        permissions: []
      }))
      .mockResolvedValueOnce(jsonResponse({
        roleId: 4,
        roleCode: 'ASSISTANT',
        roleName: '助教',
        description: '课程助教',
        enabled: false,
        permissions: []
      }))
      .mockResolvedValueOnce(jsonResponse({
          roleId: 2,
          roleCode: 'TEACHER',
          roleName: '教师',
          enabled: true,
          permissions: [{ permissionId: 11, permissionCode: 'auth:manage', permissionName: '用户权限管理' }]
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 47,
        username: 'newteacher46',
        userType: 'TEACHER',
        displayName: '新教师46',
        accountStatus: 'ACTIVE',
        roles: ['TEACHER'],
        permissions: ['course:manage']
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 46,
        username: 'teacher46',
        userType: 'TEACHER',
        displayName: '教师46',
        accountStatus: 'DISABLED',
        roles: ['STUDENT', 'TEACHER'],
        permissions: ['course:manage']
      }))
      .mockResolvedValueOnce(jsonResponse({
        records: [{
          logId: 51,
          operatorId: 1,
          operationType: 'LOGIN_SUCCESS',
          targetType: 'AUTH_USER',
          targetId: 'admin',
          resultStatus: 'SUCCESS',
          failureReason: null,
          clientIp: '203.0.113.51',
          userAgent: 'AuditTest/51',
          createdAt: '2026-06-01T13:00:00'
        }],
        total: 1
      }));

    const wrapper = mount(AuthAdminView);
    await flushPromises();

    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.text()).toContain('角色管理');
    expect(wrapper.text()).toContain('权限分配');
    expect(wrapper.text()).toContain('用户角色分配');
    expect(wrapper.text()).toContain('UI-AUTH-09');
    expect(wrapper.text()).toContain('LOGIN_FAILURE');
    expect(wrapper.text()).toContain('203.0.113.50');
    expect(wrapper.text()).toContain('teacher46');
    expect(wrapper.text()).toContain('auth:manage');

    await wrapper.find('[data-user-role="46-1"]').setValue(true);
    await wrapper.find('[data-save-user-roles="46"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('用户角色已更新');

    await wrapper.find('input[name="roleCode"]').setValue('ASSISTANT');
    await wrapper.find('input[name="roleName"]').setValue('助教');
    await wrapper.find('input[name="description"]').setValue('课程助教');
    await wrapper.find('[data-create-role-form]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('角色已创建');

    await wrapper.find('[data-role-enabled="4"]').setValue(false);
    await wrapper.find('[data-save-role-form="4"]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('角色已更新');

    await wrapper.find('[data-role-permission="2-11"]').setValue(true);
    await wrapper.find('[data-save-role-permissions="2"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('角色权限已更新');

    await wrapper.find('input[name="username"]').setValue('newteacher46');
    await wrapper.find('input[name="password"]').setValue('Teacher46@pass');
    await wrapper.find('input[name="displayName"]').setValue('新教师46');
    await wrapper.find('select[name="userType"]').setValue('TEACHER');
    await wrapper.find('[data-create-user-form]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('用户已创建');
    expect(wrapper.text()).toContain('newteacher46');

    await wrapper.find('[data-toggle-user-status="46"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('账号状态已更新');
    expect(wrapper.text()).toContain('DISABLED');

    await wrapper.find('input[name="operatorId"]').setValue('1');
    await wrapper.find('input[name="operationType"]').setValue('LOGIN_SUCCESS');
    await wrapper.find('select[name="resultStatus"]').setValue('SUCCESS');
    await wrapper.find('input[name="startTime"]').setValue('2026-06-01T00:00');
    await wrapper.find('input[name="endTime"]').setValue('2026-06-01T23:59');
    await wrapper.find('[data-audit-filter-form]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('LOGIN_SUCCESS');
    expect(wrapper.text()).toContain('203.0.113.51');

    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/admin/audit-logs?page=1&size=20', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/admin/users/46/roles', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleIds: [1, 2] })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/v1/admin/roles', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: true })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(7, '/api/v1/admin/roles', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleId: 4, roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: false })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(8, '/api/v1/admin/roles/2/permissions', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ permissionIds: [11, 12] })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(9, '/api/v1/admin/users', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        username: 'newteacher46',
        password: 'Teacher46@pass',
        userType: 'TEACHER',
        displayName: '新教师46',
        phone: '',
        email: '',
        roleIds: [2]
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(10, '/api/v1/admin/users/46/status', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ accountStatus: 'DISABLED' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(11, '/api/v1/admin/audit-logs?operatorId=1&operationType=LOGIN_SUCCESS&resultStatus=SUCCESS&startTime=2026-06-01T00%3A00&endTime=2026-06-01T23%3A59&page=1&size=20', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' })
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
