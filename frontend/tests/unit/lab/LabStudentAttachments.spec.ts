import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as crsApi from '../../../src/api/crs/courses';
import type { CourseResource } from '../../../src/types/crs';
import LabStudentAttachments from '../../../src/views/lab/LabStudentAttachments.vue';

vi.mock('../../../src/api/crs/courses');

describe('LabStudentAttachments', () => {
  const createObjectUrl = vi.fn(() => 'blob:lab-attachment');
  const revokeObjectUrl = vi.fn();
  let downloadedFilename = '';
  let downloadedHref = '';

  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-08-18T09:00:00+08:00'));
    downloadedFilename = '';
    downloadedHref = '';
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      value: createObjectUrl
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function clickDownload(this: HTMLAnchorElement) {
      downloadedFilename = this.download;
      downloadedHref = this.href;
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows the empty state without requesting the whole course resource list', async () => {
    const wrapper = mountAttachments({ attachmentIds: [] });
    await flushPromises();

    expect(wrapper.get('[data-testid="lab-attachment-empty"]').text()).toContain('无附件');
    expect(crsApi.listResources).not.toHaveBeenCalled();
  });

  it('renders the ordered intersection of configured ids and current published student resources', async () => {
    vi.mocked(crsApi.listResources).mockResolvedValue([
      resource({ id: 11, name: '实验指导书', originalFilename: 'guide.pdf', fileSize: 1536 }),
      resource({ id: 12, name: '输入数据', originalFilename: 'very-long-input-data-name.zip', resourceType: 'ARCHIVE' }),
      resource({ id: 13, courseId: 202, name: '其他课程资料' }),
      resource({ id: 14, visibility: 'TEACHER', name: '教师答案' }),
      resource({ id: 15, publishAt: '2026-08-19T09:00:00', name: '尚未发布' }),
      resource({ id: 16, publishAt: 'invalid-date', name: '发布时间异常' }),
      resource({ id: 99, name: '未被实验引用' })
    ]);

    const wrapper = mountAttachments({ attachmentIds: [12, 11, 13, 14, 15, 16] });
    await flushPromises();

    expect(crsApi.listResources).toHaveBeenCalledWith(101);
    expect(wrapper.findAll('[data-testid="lab-attachment-name"]').map((item) => item.text()))
      .toEqual(['输入数据', '实验指导书']);
    expect(wrapper.text()).toContain('very-long-input-data-name.zip');
    expect(wrapper.text()).toContain('压缩包');
    expect(wrapper.text()).toContain('1.5 KB');
    expect(wrapper.get('[data-testid="lab-attachment-partial"]').text())
      .toBe('部分附件已失效或暂不可访问（4 个），仅展示当前可下载项。');
    expect(wrapper.text()).not.toContain('其他课程资料');
    expect(wrapper.text()).not.toContain('教师答案');
    expect(wrapper.text()).not.toContain('尚未发布');
    expect(wrapper.text()).not.toContain('发布时间异常');
    expect(wrapper.text()).not.toContain('未被实验引用');
    expect(wrapper.html()).not.toContain('/api/v1/courses/101/resources/11/download');
  });

  it('keeps surrounding experiment content available when metadata fails and retries locally', async () => {
    vi.mocked(crsApi.listResources)
      .mockRejectedValueOnce(new Error('资源服务暂时不可用'))
      .mockResolvedValueOnce([resource({ id: 11, name: '重试成功资料' })]);
    const Host = defineComponent({
      components: { LabStudentAttachments },
      template: `
        <main>
          <h1>实验正文仍然可用</h1>
          <LabStudentAttachments :course-id="101" :lab-id="7" :attachment-ids="[11]" />
          <a href="/submit">进入提交</a>
        </main>
      `
    });

    const wrapper = mount(Host);
    await flushPromises();

    expect(wrapper.text()).toContain('实验正文仍然可用');
    expect(wrapper.text()).toContain('进入提交');
    expect(wrapper.get('[data-testid="lab-attachment-error"]').attributes('role')).toBe('alert');
    const retry = wrapper.get('[data-testid="lab-attachment-retry"]');
    expect(retry.element.tagName).toBe('BUTTON');

    await retry.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('重试成功资料');
    expect(wrapper.find('[data-testid="lab-attachment-error"]').exists()).toBe(false);
  });

  it('keeps the newest overlapping metadata retry when responses arrive out of order', async () => {
    const olderRetry = deferred<CourseResource[]>();
    const newerRetry = deferred<CourseResource[]>();
    vi.mocked(crsApi.listResources)
      .mockRejectedValueOnce(new Error('首次加载失败'))
      .mockReturnValueOnce(olderRetry.promise)
      .mockReturnValueOnce(newerRetry.promise);
    const wrapper = mountAttachments({ attachmentIds: [11] });
    await flushPromises();

    const retry = wrapper.get('[data-testid="lab-attachment-retry"]').element;
    retry.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    retry.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(crsApi.listResources).toHaveBeenCalledTimes(3);

    newerRetry.resolve([resource({ id: 11, name: '最新附件' })]);
    await flushPromises();
    expect(wrapper.text()).toContain('最新附件');

    olderRetry.resolve([resource({ id: 11, name: '过期附件' })]);
    await flushPromises();
    expect(wrapper.text()).toContain('最新附件');
    expect(wrapper.text()).not.toContain('过期附件');
  });

  it('ignores metadata from the previous course and lab after route reuse', async () => {
    const oldResources = deferred<CourseResource[]>();
    vi.mocked(crsApi.listResources)
      .mockReturnValueOnce(oldResources.promise)
      .mockResolvedValueOnce([resource({ id: 22, courseId: 202, name: '新实验附件' })]);
    const wrapper = mountAttachments({ attachmentIds: [11] });

    await wrapper.setProps({ courseId: 202, labId: 8, attachmentIds: [22] });
    await flushPromises();
    expect(wrapper.text()).toContain('新实验附件');

    oldResources.resolve([resource({ id: 11, name: '旧实验附件' })]);
    await flushPromises();
    expect(wrapper.text()).toContain('新实验附件');
    expect(wrapper.text()).not.toContain('旧实验附件');
  });

  it('downloads through the authenticated CRS API with the response filename and Blob URL', async () => {
    const blob = new Blob(['guide'], { type: 'application/pdf' });
    vi.mocked(crsApi.listResources).mockResolvedValue([
      resource({ id: 11, name: '实验指导书', originalFilename: 'fallback.pdf' })
    ]);
    vi.mocked(crsApi.downloadResource).mockResolvedValue({ blob, filename: 'header-name.pdf' });
    const wrapper = mountAttachments({ attachmentIds: [11] });
    await flushPromises();

    const button = wrapper.get('[data-testid="lab-attachment-download"]');
    expect(button.element.tagName).toBe('BUTTON');
    expect(button.attributes('type')).toBe('button');
    await button.trigger('click');
    await flushPromises();

    expect(crsApi.downloadResource).toHaveBeenCalledWith(101, 11);
    expect(createObjectUrl).toHaveBeenCalledWith(blob);
    expect(downloadedFilename).toBe('header-name.pdf');
    expect(downloadedHref).toContain('blob:lab-attachment');
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:lab-attachment');
  });

  it('gives every download button a resource-specific accessible name while downloading', async () => {
    const pendingDownload = deferred<{ blob: Blob; filename?: string }>();
    vi.mocked(crsApi.listResources).mockResolvedValue([
      resource({ id: 11, name: '实验指导书' }),
      resource({ id: 12, name: '输入数据包' })
    ]);
    vi.mocked(crsApi.downloadResource).mockReturnValue(pendingDownload.promise);
    const wrapper = mountAttachments({ attachmentIds: [11, 12] });
    await flushPromises();

    const buttons = wrapper.findAll('[data-testid="lab-attachment-download"]');
    expect(buttons.map((button) => button.attributes('aria-label'))).toEqual([
      '下载附件：实验指导书',
      '下载附件：输入数据包'
    ]);

    await buttons[0]!.trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-testid="lab-attachment-download"]')[0]?.attributes('aria-label'))
      .toBe('下载附件：实验指导书');

    pendingDownload.resolve({ blob: new Blob(['guide']), filename: 'guide.pdf' });
    await flushPromises();
  });

  it('deduplicates downloads, keeps the row after failure, and allows a retry', async () => {
    const pendingDownload = deferred<{ blob: Blob; filename?: string }>();
    vi.mocked(crsApi.listResources).mockResolvedValue([resource({ id: 11, name: '可重试附件' })]);
    vi.mocked(crsApi.downloadResource)
      .mockReturnValueOnce(pendingDownload.promise)
      .mockResolvedValueOnce({ blob: new Blob(['ok']), filename: 'retry.pdf' });
    const wrapper = mountAttachments({ attachmentIds: [11] });
    await flushPromises();

    const button = wrapper.get('[data-testid="lab-attachment-download"]').element;
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(crsApi.downloadResource).toHaveBeenCalledTimes(1);
    await flushPromises();
    expect(wrapper.get('[data-testid="lab-attachment-download"]').attributes('disabled')).toBeDefined();

    pendingDownload.reject(new Error('下载服务暂时不可用'));
    await flushPromises();
    expect(wrapper.text()).toContain('可重试附件');
    expect(wrapper.get('[data-testid="lab-attachment-download-error"]').text())
      .toContain('下载服务暂时不可用');

    await wrapper.get('[data-testid="lab-attachment-download"]').trigger('click');
    await flushPromises();
    expect(crsApi.downloadResource).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="lab-attachment-download-error"]').exists()).toBe(false);
  });

  it('drops a late download response after the route changes', async () => {
    const oldDownload = deferred<{ blob: Blob; filename?: string }>();
    vi.mocked(crsApi.listResources)
      .mockResolvedValueOnce([resource({ id: 11, name: '旧附件' })])
      .mockResolvedValueOnce([resource({ id: 22, courseId: 202, name: '新附件' })]);
    vi.mocked(crsApi.downloadResource).mockReturnValueOnce(oldDownload.promise);
    const wrapper = mountAttachments({ attachmentIds: [11] });
    await flushPromises();

    await wrapper.get('[data-testid="lab-attachment-download"]').trigger('click');
    await wrapper.setProps({ courseId: 202, labId: 8, attachmentIds: [22] });
    await flushPromises();
    expect(wrapper.text()).toContain('新附件');

    oldDownload.resolve({ blob: new Blob(['old']), filename: 'old.pdf' });
    await flushPromises();
    expect(createObjectUrl).not.toHaveBeenCalled();
    expect(downloadedFilename).toBe('');
    expect(wrapper.text()).not.toContain('旧附件');
  });
});

function mountAttachments(overrides: Partial<{
  courseId: number;
  labId: number;
  attachmentIds: number[];
}> = {}) {
  return mount(LabStudentAttachments, {
    props: {
      courseId: 101,
      labId: 7,
      attachmentIds: [11],
      ...overrides
    }
  });
}

function resource(overrides: Partial<CourseResource> = {}): CourseResource {
  const id = overrides.id ?? 11;
  const courseId = overrides.courseId ?? 101;
  return {
    id,
    courseId,
    chapterId: 3,
    name: '实验附件',
    resourceType: 'COURSEWARE',
    visibility: 'STUDENT',
    publishAt: '2026-08-18T08:00:00',
    originalFilename: 'attachment.pdf',
    contentType: 'application/pdf',
    fileSize: 2048,
    uploadUserId: 501,
    downloadUrl: `/api/v1/courses/${courseId}/resources/${id}/download`,
    createdAt: '2026-08-18T08:00:00',
    updatedAt: '2026-08-18T08:00:00',
    ...overrides
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
