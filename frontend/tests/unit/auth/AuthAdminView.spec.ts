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
      }));

    const wrapper = mount(AuthAdminView);
    await flushPromises();

    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.text()).toContain('角色管理');
    expect(wrapper.text()).toContain('权限分配');
    expect(wrapper.text()).toContain('用户角色分配');
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

    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/admin/users/46/roles', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleIds: [1, 2] })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/admin/roles', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: true })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/v1/admin/roles/4', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ roleCode: 'ASSISTANT', roleName: '助教', description: '课程助教', enabled: false })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(7, '/api/v1/admin/roles/2/permissions', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ permissionIds: [11, 12] })
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
