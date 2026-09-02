import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routerKey, type Router } from 'vue-router';
import LabEditorView from '../../../src/views/lab/LabEditorView.vue';
import * as labApi from '../../../src/api/lab/labs';
import * as courseApi from '../../../src/api/crs/courses';
import type { CourseResource } from '../../../src/types/crs';
import type { LabExperimentDetail } from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/crs/courses');

const replace = vi.fn();

describe('LabEditorView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    replace.mockResolvedValue(undefined);
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-08-19T00:00:00+08:00').getTime());
    vi.mocked(courseApi.listChapters).mockResolvedValue([{
      id: 8,
      courseId: 101,
      parentId: null,
      chapterName: '自动评测基础',
      sortOrder: 1,
      visibleStatus: 1,
      chapterType: 1,
      children: [],
      createdAt: '2026-08-01T08:00:00',
      updatedAt: '2026-08-01T08:00:00'
    }]);
    vi.mocked(courseApi.listResources).mockResolvedValue([resource()]);
  });

  it('creates a draft through structured sections and selects attachments by resource name', async () => {
    vi.mocked(labApi.createLab).mockResolvedValue(detail({ id: 9, title: '容器评测实验' }));
    vi.mocked(labApi.updateLab).mockResolvedValue(detail({ id: 9, title: '容器评测实验' }));
    const wrapper = mountEditor({ courseId: 101 });
    await flushPromises();

    expect(wrapper.text()).toContain('基础信息');
    expect(wrapper.text()).toContain('内容与附件');
    expect(wrapper.text()).toContain('测试用例');
    expect(wrapper.text()).toContain('发布检查');
    expect(wrapper.text()).toContain('实验说明.pdf');
    expect(wrapper.text()).not.toContain('附件占位');
    expect(wrapper.text()).not.toContain('DOCKER_IO');

    await wrapper.get('[name="title"]').setValue('容器评测实验');
    await wrapper.get('[name="description"]').setValue('完成容器输入输出评测。');
    await wrapper.get('[name="chapterId"]').setValue('8');
    await wrapper.get('[name="deadline"]').setValue('2026-08-25T23:59');
    await wrapper.get('[name="maxScore"]').setValue('100');
    await wrapper.get('[name="evaluationMode"]').setValue('MIXED');
    await wrapper.get('[data-testid="language-python"]').setValue(true);
    await wrapper.get('[data-testid="resource-10"]').setValue(true);
    await wrapper.get('[name="testcase-input-0"]').setValue('1 2');
    await wrapper.get('[name="testcase-output-0"]').setValue('3');
    await wrapper.get('[data-testid="lab-editor-form"]').trigger('submit');
    await flushPromises();

    expect(labApi.createLab).toHaveBeenCalledWith(101, expect.objectContaining({
      chapterId: 8,
      title: '容器评测实验',
      description: '完成容器输入输出评测。',
      deadline: new Date('2026-08-25T23:59').toISOString(),
      attachmentIds: [10],
      allowedLanguages: 'python',
      evaluationMode: 'MIXED',
      testcases: [expect.objectContaining({ input: '1 2', expectedOutput: '3', scoreWeight: 100 })]
    }));
    expect(wrapper.get('[role="status"]').text()).toContain('草稿已保存');

    expect(replace).toHaveBeenCalledWith({
      name: 'lab-edit',
      params: { courseId: 101, labId: 9 }
    });

    await wrapper.get('[data-testid="lab-editor-form"]').trigger('submit');
    await flushPromises();

    expect(labApi.createLab).toHaveBeenCalledTimes(1);
    expect(labApi.updateLab).toHaveBeenCalledWith(9, expect.objectContaining({
      title: '容器评测实验'
    }));
  });

  it('loads and updates an existing draft without exposing raw resource identifiers', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValue(detail());
    vi.mocked(labApi.updateLab).mockResolvedValue(detail({ title: '更新后的实验' }));
    const wrapper = mountEditor({ courseId: 101, labId: 7 });
    await flushPromises();

    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('原实验');
    expect((wrapper.get('[data-testid="resource-10"]').element as HTMLInputElement).checked).toBe(true);
    expect(wrapper.text()).toContain('实验说明.pdf');
    expect(wrapper.text()).not.toContain('资源 #10');

    await wrapper.get('[name="title"]').setValue('更新后的实验');
    await wrapper.get('[data-testid="lab-editor-form"]').trigger('submit');
    await flushPromises();

    expect(labApi.updateLab).toHaveBeenCalledWith(7, expect.objectContaining({
      title: '更新后的实验',
      attachmentIds: [10]
    }));
    expect(wrapper.get('[role="status"]').text()).toContain('草稿已更新');
  });

  it('keeps invalid input visible and blocks the request with an actionable summary', async () => {
    const wrapper = mountEditor({ courseId: 101 });
    await flushPromises();
    await wrapper.get('[name="title"]').setValue('未完成实验');
    await wrapper.get('[name="deadline"]').setValue('2026-08-18T10:00');
    await wrapper.get('[data-testid="lab-editor-form"]').trigger('submit');
    await flushPromises();

    expect(labApi.createLab).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('实验说明不能为空');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('截止时间必须晚于当前时间');
    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('未完成实验');
  });

  it('uploads a real course resource and selects it for the lab', async () => {
    vi.mocked(courseApi.uploadResource).mockResolvedValue(resource({
      id: 11,
      name: '实验数据.zip',
      originalFilename: 'data.zip'
    }));
    const wrapper = mountEditor({ courseId: 101 });
    await flushPromises();

    const file = new File(['zip'], 'data.zip', { type: 'application/zip' });
    const input = wrapper.get('[name="attachmentUpload"]');
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] });
    await input.trigger('change');
    await wrapper.get('[data-testid="upload-resource"]').trigger('click');
    await flushPromises();

    expect(courseApi.uploadResource).toHaveBeenCalledWith(101, expect.objectContaining({
      name: 'data.zip',
      resourceType: 'ARCHIVE',
      visibility: 'STUDENT'
    }), file);
    expect(wrapper.text()).toContain('实验数据.zip');
    expect((wrapper.get('[data-testid="resource-11"]').element as HTMLInputElement).checked).toBe(true);
  });

  it('blocks editing a non-draft experiment and provides a route back to teacher detail', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValue(detail({ status: 'PUBLISHED' }));
    const wrapper = mountEditor({ courseId: 101, labId: 7 });
    await flushPromises();

    expect(wrapper.get('[data-state="forbidden"]').text()).toContain('只有草稿实验可以编辑');
    expect(wrapper.find('[data-testid="lab-editor-form"]').exists()).toBe(false);
    expect(wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))).toContainEqual({
      name: 'lab-manage-detail',
      params: { courseId: 101, labId: 7 }
    });
  });
});

