import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabTeacherView from '../../../src/views/lab/LabTeacherView.vue';
import * as labApi from '../../../src/api/lab/labs';

vi.mock('../../../src/api/lab/labs');

describe('LabTeacherView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('creates a draft lab and refreshes the visible teacher list', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([]);
    vi.mocked(labApi.createLab).mockResolvedValueOnce({
      id: 1,
      courseId: 101,
      chapterId: null,
      title: '实验一',
      description: '实现链表操作',
      status: 'DRAFT',
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
      testcases: [
        {
          id: 11,
          labId: 1,
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
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 1,
        courseId: 101,
        title: '实验一',
        status: 'DRAFT',
        deadline: '2026-06-30T23:59:59',
        maxScore: 100,
        evaluationMode: 'DOCKER_IO',
        autoEvaluate: true,
        reportRequired: false,
        deleted: false
      }
    ]);

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无实验');

    await wrapper.get('[name="title"]').setValue('实验一');
    await wrapper.get('[name="description"]').setValue('实现链表操作');
    await wrapper.get('[name="deadline"]').setValue('2026-06-30T23:59');
    await wrapper.get('[name="maxScore"]').setValue('100');
    await wrapper.get('[name="allowedLanguages"]').setValue('java,python');
    await wrapper.get('[name="testcase-input-0"]').setValue('1 2');
    await wrapper.get('[name="testcase-output-0"]').setValue('3');
    await wrapper.get('[name="testcase-weight-0"]').setValue('100');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.createLab).toHaveBeenCalledWith(101, expect.objectContaining({
      title: '实验一',
      description: '实现链表操作',
      maxScore: 100,
      allowedLanguages: 'java,python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      testcases: [
        expect.objectContaining({
          input: '1 2',
          expectedOutput: '3',
          scoreWeight: 100
        })
      ]
    }));
    expect(wrapper.text()).toContain('保存成功');
    expect(wrapper.text()).toContain('实验一');
    expect(wrapper.text()).toContain('DRAFT');
  });

  it('keeps invalid form data on the page and blocks create requests', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([]);

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[name="deadline"]').setValue('2020-01-01T00:00');
    await wrapper.get('[name="maxScore"]').setValue('0');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.createLab).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('实验名称不能为空');
    expect(wrapper.text()).toContain('截止时间必须晚于当前时间');
    expect(wrapper.text()).toContain('满分必须大于 0');
  });

  it('updates publishes closes and deletes draft labs through teacher actions', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 7,
        courseId: 101,
        title: '实验二',
        status: 'DRAFT',
        deadline: '2026-06-20T23:59:59',
        maxScore: 100,
        evaluationMode: 'DOCKER_IO',
        autoEvaluate: true,
        reportRequired: false,
        deleted: false
      }
    ]);
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      chapterId: null,
      title: '实验二',
      description: '初版',
      status: 'DRAFT',
      deadline: '2026-06-20T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'java',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.updateLab).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      chapterId: null,
      title: '实验二-修订',
      description: '更新后的说明',
      status: 'DRAFT',
      deadline: '2026-06-25T23:59:59',
      maxScore: 120,
      attachmentIds: [],
      allowedLanguages: 'java,cpp',
      evaluationMode: 'MIXED',
      autoEvaluate: false,
      reportRequired: true,
      timeLimitMs: 90000,
      memoryLimitKb: 524288,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 7,
        courseId: 101,
        title: '实验二-修订',
        status: 'DRAFT',
        deadline: '2026-06-25T23:59:59',
        maxScore: 120,
        evaluationMode: 'MIXED',
        autoEvaluate: false,
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.publishLab).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      title: '实验二-修订',
      status: 'PUBLISHED',
      deadline: '2026-06-25T23:59:59',
      maxScore: 120,
      evaluationMode: 'MIXED',
      autoEvaluate: false,
      reportRequired: true,
      deleted: false
    });
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 7,
        courseId: 101,
        title: '实验二-修订',
        status: 'PUBLISHED',
        deadline: '2026-06-25T23:59:59',
        maxScore: 120,
        evaluationMode: 'MIXED',
        autoEvaluate: false,
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.closeLab).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      title: '实验二-修订',
      status: 'CLOSED',
      deadline: '2026-06-25T23:59:59',
      maxScore: 120,
      evaluationMode: 'MIXED',
      autoEvaluate: false,
      reportRequired: true,
      deleted: false
    });
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 7,
        courseId: 101,
        title: '实验二-修订',
        status: 'CLOSED',
        deadline: '2026-06-25T23:59:59',
        maxScore: 120,
        evaluationMode: 'MIXED',
        autoEvaluate: false,
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 9,
        courseId: 101,
        title: '草稿实验',
        status: 'DRAFT',
        deadline: '2026-07-01T23:59:59',
        maxScore: 100,
        evaluationMode: 'DOCKER_IO',
        autoEvaluate: true,
        reportRequired: false,
        deleted: false
      }
    ]);
    vi.mocked(labApi.deleteLab).mockResolvedValueOnce({
      id: 9,
      courseId: 101,
      title: '草稿实验',
      status: 'DRAFT',
      deadline: '2026-07-01T23:59:59',
      maxScore: 100,
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      deleted: true
    });
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([]);

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '编辑')?.trigger('click');
    await flushPromises();
    await wrapper.get('[name="title"]').setValue('实验二-修订');
    await wrapper.get('[name="description"]').setValue('更新后的说明');
    await wrapper.get('[name="deadline"]').setValue('2026-06-25T23:59');
    await wrapper.get('[name="maxScore"]').setValue('120');
    await wrapper.get('[name="allowedLanguages"]').setValue('java,cpp');
    await wrapper.get('[name="evaluationMode"]').setValue('MIXED');
    await wrapper.get('[name="autoEvaluate"]').setValue(false);
    await wrapper.get('[name="reportRequired"]').setValue(true);
    await wrapper.get('[name="timeLimitMs"]').setValue('90000');
    await wrapper.get('[name="memoryLimitKb"]').setValue('524288');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.updateLab).toHaveBeenCalledWith(7, expect.objectContaining({
      title: '实验二-修订',
      evaluationMode: 'MIXED',
      autoEvaluate: false,
      reportRequired: true
    }));
    expect(wrapper.text()).toContain('更新成功');

    await wrapper.findAll('button').find((button) => button.text() === '发布')?.trigger('click');
    await flushPromises();
    expect(labApi.publishLab).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('发布成功');

    await wrapper.findAll('button').find((button) => button.text() === '截止')?.trigger('click');
    await flushPromises();
    expect(labApi.closeLab).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('截止成功');

    wrapper.unmount();

    const draftWrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();
    await draftWrapper.findAll('button').find((button) => button.text() === '删除草稿')?.trigger('click');
    await flushPromises();
    expect(labApi.deleteLab).toHaveBeenCalledWith(9);
    expect(draftWrapper.text()).toContain('草稿已删除');
  });

  it('filters teacher-facing submission history and opens a detail panel', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 12,
        courseId: 101,
        title: '实验十二',
        status: 'PUBLISHED',
        deadline: '2026-06-25T23:59:59',
        maxScore: 100,
        evaluationMode: 'DOCKER_IO',
        autoEvaluate: true,
        reportRequired: false,
        deleted: false
      }
    ]);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([
        {
          submissionId: 301,
          labId: 12,
          studentId: 602,
          language: 'python',
          submitStatus: 'LATE',
          evaluationStatus: 'ACCEPTED',
          autoScore: 88,
          finalScore: 90,
          version: 2,
          submittedAt: '2026-06-26T00:10:00',
          isLatest: true,
          isFinal: true,
          isScoringBasis: true,
          hasFile: true
        }
      ])
      .mockResolvedValueOnce([
        {
          submissionId: 301,
          labId: 12,
          studentId: 602,
          language: 'python',
          submitStatus: 'LATE',
          evaluationStatus: 'ACCEPTED',
          autoScore: 88,
          finalScore: 90,
          version: 2,
          submittedAt: '2026-06-26T00:10:00',
          isLatest: true,
          isFinal: true,
          isScoringBasis: true,
          hasFile: true
        }
      ]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
      submissionId: 301,
      labId: 12,
      studentId: 602,
      language: 'python',
      submitStatus: 'LATE',
      evaluationStatus: 'ACCEPTED',
      autoScore: 88,
      finalScore: 90,
      version: 2,
      submittedAt: '2026-06-26T00:10:00',
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: true,
      code: "print('teacher detail')",
      fileId: 'file-301'
    });

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '查看提交')?.trigger('click');
    await flushPromises();

    await wrapper.get('[name="studentId"]').setValue('602');
    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[name="evaluationStatus"]').setValue('ACCEPTED');
    await wrapper.get('[name="overdue"]').setValue('true');
    await wrapper.get('[data-action="search-submissions"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(12, {
      studentId: 602,
      submitStatus: 'LATE',
      evaluationStatus: 'ACCEPTED',
      overdue: true
    });
    expect(wrapper.text()).toContain('602');
    expect(wrapper.text()).toContain('LATE');

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(12, 301);
    expect(wrapper.text()).toContain("print('teacher detail')");
    expect(wrapper.text()).toContain('file-301');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
