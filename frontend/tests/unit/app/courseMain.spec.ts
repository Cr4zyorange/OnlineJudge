import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('course-main entry', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('mounts the shared app router for course URLs', async () => {
    const mount = vi.fn();
    const createApp = vi.fn(() => ({ mount }));
    const appShell = { name: 'OnlineJudgeAppShell' };
    const courseShell = { name: 'CourseManagementOnlyShell' };
    vi.doMock('vue', () => ({ createApp }));
    vi.doMock('../../../src/app/App.vue', () => ({ default: appShell }));
    vi.doMock('../../../src/views/crs/CourseManagementView.vue', () => ({ default: courseShell }));
    vi.doMock('../../../src/app/authContext', () => ({
      configureDefaultAuthContext: vi.fn()
    }));

    await import('../../../src/app/course-main');

    expect(createApp).toHaveBeenCalledWith(appShell);
    expect(mount).toHaveBeenCalledWith('#app');
  });
});
