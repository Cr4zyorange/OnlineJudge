import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabStudentView from '../../../src/views/lab/LabStudentView.vue';
import * as crsApi from '../../../src/api/crs/courses';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import * as learningRecordsApi from '../../../src/api/lrn/learningRecords';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';

const downloadLabReportMock = vi.hoisted(() => vi.fn());

vi.mock('../../../src/api/lab/labs', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../src/api/lab/labs')>();
  const mockedEntries = Object.fromEntries(Object.keys(actual).map((key) => [key, vi.fn()]));
  return {
    ...mockedEntries,
    downloadLabReport: downloadLabReportMock
  };
});
vi.mock('../../../src/api/crs/courses');
vi.mock('../../../src/api/lrn/learningProgress');
vi.mock('../../../src/api/lrn/learningRecords');

describe('LabStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-06-01T08:00:00+08:00'));
    installLocalStorageMock();
    window.history.replaceState({}, '', '/courses/101/labs/7?role=student');
    resetRuntimeContext();
    currentUser.value = {
      id: 601,
      username: 'lab-student',
      displayName: '实验学生',
      userType: 'STUDENT',
      roles: ['STUDENT'],
      permissions: []
    };
    vi.mocked(crsApi.listResources).mockResolvedValue([]);
    vi.mocked(learningRecordsApi.reportLearningRecord).mockResolvedValue({
      id: 1,
      courseId: 101,
      courseName: '软件工程基础',
      sourceModule: 'LAB',
      sourceId: 7,
      actionType: 'ACCESS',
      durationSeconds: 0,
      startedAt: '2026-06-01 10:00:00',
      endedAt: '2026-06-01 10:00:00'
    });
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

  afterEach(() => {
    vi.useRealTimers();
    resetRuntimeContext();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
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
    expect(wrapper.text()).toContain('Java、Python');
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7);
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 88);
    expect(wrapper.text()).toContain('查看提交历史');
    expect(wrapper.text()).toContain('版本 2');
    expect(wrapper.text()).toContain('评测状态：通过');
    expect(wrapper.text()).toContain('全部用例通过');
    expect(wrapper.text()).toContain('92');
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      progressPercent: 10
    }));
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      actionType: 'ACCESS'
    }));

    await wrapper.get('[name="language"]').setValue('python');
    await wrapper.get('[name="code"]').setValue("print('hello lab')");
    await wrapper.get('[name="code"]').trigger('blur');
    await flushPromises();
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
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      actionType: 'STUDY',
      durationSeconds: expect.any(Number)
    }));
    expect(learningRecordsApi.reportLearningRecord).toHaveBeenLastCalledWith(expect.objectContaining({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 7,
      actionType: 'SUBMIT'
    }));
    expect(wrapper.text()).toContain('提交成功');
    expect(wrapper.text()).toContain('版本 1');
    expect(wrapper.text()).toContain('评测中');
    expect(wrapper.text()).toContain('评测进行中');

    await vi.advanceTimersByTimeAsync(1000);
    await flushPromises();

    expect(wrapper.text()).toContain('100');
    expect(wrapper.text()).toContain('通过用例：1 / 1');
    expect(labApi.getLabSubmissionResult).toHaveBeenCalledWith(7, 99);
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
    expect(learningProgressApi.saveLearningProgress).not.toHaveBeenCalledWith(expect.objectContaining({
      sourceModule: 'LAB',
      sourceId: 7,
      lastPosition: 'labId=7'
    }));
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

    expect(wrapper.text()).toContain('答案错误');
    expect(wrapper.text()).toContain('部分用例未通过');
    expect(wrapper.text()).toContain('通过用例：1 / 2');
    expect(wrapper.text()).toContain('期望输出 B，实际输出 C');
  });

  it('shows the report upload panel and uploads a report successfully', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 13,
      courseId: 101,
      chapterId: null,
      title: '实验十三',
      description: '报告上传',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: true,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 130,
        labId: 13,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 100,
        finalScore: 100,
        version: 1,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 130,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 1,
      totalCases: 1,
      message: '全部用例通过',
      caseResults: [],
      submittedAt: '2026-06-01T12:00:00',
      finishedAt: '2026-06-01T12:00:03'
    });
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
      submissionId: 130,
      labId: 13,
      studentId: 601,
      language: 'python',
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      autoScore: 100,
      finalScore: 100,
      version: 1,
      submittedAt: '2026-06-01T12:00:00',
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: false,
      code: "print('report upload')",
      sourceFile: null,
      latestReport: null
    });
    vi.mocked(labApi.uploadLabReport).mockResolvedValueOnce({
      reportId: 801,
      submissionId: 130,
      fileName: 'report-v1.pdf',
      fileType: 'PDF',
      fileSize: 2048,
      version: 1,
      score: null,
      comment: null,
      submittedAt: '2026-06-01T12:10:00',
      downloadUrl: '/api/v1/labs/13/reports/801/download'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 13
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('实验报告');
    expect(wrapper.text()).toContain('暂无实验报告');

    const reportFile = new File(['report'], 'report-v1.pdf', { type: 'application/pdf' });
    const reportInput = wrapper.get('[name="reportFile"]');
    Object.defineProperty(reportInput.element, 'files', {
      value: [reportFile],
      configurable: true
    });

    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    await flushPromises();

    expect(labApi.uploadLabReport).toHaveBeenCalledWith(13, {
      submissionId: 130,
      reportFile
    });
    expect(wrapper.text()).toContain('实验报告上传成功');
    expect(wrapper.text()).toContain('最新报告版本：1');
    expect(wrapper.text()).toContain('report-v1.pdf');
  });

  it('downloads the latest report through the lab download action', async () => {
    const reportBlob = new Blob(['report content'], { type: 'application/pdf' });
    downloadLabReportMock.mockResolvedValueOnce({
      blob: reportBlob,
      filename: 'report-v1.pdf'
    });
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:report-v1'),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 13,
      courseId: 101,
      chapterId: null,
      title: '实验十三',
      description: '报告下载',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: true,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 130,
        labId: 13,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 100,
        finalScore: 100,
        version: 1,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 130,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 1,
      totalCases: 1,
      message: '全部用例通过',
      caseResults: [],
      submittedAt: '2026-06-01T12:00:00',
      finishedAt: '2026-06-01T12:00:03'
    });
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
      submissionId: 130,
      labId: 13,
      studentId: 601,
      language: 'python',
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      autoScore: 100,
      finalScore: 100,
      version: 1,
      submittedAt: '2026-06-01T12:00:00',
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: false,
      code: "print('report upload')",
      sourceFile: null,
      latestReport: null
    });
    vi.mocked(labApi.uploadLabReport).mockResolvedValueOnce({
      reportId: 801,
      submissionId: 130,
      fileName: 'report-v1.pdf',
      fileType: 'PDF',
      fileSize: 2048,
      version: 1,
      score: null,
      comment: null,
      submittedAt: '2026-06-01T12:10:00',
      downloadUrl: '/api/v1/labs/13/reports/801/download'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 13
      }
    });
    await flushPromises();

    const reportFile = new File(['report'], 'report-v1.pdf', { type: 'application/pdf' });
    const reportInput = wrapper.get('[name="reportFile"]');
    Object.defineProperty(reportInput.element, 'files', {
      value: [reportFile],
      configurable: true
    });
    await reportInput.trigger('change');
    await wrapper.get('.lab-student__report-form').trigger('submit');
    await flushPromises();

    const downloadButton = wrapper.findAll('button').find((button) => button.text().includes('下载最新报告'));
    expect(downloadButton).toBeTruthy();

    await downloadButton!.trigger('click');
    await flushPromises();

    expect(downloadLabReportMock).toHaveBeenCalledWith(13, 801);
    expect(URL.createObjectURL).toHaveBeenCalledWith(reportBlob);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report-v1');
  });

  it('shows the latest teacher score and feedback beside evaluation results', async () => {
    window.localStorage.setItem('onlinejudge.userId', '601');
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 14,
      courseId: 101,
      chapterId: null,
      title: '实验十四',
      description: '评分结果展示',
      status: 'SCORE_PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: true,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 140,
        labId: 14,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 88,
        finalScore: 95,
        version: 3,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 140,
      evaluationStatus: 'ACCEPTED',
      score: 88,
      passedCases: 2,
      totalCases: 2,
      message: '全部用例通过',
      caseResults: [],
      submittedAt: '2026-06-01T12:00:00',
      finishedAt: '2026-06-01T12:00:03'
    });
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
      submissionId: 140,
      labId: 14,
      studentId: 601,
      language: 'python',
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      autoScore: 88,
      finalScore: 95,
      version: 3,
      submittedAt: '2026-06-01T12:00:00',
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: false,
      code: "print('graded')",
      sourceFile: null,
      latestReport: {
        reportId: 814,
        submissionId: 140,
        fileName: 'report-v3.pdf',
        fileType: 'PDF',
        fileSize: 2048,
        version: 3,
        score: 30,
        comment: '报告结构完整',
        submittedAt: '2026-06-01T12:10:00',
        downloadUrl: '/api/v1/labs/14/reports/814/download'
      },
      latestScore: {
        submissionId: 140,
        reportId: 814,
        autoScore: 88,
        reportScore: 30,
        manualScore: 92,
        finalScore: 95,
        comment: '整体实现稳定',
        hasChangeLogs: true,
        scoredAt: '2026-06-01T13:00:00',
        updatedAt: '2026-06-01T13:20:00'
      }
    });
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce({
      labId: 14,
      studentId: 601,
      status: 'SCORE_PUBLISHED',
      submission: {
        submissionId: 140,
        labId: 14,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 88,
        finalScore: 95,
        version: 3,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false,
        code: "print('graded')",
        sourceFile: null,
        latestReport: {
          reportId: 814,
          submissionId: 140,
          fileName: 'report-v3.pdf',
          fileType: 'PDF',
          fileSize: 2048,
          version: 3,
          score: 30,
          comment: '报告结构完整',
          submittedAt: '2026-06-01T12:10:00',
          downloadUrl: '/api/v1/labs/14/reports/814/download'
        },
        latestScore: {
          submissionId: 140,
          reportId: 814,
          autoScore: 88,
          reportScore: 30,
          manualScore: 92,
          finalScore: 95,
          comment: '整体实现稳定',
          hasChangeLogs: true,
          scoredAt: '2026-06-01T13:00:00',
          updatedAt: '2026-06-01T13:20:00'
        }
      },
      evaluationResult: {
        submissionId: 140,
        evaluationStatus: 'ACCEPTED',
        score: 88,
        passedCases: 2,
        totalCases: 2,
        message: '全部用例通过',
        caseResults: [],
        submittedAt: '2026-06-01T12:00:00',
        finishedAt: '2026-06-01T12:00:03'
      },
      latestReport: {
        reportId: 814,
        submissionId: 140,
        fileName: 'report-v3.pdf',
        fileType: 'PDF',
        fileSize: 2048,
        version: 3,
        score: 30,
        comment: '报告结构完整',
        submittedAt: '2026-06-01T12:10:00',
        downloadUrl: '/api/v1/labs/14/reports/814/download'
      },
      latestScore: {
        submissionId: 140,
        reportId: 814,
        autoScore: 88,
        reportScore: 30,
        manualScore: 92,
        finalScore: 95,
        comment: '整体实现稳定',
        hasChangeLogs: true,
        scoredAt: '2026-06-01T13:00:00',
        updatedAt: '2026-06-01T13:20:00'
      },
      publishedAt: '2026-06-01T13:30:00'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 14
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('最终得分：95');
    expect(wrapper.text()).toContain('人工评分：92');
    expect(wrapper.text()).toContain('报告评分：30');
    expect(wrapper.text()).toContain('教师评语：整体实现稳定');
    expect(wrapper.text()).toContain('评分已更新');
    expect(wrapper.text()).toContain('报告评语：报告结构完整');
  });

  it('hides unpublished teacher scoring details until lab scores are released', async () => {
    window.localStorage.setItem('onlinejudge.userId', '601');
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 15,
      courseId: 101,
      chapterId: null,
      title: '实验十五',
      description: '成绩发布前隐藏教师反馈',
      status: 'PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: true,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 150,
        labId: 15,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 100,
        finalScore: 95,
        version: 1,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 150,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 1,
      totalCases: 1,
      message: '全部用例通过',
      caseResults: [],
      submittedAt: '2026-06-01T12:00:00',
      finishedAt: '2026-06-01T12:00:03'
    });
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce({
      labId: 15,
      studentId: 601,
      status: 'PUBLISHED',
      submission: {
        submissionId: 150,
        labId: 15,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 100,
        finalScore: null,
        version: 1,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false,
        code: "print('hidden score')",
        sourceFile: null,
        latestReport: {
          reportId: 915,
          submissionId: 150,
          fileName: 'report-v1.pdf',
          fileType: 'PDF',
          fileSize: 2048,
          version: 1,
          score: null,
          comment: null,
          submittedAt: '2026-06-01T12:10:00',
          downloadUrl: '/api/v1/labs/15/reports/915/download'
        },
        latestScore: null
      },
      evaluationResult: {
        submissionId: 150,
        evaluationStatus: 'ACCEPTED',
        score: 100,
        passedCases: 1,
        totalCases: 1,
        message: '全部用例通过',
        caseResults: [],
        submittedAt: '2026-06-01T12:00:00',
        finishedAt: '2026-06-01T12:00:03'
      },
      latestReport: {
        reportId: 915,
        submissionId: 150,
        fileName: 'report-v1.pdf',
        fileType: 'PDF',
        fileSize: 2048,
        version: 1,
        score: null,
        comment: null,
        submittedAt: '2026-06-01T12:10:00',
        downloadUrl: '/api/v1/labs/15/reports/915/download'
      },
      latestScore: null,
      publishedAt: null
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 15
      }
    });
    await flushPromises();

    expect(labApi.getLabResult).toHaveBeenCalledWith(15, 601);
    expect(wrapper.text()).toContain('自动得分：100');
    expect(wrapper.text()).not.toContain('最终得分：95');
    expect(wrapper.text()).not.toContain('教师评语：');
    expect(wrapper.text()).not.toContain('报告评分：');
  });

  it('shows published teacher scoring details from the lab result api', async () => {
    window.localStorage.setItem('onlinejudge.userId', '601');
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({
      id: 16,
      courseId: 101,
      chapterId: null,
      title: '实验十六',
      description: '成绩发布后展示教师反馈',
      status: 'SCORE_PUBLISHED',
      deadline: '2026-06-30T23:59:59',
      maxScore: 100,
      attachmentIds: [],
      allowedLanguages: 'python',
      evaluationMode: 'DOCKER_IO',
      autoEvaluate: true,
      reportRequired: true,
      timeLimitMs: 60000,
      memoryLimitKb: 262144,
      deleted: false,
      testcases: []
    });
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 160,
        labId: 16,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 88,
        finalScore: 95,
        version: 2,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }
    ]);
    vi.mocked(labApi.getLabSubmissionResult).mockResolvedValueOnce({
      submissionId: 160,
      evaluationStatus: 'ACCEPTED',
      score: 88,
      passedCases: 2,
      totalCases: 2,
      message: '全部用例通过',
      caseResults: [],
      submittedAt: '2026-06-01T12:00:00',
      finishedAt: '2026-06-01T12:00:03'
    });
    vi.mocked(labApi.getLabResult).mockResolvedValueOnce({
      labId: 16,
      studentId: 601,
      status: 'SCORE_PUBLISHED',
      submission: {
        submissionId: 160,
        labId: 16,
        studentId: 601,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 88,
        finalScore: 95,
        version: 2,
        submittedAt: '2026-06-01T12:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false,
        code: "print('published score')",
        sourceFile: null,
        latestReport: {
          reportId: 916,
          submissionId: 160,
          fileName: 'report-v2.pdf',
          fileType: 'PDF',
          fileSize: 2048,
          version: 2,
          score: 30,
          comment: '报告结构完整',
          submittedAt: '2026-06-01T12:10:00',
          downloadUrl: '/api/v1/labs/16/reports/916/download'
        },
        latestScore: {
          submissionId: 160,
          reportId: 916,
          autoScore: 88,
          reportScore: 30,
          manualScore: 92,
          finalScore: 95,
          comment: '整体实现稳定',
          hasChangeLogs: true,
          scoredAt: '2026-06-01T13:00:00',
          updatedAt: '2026-06-01T13:20:00'
        }
      },
      evaluationResult: {
        submissionId: 160,
        evaluationStatus: 'ACCEPTED',
        score: 88,
        passedCases: 2,
        totalCases: 2,
        message: '全部用例通过',
        caseResults: [],
        submittedAt: '2026-06-01T12:00:00',
        finishedAt: '2026-06-01T12:00:03'
      },
      latestReport: {
        reportId: 916,
        submissionId: 160,
        fileName: 'report-v2.pdf',
        fileType: 'PDF',
        fileSize: 2048,
        version: 2,
        score: 30,
        comment: '报告结构完整',
        submittedAt: '2026-06-01T12:10:00',
        downloadUrl: '/api/v1/labs/16/reports/916/download'
      },
      latestScore: {
        submissionId: 160,
        reportId: 916,
        autoScore: 88,
        reportScore: 30,
        manualScore: 92,
        finalScore: 95,
        comment: '整体实现稳定',
        hasChangeLogs: true,
        scoredAt: '2026-06-01T13:00:00',
        updatedAt: '2026-06-01T13:20:00'
      },
      publishedAt: '2026-06-01T13:30:00'
    });

    const wrapper = mount(LabStudentView, {
      props: {
        courseId: 101,
        labId: 16
      }
    });
    await flushPromises();

    expect(labApi.getLabResult).toHaveBeenCalledWith(16, 601);
    expect(wrapper.text()).toContain('最终得分：95');
    expect(wrapper.text()).toContain('人工评分：92');
    expect(wrapper.text()).toContain('报告评分：30');
    expect(wrapper.text()).toContain('教师评语：整体实现稳定');
    expect(wrapper.text()).toContain('评分已更新');
    expect(wrapper.text()).toContain('报告评语：报告结构完整');
  });
});

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

async function flushPromises() {
  for (let tick = 0; tick < 12; tick += 1) {
    await Promise.resolve();
  }
}