function mountEditor(props: { courseId: number; labId?: number }) {
  return mount(LabEditorView, {
    props,
    global: {
      stubs: { RouterLink: RouterLinkStub },
      provide: {
        [routerKey as symbol]: { replace } as unknown as Router
      }
    }
  });
}

function resource(overrides: Partial<CourseResource> = {}): CourseResource {
  return {
    id: 10,
    courseId: 101,
    chapterId: 8,
    name: '实验说明.pdf',
    resourceType: 'DOCUMENT',
    visibility: 'STUDENT',
    publishAt: null,
    originalFilename: 'lab-guide.pdf',
    contentType: 'application/pdf',
    fileSize: 2048,
    uploadUserId: 9,
    downloadUrl: '/resources/10/download',
    createdAt: '2026-08-01T08:00:00',
    updatedAt: '2026-08-01T08:00:00',
    ...overrides
  };
}

function detail(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 8,
    title: '原实验',
    description: '完成自动评测。',
    status: 'DRAFT',
    deadline: '2026-08-25T23:59:00',
    maxScore: 100,
    attachmentIds: [10],
    allowedLanguages: 'python,java',
    evaluationMode: 'MIXED',
    autoEvaluate: true,
    reportRequired: true,
    timeLimitMs: 60000,
    memoryLimitKb: 262144,
    testcases: [{
      id: 21,
      labId: 7,
      input: '1 2',
      expectedOutput: '3',
      scoreWeight: 100,
      public: true,
      timeLimitMs: 1000,
      memoryLimitKb: 65536,
      orderNum: 1
    }],
    publishedAt: null,
    deleted: false,
    ...overrides
  };
}
