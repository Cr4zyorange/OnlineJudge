import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PlatformNavigationBar from '../../../src/components/PlatformNavigationBar.vue';
import { BACKGROUND_STORAGE_KEY } from '../../../src/backgroundOptions';
import { logout } from '../../../src/api/auth/auth';

vi.mock('../../../src/api/auth/auth', () => ({
  logout: vi.fn()
}));

describe('PlatformNavigationBar', () => {
  beforeEach(() => {
    installLocalStorageMock();
    vi.mocked(logout).mockReset();
    window.localStorage.setItem('onlinejudge.username', 'Teacher101');
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    document.documentElement.style.removeProperty('--oj-bg-image');
    document.body.classList.remove('oj-live-background');
    document.body.classList.remove('oj-video-background');
    document.querySelectorAll('.background-picker__menu').forEach((menu) => menu.remove());
    window.history.pushState({}, '', '/courses');
  });

  it('lets users pick a background image from the fixed image set', async () => {
    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });

    expect(wrapper.get('[data-testid="platform-nav-courses"]').classes()).toContain('active');
    expect(wrapper.get('[data-testid="platform-nav-courses"]').text()).toBe('课程中心');
    expect(wrapper.get('[data-testid="platform-nav-learning"]').text()).toBe('学习任务');

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');

    const options = Array.from(document.querySelectorAll('[data-testid^="background-option-"]'));
    expect(options.length).toBeGreaterThanOrEqual(2);
    expect(document.querySelector('[data-testid="background-option-2"]')?.textContent?.trim()).toBe('');
    expect(document.querySelector('[data-testid="background-option-live-aurora"]')?.textContent?.trim()).not.toBe('');
    expect(document.querySelector('[data-testid="background-option-live-aurora"] video')).not.toBeNull();
    clickDocumentOption('background-option-2');
    await wrapper.vm.$nextTick();

    expect(window.localStorage.setItem).toHaveBeenCalledWith(BACKGROUND_STORAGE_KEY, '2');
    expect(document.documentElement.style.getPropertyValue('--oj-bg-image')).toContain('url("');
    expect(document.body.classList.contains('oj-live-background')).toBe(false);
    expect(document.body.classList.contains('oj-video-background')).toBe(false);
    expect(document.querySelector('[data-testid="background-option-2"]')).toBeNull();
  });

  it('applies and clears the live video background mode when users switch themes', async () => {
    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');
    clickDocumentOption('background-option-live-aurora');
    await wrapper.vm.$nextTick();

    expect(window.localStorage.setItem).toHaveBeenCalledWith(BACKGROUND_STORAGE_KEY, 'live-aurora');
    expect(document.documentElement.style.getPropertyValue('--oj-bg-image')).toBe('none');
    expect(document.body.classList.contains('oj-video-background')).toBe(true);
    expect(wrapper.find('[data-testid="live-background-video"]').exists()).toBe(true);

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');
    clickDocumentOption('background-option-1');
    await wrapper.vm.$nextTick();

    expect(window.localStorage.setItem).toHaveBeenCalledWith(BACKGROUND_STORAGE_KEY, '1');
    expect(document.body.classList.contains('oj-live-background')).toBe(false);
    expect(document.body.classList.contains('oj-video-background')).toBe(false);
    expect(wrapper.find('[data-testid="live-background-video"]').exists()).toBe(false);
  });

  it('renders navigation when browser storage is unavailable', () => {
    installUnavailableLocalStorage();

    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });

    expect(wrapper.find('[data-testid="platform-navigation"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="platform-nav-profile"]').text()).toBe('T');
  });

  it('logs out from the global navigation once, clears the route, and returns to login', async () => {
    let resolveLogout: () => void = () => {};
    vi.mocked(logout).mockReturnValue(new Promise<void>((resolve) => {
      resolveLogout = resolve;
    }));
    const navigationListener = vi.fn();
    window.addEventListener('onlinejudge:navigation', navigationListener);

    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/courses'
      }
    });

    await wrapper.get('[data-testid="platform-nav-logout"]').trigger('click');
    await wrapper.get('[data-testid="platform-nav-logout"]').trigger('click');

    expect(logout).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="platform-nav-logout"]').attributes('disabled')).toBeDefined();

    resolveLogout();
    await flushPromises();

    expect(window.location.pathname).toBe('/login');
    expect(navigationListener).toHaveBeenCalledTimes(1);
    window.removeEventListener('onlinejudge:navigation', navigationListener);
  });

  it('renders the theme menu as a dropdown grid', async () => {
    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');

    expect(document.querySelector('.background-picker__menu')).not.toBeNull();
    expect(document.querySelectorAll('[data-testid^="background-option-"]').length).toBeGreaterThan(1);
  });

  it('positions the theme menu below the final navbar bottom during entrance animation', async () => {
    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });
    const toggle = wrapper.get('[data-testid="background-picker-toggle"]').element as HTMLElement;
    const nav = wrapper.get('[data-testid="platform-navigation"]').element as HTMLElement;

    vi.spyOn(toggle, 'getBoundingClientRect').mockReturnValue({
      bottom: 24,
      height: 34,
      left: 240,
      right: 320,
      top: -10,
      width: 80,
      x: 240,
      y: -10,
      toJSON: () => ({})
    });
    vi.spyOn(nav, 'getBoundingClientRect').mockReturnValue({
      bottom: 30,
      height: 72,
      left: 0,
      right: 1024,
      top: -42,
      width: 1024,
      x: 0,
      y: -42,
      toJSON: () => ({})
    });
    Object.defineProperty(nav, 'offsetTop', { configurable: true, value: 0 });
    Object.defineProperty(nav, 'offsetHeight', { configurable: true, value: 72 });

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');

    expect((document.querySelector('.background-picker__menu') as HTMLElement)?.style.top).toBe('82px');
  });

  it('keeps video previews on metadata preload to avoid eager theme asset downloads', async () => {
    const wrapper = mount(PlatformNavigationBar, {
      props: {
        currentPath: '/'
      }
    });

    await wrapper.get('[data-testid="background-picker-toggle"]').trigger('click');

    const previewVideos = Array.from(
      document.querySelectorAll<HTMLVideoElement>('.background-picker__preview-video')
    );
    expect(previewVideos.length).toBeGreaterThan(0);
    expect(previewVideos.every((video) => video.getAttribute('preload') === 'metadata')).toBe(true);
  });
});

function clickDocumentOption(testId: string) {
  const option = document.querySelector(`[data-testid="${testId}"]`);
  if (!(option instanceof HTMLElement)) {
    throw new Error(`Missing option ${testId}`);
  }
  option.click();
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

function installUnavailableLocalStorage() {
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    get() {
      throw new Error('localStorage is unavailable');
    }
  });
}

async function flushPromises() {
  for (let tick = 0; tick < 4; tick += 1) {
    await Promise.resolve();
  }
}
