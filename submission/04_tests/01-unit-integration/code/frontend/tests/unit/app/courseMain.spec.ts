import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('course-main entry', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('mounts the shared app router for course URLs', async () => {
    const mount = vi.fn();
    const use = vi.fn();
    const createApp = vi.fn(() => ({ mount, use }));
    const router = { name: 'OnlineJudgeRouter' };
    const createAppRouter = vi.fn(() => router);
    const appShell = { name: 'OnlineJudgeAppShell' };
    vi.doMock('vue', () => ({ createApp }));
    vi.doMock('../../../src/app/App.vue', () => ({ default: appShell }));
    vi.doMock('../../../src/app/router', () => ({ createAppRouter }));
    vi.doMock('../../../src/app/authContext', () => ({
      configureDefaultAuthContext: vi.fn()
    }));

    await import('../../../src/app/course-main');

    expect(createApp).toHaveBeenCalledWith(appShell);
    expect(createAppRouter).toHaveBeenCalledOnce();
    expect(use).toHaveBeenCalledWith(router);
    expect(mount).toHaveBeenCalledWith('#app');
  });
});
