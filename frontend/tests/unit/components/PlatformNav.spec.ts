import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, describe, expect, it } from 'vitest';
import PlatformNav from '../../../src/components/foundation/PlatformNav.vue';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';

describe('PlatformNav', () => {
  afterEach(() => {
    resetRuntimeContext();
  });

  it('derives the AUTH administration entry from the current runtime user roles', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }]
    });
    await router.push('/courses');
    await router.isReady();
    currentUser.value = user('ADMIN');

    const wrapper = mount(PlatformNav, { global: { plugins: [router] } });

    expect(wrapper.get('[data-testid="platform-nav-admin"]').attributes('href')).toBe('/admin/auth');

    currentUser.value = user('STUDENT');
    await nextTick();

    expect(wrapper.find('[data-testid="platform-nav-admin"]').exists()).toBe(false);
  });
});

function user(role: 'ADMIN' | 'STUDENT') {
  return {
    id: role === 'ADMIN' ? 1 : 2,
    username: role.toLowerCase(),
    userType: role,
    displayName: role === 'ADMIN' ? '平台管理员' : '学生用户',
    roles: [role],
    permissions: []
  };
}
