import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabTeacherView from '../../../src/views/lab/LabTeacherView.vue';
import * as labApi from '../../../src/api/lab/labs';
import type { LabScoreSummary, LabStatistics, LabSubmissionDetail } from '../../../src/types/lab';

const downloadLabReportMock = vi.hoisted(() => vi.fn());
const scoreLabSubmissionMock = vi.hoisted(() => vi.fn());

vi.mock('../../../src/api/lab/labs', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../src/api/lab/labs')>();
  const mockedEntries = Object.fromEntries(Object.keys(actual).map((key) => [key, vi.fn()]));
  return {
    ...mockedEntries,
    downloadLabReport: downloadLabReportMock,
    scoreLabSubmission: scoreLabSubmissionMock
  };
});

describe('LabTeacherView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
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

  it('updates publishes closes releases scores and deletes draft labs through teacher actions', async () => {
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
    vi.mocked(labApi.releaseLabScores).mockResolvedValueOnce({
      id: 7,
      courseId: 101,
      title: '实验二-修订',
      status: 'SCORE_PUBLISHED',
      deadline: '2026-06-25T23:59:59',
      maxScore: 120,
      evaluationMode: 'MIXED',
      autoEvaluate: false,
      reportRequired: true,
      publishedAt: '2026-06-26T10:00:00',
      deleted: false
    });
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([
      {
        id: 7,
        courseId: 101,
        title: '实验二-修订',
        status: 'SCORE_PUBLISHED',
        deadline: '2026-06-25T23:59:59',
        maxScore: 120,
        evaluationMode: 'MIXED',
        autoEvaluate: false,
        reportRequired: true,
        publishedAt: '2026-06-26T10:00:00',
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

    await wrapper.findAll('button').find((button) => button.text() === '发布成绩')?.trigger('click');
    await flushPromises();
    expect(labApi.releaseLabScores).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('成绩发布成功');
    expect(wrapper.text()).toContain('SCORE_PUBLISHED');

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

  it('loads lab statistics into the teacher statistics panel', async () => {
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
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce(labStatisticsFixture());

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '统计')?.trigger('click');
    await flushPromises();

    expect(labApi.getLabStatistics).toHaveBeenCalledWith(12);
    expect(wrapper.text()).toContain('实验统计概览');
    expect(wrapper.text()).toContain('实验十二');
    expect(wrapper.text()).toContain('66.67%');
    expect(wrapper.text()).toContain('33.33%');
    expect(wrapper.text()).toContain('703');
    expect(wrapper.text()).toContain('90-100');
    expect(wrapper.text()).toContain('1 人');
    const distributionChart = wrapper.get('[data-testid="score-distribution-chart"]');
    const distributionBars = distributionChart.findAll('[data-testid="score-distribution-bar"]');
    expect(distributionChart.attributes('role')).toBe('img');
    expect(distributionChart.attributes('aria-label')).toContain('分数分布柱状图');
    expect(distributionBars).toHaveLength(5);
    expect(distributionBars[1].attributes('aria-label')).toBe('60-69：1 人');
    expect(distributionBars[1].attributes('style')).toContain('--bar-height: 100%;');
    expect(wrapper.text()).toContain('统计生成时间：2026-06-06 23:00:00');
  });

  it('shows a teacher-facing error when lab statistics loading fails', async () => {
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
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.getLabStatistics).mockRejectedValueOnce(new Error('实验统计加载失败'));

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '统计')?.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('实验统计加载失败');
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
      fileId: 'file-301',
      latestReport: {
        reportId: 901,
        submissionId: 301,
        fileName: 'report-v2.pdf',
        fileType: 'PDF',
        fileSize: 4096,
        version: 2,
        score: 95,
        comment: '报告完整',
        submittedAt: '2026-06-26T00:20:00',
        downloadUrl: '/api/v1/labs/12/reports/901/download'
      }
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
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('602');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('LATE');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('最新版本');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('当前有效版本');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('当前评分依据');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('包含文件');

    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(12, 301);
    expect(wrapper.text()).toContain("print('teacher detail')");
    expect(wrapper.text()).toContain('file-301');
    expect(wrapper.text()).toContain('report-v2.pdf');
    expect(wrapper.text()).toContain('报告完整');
    expect(wrapper.text()).toContain('下载报告');
    expect(wrapper.find('.labs__submission-detail').text()).toContain('最新版本');
    expect(wrapper.find('.labs__submission-detail').text()).toContain('当前有效版本');
    expect(wrapper.find('.labs__submission-detail').text()).toContain('当前评分依据');
    expect(wrapper.find('.labs__submission-detail').text()).toContain('包含文件');
  });

  it('downloads a submission report through the lab download action', async () => {
    const reportBlob = new Blob(['teacher report'], { type: 'application/pdf' });
    downloadLabReportMock.mockResolvedValueOnce({
      blob: reportBlob,
      filename: 'report-v2.pdf'
    });
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:report-v2'),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

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
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 301,
        labId: 12,
        studentId: 602,
        language: 'python',
        submitStatus: 'SUBMITTED',
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
      submitStatus: 'SUBMITTED',
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
      fileId: 'file-301',
      latestReport: {
        reportId: 901,
        submissionId: 301,
        fileName: 'report-v2.pdf',
        fileType: 'PDF',
        fileSize: 4096,
        version: 2,
        score: 95,
        comment: '报告完整',
        submittedAt: '2026-06-26T00:20:00',
        downloadUrl: '/api/v1/labs/12/reports/901/download'
      }
    });

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '查看提交')?.trigger('click');
    await flushPromises();
    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    const downloadButton = wrapper.findAll('button').find((button) => button.text() === '下载报告');
    expect(downloadButton).toBeTruthy();

    await downloadButton!.trigger('click');
    await flushPromises();

    expect(downloadLabReportMock).toHaveBeenCalledWith(12, 901);
    expect(URL.createObjectURL).toHaveBeenCalledWith(reportBlob);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report-v2');
  });

  it('scores a submission report and updates the visible report feedback', async () => {
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
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 301,
        labId: 12,
        studentId: 602,
        language: 'python',
        submitStatus: 'SUBMITTED',
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
      submitStatus: 'SUBMITTED',
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
      fileId: 'file-301',
      latestReport: {
        reportId: 901,
        submissionId: 301,
        fileName: 'report-v2.pdf',
        fileType: 'PDF',
        fileSize: 4096,
        version: 2,
        score: null,
        comment: null,
        submittedAt: '2026-06-26T00:20:00',
        downloadUrl: '/api/v1/labs/12/reports/901/download'
      }
    });
    vi.mocked(labApi.scoreLabReport).mockResolvedValueOnce({
      reportId: 901,
      submissionId: 301,
      fileName: 'report-v2.pdf',
      fileType: 'PDF',
      fileSize: 4096,
      version: 2,
      score: 95,
      comment: '报告完整',
      submittedAt: '2026-06-26T00:20:00',
      downloadUrl: '/api/v1/labs/12/reports/901/download'
    });

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '查看提交')?.trigger('click');
    await flushPromises();
    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    await wrapper.get('[name="reportScore"]').setValue('95');
    await wrapper.get('[name="reportComment"]').setValue('报告完整');
    await wrapper.get('[data-action="score-report"]').trigger('click');
    await flushPromises();

    expect(labApi.scoreLabReport).toHaveBeenCalledWith(12, 901, {
      score: 95,
      comment: '报告完整'
    });
    expect(wrapper.text()).toContain('报告评分：95');
    expect(wrapper.text()).toContain('报告评语：报告完整');
    expect(wrapper.text()).toContain('报告评分已保存');
  });

  it('shows persisted submission scoring data and saves rescoring changes', async () => {
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
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
      {
        submissionId: 301,
        labId: 12,
        studentId: 602,
        language: 'python',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 88,
        finalScore: 95,
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
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'ACCEPTED',
      autoScore: 88,
      finalScore: 95,
      version: 2,
      submittedAt: '2026-06-26T00:10:00',
      isLatest: true,
      isFinal: true,
      isScoringBasis: true,
      hasFile: true,
      code: "print('teacher detail')",
      fileId: 'file-301',
      latestReport: {
        reportId: 901,
        submissionId: 301,
        fileName: 'report-v2.pdf',
        fileType: 'PDF',
        fileSize: 4096,
        version: 2,
        score: 30,
        comment: '报告完整',
        submittedAt: '2026-06-26T00:20:00',
        downloadUrl: '/api/v1/labs/12/reports/901/download'
      },
      latestScore: {
        submissionId: 301,
        reportId: 901,
        autoScore: 88,
        reportScore: 30,
        manualScore: 92,
        finalScore: 95,
        comment: '整体实现稳定',
        hasChangeLogs: false,
        scoredAt: '2026-06-26T01:00:00',
        updatedAt: '2026-06-26T01:00:00'
      }
    });
    vi.mocked(scoreLabSubmissionMock).mockResolvedValueOnce({
      submissionId: 301,
      reportId: 901,
      autoScore: 88,
      reportScore: 35,
      manualScore: 94,
      finalScore: 97,
      comment: '补充修正后通过',
      hasChangeLogs: true,
      scoredAt: '2026-06-26T01:00:00',
      updatedAt: '2026-06-26T01:30:00'
    });

    const wrapper = mount(LabTeacherView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '查看提交')?.trigger('click');
    await flushPromises();
    await wrapper.get('[data-submission-id="301"] button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('自动得分：88');
    expect(wrapper.text()).toContain('人工评分：92');
    expect(wrapper.text()).toContain('报告评分：30');
    expect(wrapper.text()).toContain('教师评语：整体实现稳定');

    await wrapper.get('[name="manualScore"]').setValue('94');
    await wrapper.get('[name="submissionReportScore"]').setValue('35');
    await wrapper.get('[name="finalScore"]').setValue('97');
    await wrapper.get('[name="scoreComment"]').setValue('补充修正后通过');
    await wrapper.get('[name="changeReason"]').setValue('核对评分标准后修正');
    await wrapper.get('[data-action="score-submission"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(scoreLabSubmissionMock)).toHaveBeenCalledWith(12, 301, {
      manualScore: 94,
      reportScore: 35,
      finalScore: 97,
      comment: '补充修正后通过',
      changeReason: '核对评分标准后修正'
    });
    expect(wrapper.text()).toContain('最终得分：97');
    expect(wrapper.text()).toContain('评分留痕：已记录');
    expect(wrapper.text()).toContain('提交评分已保存');
  });

  it('blocks submission score saving when required score fields are empty', async () => {
    const wrapper = await mountTeacherWithSubmissionDetail();

    await wrapper.get('[name="manualScore"]').setValue('');
    await wrapper.get('[name="finalScore"]').setValue('');
    await wrapper.get('[data-action="score-submission"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(scoreLabSubmissionMock)).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('人工评分不能为空');
    expect(wrapper.text()).not.toContain('提交评分已保存');
  });

  it('keeps invalid submission score input as page feedback instead of throwing', async () => {
    const wrapper = await mountTeacherWithSubmissionDetail();

    await wrapper.get('[name="manualScore"]').setValue('-1');
    await wrapper.get('[data-action="score-submission"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(scoreLabSubmissionMock)).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('人工评分不能为负数');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

async function mountTeacherWithSubmissionDetail(detailOverrides: Partial<LabSubmissionDetail> = {}) {
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
      reportRequired: true,
      deleted: false
    }
  ]);
  vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([
    {
      submissionId: 301,
      labId: 12,
      studentId: 602,
      language: 'python',
      submitStatus: 'SUBMITTED',
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
    submitStatus: 'SUBMITTED',
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
    fileId: 'file-301',
    latestReport: {
      reportId: 901,
      submissionId: 301,
      fileName: 'report-v2.pdf',
      fileType: 'PDF',
      fileSize: 4096,
      version: 2,
      score: 30,
      comment: '报告完整',
      submittedAt: '2026-06-26T00:20:00',
      downloadUrl: '/api/v1/labs/12/reports/901/download'
    },
    ...detailOverrides
  });

  const wrapper = mount(LabTeacherView, {
    props: {
      courseId: 101
    }
  });
  await flushPromises();

  await wrapper.findAll('button').find((button) => button.text() === '查看提交')?.trigger('click');
  await flushPromises();
  await wrapper.get('[data-submission-id="301"] button').trigger('click');
  await flushPromises();

  return wrapper;
}

function labStatisticsFixture(overrides: Partial<LabStatistics> = {}): LabStatistics {
  return {
    labId: 12,
    courseId: 101,
    totalStudentCount: 3,
    submittedCount: 2,
    unsubmittedCount: 1,
    evaluatedCount: 1,
    submissionRate: 66.67,
    evaluationCompletionRate: 33.33,
    averageScore: 81.5,
    lateSubmissionCount: 1,
    unsubmittedStudentIds: [703],
    scoreDistribution: {
      '0-59': 0,
      '60-69': 1,
      '70-79': 0,
      '80-89': 0,
      '90-100': 1
    },
    generatedAt: '2026-06-06T23:00:00',
    ...overrides
  };
}
