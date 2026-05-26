import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabStudentView from '../../../src/views/lab/LabStudentView.vue';
import * as labApi from '../../../src/api/lab/labs';

vi.mock('../../../src/api/lab/labs');

describe('LabStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads published lab detail and submits code successfully', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      chapterId: null,
      title: '实验七',
      description: '完成基础排序实现',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [11, 12],
      allowedLanguages: 'java,python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: [
        {
          id: 1,
          labId: 7,
          input: '1 2',
          expectedOutput: '3',
          scoreWeight: 100,
          public: true,
          timeLimitMs: 1000,
          memoryLimitKb: 65536,
          orderNum: 1
        }
      ]
    });
    vi.mocked(labApi.submitLab).mockResolvedValueOnce({
      submissionId: 99,
      labId: 7,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'PENDING',
      version: 1,
      submittedAt: '2026-06-01T10:00:00'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 7
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('实验七');
    expect(wrapper.text()).toContain('完成基础排序实现');
    expect(wrapper.text()).toContain('java,python');

    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('hello lab')");
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.submitLab).toHaveBeenCalledWith(7, expect.objectContaining({
      language: 'python',
      code: "print('hello lab')"
    }));
    expect(wrapper.text()).toContain('提交成功');
    expect(wrapper.text()).toContain('版本 1');
    expect(wrapper.text()).toContain('PENDING');
  });

  it('shows frontend validation errors before calling the submit api', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 8,
      courseId: 101,
      chapterId: null,
      title: '实验八',
      description: '提交校验',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'java,python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 8
      }
    });
    await flushPromises();

    await wrapper.get('[name="language"]').setValue('');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.submitLab).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请选择编程语言');
    expect(wrapper.text()).toContain('请填写代码或上传文件');
  });

  it('surfaces backend submission errors on the page', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 9,
      courseId: 101,
      chapterId: null,
      title: '实验九',
      description: '异常提示',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'java,python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.submitLab).mockRejectedValueOnce(new Error('实验已截止，当前不允许提交'));

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 9
      }
    });
    await flushPromises();

    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('late')");
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('实验已截止，当前不允许提交');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
