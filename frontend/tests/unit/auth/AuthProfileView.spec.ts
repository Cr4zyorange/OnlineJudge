import { flushPromises, mount } from '@vue/test-utils';
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AuthProfileView from '../../../src/views/auth/AuthProfileView.vue';

describe('AuthProfileView', () => {
  beforeEach(() => {
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'profile-token');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('loads current profile, updates contact information, and changes password', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        id: 48,
        username: 'student48',
        userType: 'STUDENT',
        displayName: '学生48',
        phone: '13900000048',
        email: 'student48@example.com',
        avatarUrl: '',
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

    const wrapper = mount(AuthProfileView);
    await flushPromises();

    expect(wrapper.text()).toContain('student48');
    expect((wrapper.find('input[name="displayName"]').element as HTMLInputElement).value).toBe('学生48');

    await wrapper.find('input[name="displayName"]').setValue('学生48-更新');
    await wrapper.find('input[name="phone"]').setValue('13900000948');
    await wrapper.find('input[name="email"]').setValue('student48-new@example.com');
    await wrapper.find('input[name="avatarUrl"]').setValue('https://example.com/avatar48.png');
    await wrapper.find('form[data-profile-form="profile"]').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('个人资料已更新');

    await wrapper.find('input[name="oldPassword"]').setValue('Student48@pass');
    await wrapper.find('input[name="newPassword"]').setValue('Student48@new');
    await wrapper.find('input[name="confirmPassword"]').setValue('Student48@new');
    await wrapper.find('form[data-profile-form="password"]').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('密码已修改');
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/users/me', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        displayName: '学生48-更新',
        phone: '13900000948',
        email: 'student48-new@example.com',
        avatarUrl: 'https://example.com/avatar48.png'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/users/me/password', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        oldPassword: 'Student48@pass',
        newPassword: 'Student48@new'
      })
    }));
  });

  it('blocks mismatched password confirmation before calling the password api', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      id: 49,
      username: 'student49',
      userType: 'STUDENT',
      displayName: '学生49',
      roles: ['STUDENT'],
      permissions: ['course:view']
    }));

    const wrapper = mount(AuthProfileView);
    await flushPromises();

    await wrapper.find('input[name="oldPassword"]').setValue('Student49@pass');
    await wrapper.find('input[name="newPassword"]').setValue('Student49@new');
    await wrapper.find('input[name="confirmPassword"]').setValue('different');
    await wrapper.find('form[data-profile-form="password"]').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('两次输入的新密码不一致');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('presents the account security page without development markers and uses the shared glass style', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      id: 50,
      username: 'student50',
      userType: 'STUDENT',
      displayName: '学生50',
      roles: ['STUDENT'],
      permissions: ['course:view']
    }));

    const wrapper = mount(AuthProfileView);
    await flushPromises();

    expect(wrapper.text()).not.toContain('AUTH-04');

    const source = readFileSync('src/views/auth/AuthProfileView.vue', 'utf8');
    expect(source).toContain('background: rgba(255, 255, 255, 0.15);');
    expect(source).not.toContain('background: rgba(255, 255, 255, 0.72);');
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
