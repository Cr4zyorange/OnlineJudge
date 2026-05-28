import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AuthView from '../../../src/views/auth/AuthView.vue';

describe('AuthView', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('logs in and shows the role landing entry with success feedback', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      token: 'token-45',
      expiresAt: '2026-05-28T18:00:00',
      user: {
        id: 45,
        username: 'teacher45',
        userType: 'TEACHER',
        displayName: '教师45',
        roles: ['TEACHER'],
        permissions: ['course:manage']
      }
    }));
    const wrapper = mount(AuthView);

    await wrapper.find('input[name="account"]').setValue('teacher45');
    await wrapper.find('input[name="password"]').setValue('Teacher45@pass');
    await wrapper.find('form[data-auth-form="login"]').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('登录成功');
    expect(wrapper.text()).toContain('教师工作台');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBe('token-45');
  });

  it('switches to register mode and renders backend validation failures', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      json: async () => ({ code: 'AUTH_409', message: '账号已存在', data: null })
    } as Response);
    const wrapper = mount(AuthView);

    await wrapper.find('button[data-auth-mode="register"]').trigger('click');
    await wrapper.find('input[name="username"]').setValue('student45');
    await wrapper.find('input[name="displayName"]').setValue('学生45');
    await wrapper.find('input[name="registerPassword"]').setValue('Student45@pass');
    await wrapper.find('form[data-auth-form="register"]').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('账号已存在');
    expect(wrapper.text()).toContain('创建平台账号');
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
