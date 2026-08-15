import { readFileSync } from 'node:fs';
import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionWorkspaceView from '../../../src/views/lab/LabSubmissionWorkspaceView.vue';
import * as labApi from '../../../src/api/lab/labs';
import type {
  LabScoreSummary,
  LabSubmissionDetail,
  LabSubmissionHistoryItem
} from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');

const submissions: LabSubmissionHistoryItem[] = [
  {
    submissionId: 301,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 92,
    finalScore: 95,
    version: 2,
    submittedAt: '2026-06-01T10:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: false
  },
  {
    submissionId: 302,
    labId: 7,
    studentId: 602,
    language: 'java',
    submitStatus: 'LATE',
    evaluationStatus: 'PENDING',
    autoScore: null,
    finalScore: null,
    version: 1,
    submittedAt: '2026-06-02T11:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: true
  },
  {
    submissionId: 303,
    labId: 7,
    studentId: 603,
    language: 'cpp',
    submitStatus: 'WITHDRAWN',
    evaluationStatus: 'COMPILE_ERROR',
    autoScore: 0,
    finalScore: null,
    version: 3,
    submittedAt: '2026-06-02T12:00:00',
    isLatest: false,
    isFinal: false,
    isScoringBasis: false,
    hasFile: false
  }
];

const detail: LabSubmissionDetail = {
  ...submissions[0],
  code: "print('workspace detail')",
  fileId: null,
  latestReport: {
    reportId: 901,
    submissionId: 301,
    fileName: 'lab-report.pdf',
    fileType: 'PDF',
    fileSize: 2048,
    version: 1,
    score: 10,
    comment: '结构完整',
    submittedAt: '2026-06-01T10:05:00',
    downloadUrl: '/reports/901'
  },
  latestScore: {
    submissionId: 301,
    reportId: 901,
    autoScore: 92,
    reportScore: 10,
    manualScore: 93,
    finalScore: 95,
    comment: '完成良好',
    hasChangeLogs: false,
    scoredAt: '2026-06-01T12:00:00',
    updatedAt: '2026-06-01T12:00:00'
  }
};

