import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabStudentView from '../../../src/views/lab/LabStudentView.vue';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';

vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/lrn/learningProgress');

describe('LabStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/courses/101/labs/7?role=student');
    vi.mocked(learningProgressApi.saveLearningProgress).mockResolvedValue({
      progressId: 1,
      courseId: 101,
      courseName: '软件工程基础',
      chapterId: null,
      chapterName: null,
      sourceModule: 'LAB',
      sourceId: 7,
      progressPercent: 10,
      lastPosition: 'labId=7',
      status: 'IN_PROGRESS',
      continueUrl: '/courses/101/labs/7?role=student',
      updatedAt: '2026-06-01 10:00:00'
    });
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
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 88,
        labId: 7,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 92,
        finalScore: 95,
        version: 2,
        submittedAt: '2026-06-01T09:30:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
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
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('查看提交历史');
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain('ACCEPTED');
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      progressPercent: 10
    }));

    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('hello lab')");
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.submitLab).toHaveBeenCalledWith(7, expect.objectContaining({
      language: 'python',
      code: "print('hello lab')"
    }));
    expect(learningProgressApi.saveLearningProgress).toHaveBeenLastCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      progressPercent: 100,
      lastPosition: expect.stringContaining('submittedVersion=1')
    }));
    expect(wrapper.text()).toContain('提交成功');
    expect(wrapper.text()).toContain('版本 1');
    expect(wrapper.text()).toContain('PENDING');
  });

  it('restores lab draft code from the resume query parameter', async () => {
    window.history.replaceState({}, '', `/courses/101/labs/7?role=student&resume=${encodeURIComponent("code=print('resume')")}`);
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      chapterId: null,
      title: '实验七',
      description: '断点恢复',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: false,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 7
      }
    });
    await flushPromises();

    expect((wrapper.get('[name="code"]').element as HTMLTextAreaElement).value).toBe("print('resume')");
    expect(wrapper.text()).toContain('已恢复上次断点');
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
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

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

  it('blocks unsupported upload files before calling the submit api', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 10,
      courseId: 101,
      chapterId: null,
      title: '实验十',
      description: '文件格式校验',
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
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 10
      }
    });
    await flushPromises();

    const invalidFile = new File(['plain text'], 'notes.txt', { type: 'text/plain' });
    const fileInput = wrapper.get('[name="file"]');
    Object.defineProperty(fileInput.element, 'files', {
      value: [invalidFile],
      configurable: true
    });

    await fileInput.trigger('change');
    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(labApi.submitLab).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('仅支持');
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
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([]);
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

  it('shows a history loading failure without breaking the detail page', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 11,
      courseId: 101,
      chapterId: null,
      title: '实验十一',
      description: '历史记录加载失败',
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
    vi.mocked(labApi.listLabSubmissions).mockRejectedValueOnce(new Error('提交历史加载失败'));

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 11
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('实验十一');
    expect(wrapper.text()).toContain('提交历史加载失败');
  });
});

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}
