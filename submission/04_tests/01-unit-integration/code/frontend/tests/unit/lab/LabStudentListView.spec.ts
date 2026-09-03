import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as labApi from '../../../src/api/lab/labs';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
import type {
  LabExperimentStatus,
  LabExperimentSummary,
  LabSubmissionHistoryItem
} from '../../../src/types/lab';
import LabStudentListView from '../../../src/views/lab/LabStudentListView.vue';

vi.mock('../../../src/api/lab/labs');

describe('LabStudentListView visual task-list contract', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-08-15T09:00:00+08:00'));
    resetRuntimeContext();
    currentUser.value = {
      id: 7,
      username: 'lab-student',
      displayName: '实验学生',
      userType: 'STUDENT',
      roles: ['STUDENT'],
      permissions: []
    };
  });

  afterEach(() => {
    vi.useRealTimers();
    resetRuntimeContext();
  });

  it('adapts domain data into searchable task cards without exposing raw enums or role selectors', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950211, '容器 I/O 实验', 'PUBLISHED', '2026-08-16T20:00:00'),
      lab(950201, '链表基础实验', 'SCORE_PUBLISHED', '2026-08-12T20:00:00'),
      lab(950299, '未发布草稿', 'DRAFT', '2026-08-20T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([{
        submissionId: 82,
        labId: 950211,
        studentId: 7,
        language: 'java',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 87,
        finalScore: 88,
        version: 1,
        submittedAt: '2026-08-15T10:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }])
      .mockResolvedValueOnce([{
        submissionId: 81,
        labId: 950201,
        studentId: 7,
        language: 'java',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 92,
        finalScore: 95,
        version: 2,
        submittedAt: '2026-08-12T10:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('2 个可进入实验');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).toContain('最终成绩 95 分');
    expect(wrapper.text()).not.toContain('最终成绩 88 分');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('SCORE_PUBLISHED');
    expect(wrapper.text()).not.toContain('DOCKER_IO');
    expect(wrapper.get('[data-testid="open-lab-950211"]').attributes('href')).toBe('/courses/9501/labs/950211');

    await wrapper.get('[data-testid="lab-keyword-filter"]').setValue('链表');
    expect(wrapper.text()).toContain('链表基础实验');
    expect(wrapper.text()).not.toContain('容器 I/O 实验');
  });

  it('renders a manual experiment as teacher-scored instead of automatic evaluation', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([{
      ...lab(950216, '教师批阅实验', 'PUBLISHED', '2026-08-16T20:00:00'),
      evaluationMode: 'MANUAL' as unknown as LabExperimentSummary['evaluationMode']
    }]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('教师评分');
    expect(wrapper.text()).not.toContain('自动评测');
    expect(wrapper.text()).not.toContain('MANUAL');
  });

  it('shows a retryable failure state', async () => {
    vi.mocked(labApi.listLabs)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('实验列表加载失败');
    expect(wrapper.text()).toContain('网络暂时不可用');
    await wrapper.get('[data-testid="retry-lab-list"]').trigger('click');
    await flushPromises();
    expect(labApi.listLabs).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('当前筛选下没有实验');
  });

  it('keeps the numeric course identifier in navigation URLs without rendering it as page copy', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('9501');
    expect(wrapper.text()).not.toContain('UI-LAB');
  });

  it('shows a retryable failure when any submission history cannot be synchronized', async () => {
    const visibleLabs = [
      lab(950211, '容器 I/O 实验', 'PUBLISHED', '2026-08-16T20:00:00'),
      lab(950212, '数据库事务实验', 'PUBLISHED', '2026-08-17T20:00:00')
    ];
    vi.mocked(labApi.listLabs).mockResolvedValue(visibleLabs);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([submission(950211)])
      .mockRejectedValueOnce(new Error('提交历史接口暂时不可用'))
      .mockResolvedValueOnce([submission(950211)])
      .mockResolvedValueOnce([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('提交记录同步失败');
    expect(wrapper.text()).toContain('重新加载');
    expect(wrapper.text()).not.toMatch(/暂无提交|尚未提交/);

    await wrapper.get('[data-testid="retry-lab-list"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabs).toHaveBeenCalledTimes(2);
    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(4);
    expect(wrapper.text()).not.toContain('实验列表加载失败');
    expect(wrapper.text()).toContain('数据库事务实验');
  });

  it('does not treat the source-attachment flag as experiment-report completion state', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950211, '容器 I/O 实验', 'PUBLISHED', '2026-08-16T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(950211, { hasFile: false })
    ]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('补交实验报告');
    expect(wrapper.text()).toContain('请在实验详情核对报告状态');
  });

  it('keeps archived experiments available for published scores and history', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950213, '已归档算法实验', 'ARCHIVED', '2026-07-01T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(950213, { finalScore: 97 })
    ]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('已归档算法实验');
    expect(wrapper.text()).toContain('已归档');
    expect(wrapper.text()).toContain('最终成绩 97 分');
    expect(wrapper.get('[data-testid="open-lab-950213"]').attributes('href'))
      .toBe('/courses/9501/labs/950213');
  });

  it('counts a closed experiment with history as submitted', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950214, '已截止的容器实验', 'CLOSED', '2026-08-14T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(950214)
    ]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    const summary = wrapper.get('[data-testid="summary-strip"]');
    expect(summary.text()).toContain('已有提交1');
    expect(wrapper.text()).toContain('最近为第 1 版');
  });

  it('shows the scoring-basis version instead of a newer non-scoring submission', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950215, '多版本评分实验', 'SCORE_PUBLISHED', '2026-08-14T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(950215, {
        submissionId: 92,
        version: 2,
        finalScore: 70,
        isLatest: true,
        isFinal: false,
        isScoringBasis: false
      }),
      submission(950215, {
        submissionId: 91,
        version: 1,
        finalScore: 96,
        isLatest: false,
        isFinal: true,
        isScoringBasis: true
      })
    ]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('最终成绩 96 分');
    expect(wrapper.text()).toContain('第 1 版作为评分依据');
    expect(wrapper.text()).not.toContain('最终成绩 70 分');
  });

  it.each([
    ['another experiment', { labId: 950212 }],
    ['another student', { studentId: 8 }]
  ])('rejects every history item belonging to %s instead of attaching it to a task card', async (_case, mismatch) => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950211, '容器 I/O 实验', 'SCORE_PUBLISHED', '2026-08-16T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([
      submission(950211),
      submission(950211, { submissionId: 91, version: 0, ...mismatch })
    ]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('提交记录与当前实验或学生不匹配');
    expect(wrapper.text()).not.toContain('最终成绩 88 分');
  });

  it('reloads for a new course and ignores the previous course response when it arrives late', async () => {
    const previousCourse = deferred<LabExperimentSummary[]>();
    vi.mocked(labApi.listLabs).mockImplementation((courseId) => (
      courseId === 9501
        ? previousCourse.promise
        : Promise.resolve([
          { ...lab(960211, '新课程实验', 'PUBLISHED', '2026-08-18T20:00:00'), courseId: 9601 }
        ])
    ));
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();
    await wrapper.setProps({ courseId: 9601 });
    await flushPromises();

    expect(labApi.listLabs).toHaveBeenNthCalledWith(1, 9501);
    expect(labApi.listLabs).toHaveBeenNthCalledWith(2, 9601);
    expect(wrapper.text()).toContain('新课程实验');

    previousCourse.resolve([
      lab(950299, '旧课程迟到实验', 'PUBLISHED', '2026-08-18T20:00:00')
    ]);
    await flushPromises();

    expect(wrapper.text()).toContain('新课程实验');
    expect(wrapper.text()).not.toContain('旧课程迟到实验');
  });
});

function lab(id: number, title: string, status: LabExperimentStatus, deadline: string): LabExperimentSummary {
  return {
    id,
    courseId: 9501,
    title,
    status,
    deadline,
    maxScore: 100,
    evaluationMode: 'DOCKER_IO' as const,
    autoEvaluate: true,
    reportRequired: id === 950211,
    deleted: false
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function submission(
  labId: number,
  overrides: Partial<LabSubmissionHistoryItem> = {}
): LabSubmissionHistoryItem {
  return {
    submissionId: 82,
    labId,
    studentId: 7,
    language: 'java',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 87,
    finalScore: 88,
    version: 1,
    submittedAt: '2026-08-15T10:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: true,
    ...overrides
  };
}
