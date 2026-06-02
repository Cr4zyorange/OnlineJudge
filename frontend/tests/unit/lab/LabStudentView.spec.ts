import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabStudentView from '../../../src/views/lab/LabStudentView.vue';
import * as labApi from '../../../src/api/lab/labs';

vi.mock('../../../src/api/lab/labs');

describe('LabStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads published lab detail and submits code successfully', async () => {
    vi.useFakeTimers();
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
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 88,
      evaluationStatus: 'ACCEPTED',
      score: 92,
      passedCases: 1,
      totalCases: 1,
      message: '全部用例通过',
      caseResults: [
        {
          testcaseId: 1,
          orderNum: 1,
          passed: true,
          score: 92,
          input: '1 2',
          expectedOutput: '3',
          actualOutput: '3',
          message: '通过'
        }
      ],
      submittedAt: '2026-06-01T09:30:00',
      finishedAt: '2026-06-01T09:31:00'
    });
    vi.mocked(labApi.submitLab).mockResolvedValueOnce({
      submissionId: 99,
      labId: 7,
      studentId: 601,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'PENDING',
      autoScore: null,
      version: 1,
      submittedAt: '2026-06-01T10:00:00'
    });
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 99,
      evaluationStatus: 'RUNNING',
      score: 0,
      passedCases: 0,
      totalCases: 1,
      message: '评测进行中',
      caseResults: [],
      submittedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:00'
    });
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 99,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 1,
      totalCases: 1,
      message: '全部用例通过',
      caseResults: [
        {
          testcaseId: 1,
          orderNum: 1,
          passed: true,
          score: 100,
          input: '1 2',
          expectedOutput: '3',
          actualOutput: '3',
          message: '通过'
        }
      ],
      submittedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:05'
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
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 88);
    expect(wrapper.text()).toContain('查看提交历史');
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain('ACCEPTED');
    expect(wrapper.text()).toContain('全部用例通过');
    expect(wrapper.text()).toContain('92');

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
    expect(wrapper.text()).toContain('RUNNING');
    expect(wrapper.text()).toContain('评测进行中');

    await vi.advanceTimersByTimeAsync(1000);
    await flushPromises();

    expect(wrapper.text()).toContain('100');
    expect(wrapper.text()).toContain('通过用例：1 / 1');
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 99);
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

  it('shows evaluation failure details for the latest submission', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 12,
      courseId: 101,
      chapterId: null,
      title: '实验十二',
      description: '查看失败详情',
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
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 120,
        labId: 12,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'WRONG_ANSWER',
        autoScore: 50,
        finalScore: null,
        version: 1,
        submittedAt: '2026-06-01T11:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 120,
      evaluationStatus: 'WRONG_ANSWER',
      score: 50,
      passedCases: 1,
      totalCases: 2,
      message: '部分用例未通过',
      caseResults: [
        {
          testcaseId: 1,
          orderNum: 1,
          passed: true,
          score: 50,
          input: 'a',
          expectedOutput: 'A',
          actualOutput: 'A',
          message: '通过'
        },
        {
          testcaseId: 2,
          orderNum: 2,
          passed: false,
          score: 0,
          input: 'b',
          expectedOutput: 'B',
          actualOutput: 'C',
          message: '期望输出 B，实际输出 C'
        }
      ],
      submittedAt: '2026-06-01T11:00:00',
      finishedAt: '2026-06-01T11:00:03'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 12
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('WRONG_ANSWER');
    expect(wrapper.text()).toContain('部分用例未通过');
    expect(wrapper.text()).toContain('通过用例：1 / 2');
    expect(wrapper.text()).toContain('期望输出 B，实际输出 C');
  });
});

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}