describe('LabSubmissionWorkspaceView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads the teacher queue, opens the first submission, and presents Chinese summaries and statuses', async () => {
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce(submissions);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail);

    const wrapper = mount(LabSubmissionWorkspaceView, {
      props: { courseId: 101, labId: 7 }
    });
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7, {});
    expect(labApi.getLabSubmissionDetail).toHaveBeenCalledWith(7, 301);
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('3');
    expect(wrapper.get('[data-testid="summary-evaluation-pending"]').text()).toContain('1');
    expect(wrapper.get('[data-testid="summary-scoring-pending"]').text()).toContain('2');
    expect(wrapper.get('[data-testid="summary-late"]').text()).toContain('1');
    expect(wrapper.text()).toContain('已提交');
    expect(wrapper.text()).toContain('逾期提交');
    expect(wrapper.text()).toContain('已撤回');
    expect(wrapper.text()).toContain('评测通过');
    expect(wrapper.text()).toContain('排队中');
    expect(wrapper.text()).toContain('编译错误');
    expect(wrapper.text()).toContain("print('workspace detail')");
    expect(wrapper.text()).not.toContain('ACCEPTED');
    expect(wrapper.text()).not.toContain('PENDING');
    expect(wrapper.text()).not.toContain('COMPILE_ERROR');
  });

  it('sends the complete teacher filter contract and reloads the matching queue', async () => {
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce(submissions)
      .mockResolvedValueOnce([submissions[1]]);
    vi.mocked(labApi.getLabSubmissionDetail)
      .mockResolvedValueOnce(detail)
      .mockResolvedValueOnce({
        ...submissions[1],
        code: 'public class Main {}',
        fileId: 'file-302',
        latestReport: null,
        latestScore: null
      });

    const wrapper = mount(LabSubmissionWorkspaceView, {
      props: { courseId: 101, labId: 7 }
    });
    await flushPromises();

    await wrapper.get('[name="studentId"]').setValue('602');
    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[name="evaluationStatus"]').setValue('PENDING');
    await wrapper.get('[name="overdue"]').setValue(true);
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenLastCalledWith(7, {
      studentId: 602,
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      overdue: true
    });
    expect(labApi.getLabSubmissionDetail).toHaveBeenLastCalledWith(7, 302);
    expect(wrapper.text()).toContain('学生 #602');
    expect(wrapper.text()).toContain('public class Main {}');
  });

  it('shows a recoverable queue error and then the filtered empty state', async () => {
    vi.mocked(labApi.listLabSubmissions)
      .mockRejectedValueOnce(new Error('提交队列加载失败'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(LabSubmissionWorkspaceView, {
      props: { courseId: 101, labId: 7 }
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('提交队列加载失败');
    await wrapper.get('[data-action="retry-submissions"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('暂无符合条件的提交');
  });

  it('validates score changes and saves the selected submission through the scoring API', async () => {
    const updatedScore: LabScoreSummary = {
      submissionId: 301,
      reportId: 901,
      autoScore: 92,
      reportScore: 12,
      manualScore: 96,
      finalScore: 98,
      comment: '复核后调整',
      hasChangeLogs: true,
      scoredAt: '2026-06-01T12:00:00',
      updatedAt: '2026-06-02T12:00:00'
    };
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([submissions[0]]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce(detail);
    vi.mocked(labApi.scoreLabSubmission).mockResolvedValueOnce(updatedScore);

    const wrapper = mount(LabSubmissionWorkspaceView, {
      props: { courseId: 101, labId: 7 }
    });
    await flushPromises();

    await wrapper.get('[name="manualScore"]').setValue('96');
    await wrapper.get('[name="reportScore"]').setValue('12');
    await wrapper.get('[name="finalScore"]').setValue('98');
    await wrapper.get('[name="comment"]').setValue('复核后调整');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    await flushPromises();

    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('修改已评分记录时必须填写修改原因');

    await wrapper.get('[name="changeReason"]').setValue('复查实验报告与代码');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    await flushPromises();

    expect(labApi.scoreLabSubmission).toHaveBeenCalledWith(7, 301, {
      manualScore: 96,
      reportScore: 12,
      finalScore: 98,
      comment: '复核后调整',
      changeReason: '复查实验报告与代码'
    });
    expect(wrapper.get('[role="status"]').text()).toContain('评分已保存');
    expect(wrapper.get('[data-testid="selected-final-score"]').text()).toContain('98');
  });

  it('keeps score inputs visible and reports save failures', async () => {
    vi.mocked(labApi.listLabSubmissions).mockResolvedValueOnce([submissions[0]]);
    vi.mocked(labApi.getLabSubmissionDetail).mockResolvedValueOnce({
      ...detail,
      latestScore: null,
      finalScore: null
    });
    vi.mocked(labApi.scoreLabSubmission).mockRejectedValueOnce(new Error('评分服务暂不可用'));

    const wrapper = mount(LabSubmissionWorkspaceView, {
      props: { courseId: 101, labId: 7 }
    });
    await flushPromises();

    await wrapper.get('[name="manualScore"]').setValue('88');
    await wrapper.get('[name="finalScore"]').setValue('90');
    await wrapper.get('[name="comment"]').setValue('待服务恢复后重试');
    await wrapper.get('[data-action="score-submission"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="score-error"]').text()).toContain('评分服务暂不可用');
    expect((wrapper.get('[name="manualScore"]').element as HTMLInputElement).value).toBe('88');
    expect((wrapper.get('[name="finalScore"]').element as HTMLInputElement).value).toBe('90');
  });

  it('uses a card queue and collapses the workspace to one column at phone width', () => {
    const source = readFileSync('src/views/lab/LabSubmissionWorkspaceView.vue', 'utf8');

    expect(source).not.toContain('<table');
    expect(source).toContain('data-action="jump-to-submission-filters"');
    expect(source).toContain('href="#submission-filter-title"');
    expect(source).toMatch(/@media\s*\(max-width:\s*760px\)/);
    expect(source).toMatch(
      /@media\s*\(max-width:\s*760px\)[\s\S]*?\.lab-workspace__columns\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/
    );
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}
