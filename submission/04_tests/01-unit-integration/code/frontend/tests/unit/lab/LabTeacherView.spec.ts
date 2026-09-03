import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LabTeacherView from '../../../src/views/lab/LabTeacherView.vue';
import * as labApi from '../../../src/api/lab/labs';
import { currentCourse } from '../../../src/app/runtimeContext';
import type { LabExperimentSummary, LabSubmissionHistoryItem } from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');

const draft = labSummary({ id: 1, title: '编译原理词法分析', status: 'DRAFT' });
const published = labSummary({ id: 2, title: 'Docker IO 评测', status: 'PUBLISHED' });
const closed = labSummary({ id: 3, title: '数据库索引实验', status: 'CLOSED' });
const scored = labSummary({ id: 4, title: '网络协议分析', status: 'SCORE_PUBLISHED' });

describe('LabTeacherView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentCourse.value = {
      id: 101,
      name: '软件工程实践',
      teacherId: 9,
      teacherName: '周老师',
      enrollmentMode: 'PUBLIC',
      status: 'ACTIVE',
      memberCount: 32,
      member: true,
      manageable: true,
      createdAt: '2026-08-01T08:00:00',
      updatedAt: '2026-08-18T08:00:00'
    };
    vi.stubGlobal('confirm', vi.fn(() => true));
  });

  afterEach(() => {
    currentCourse.value = null;
    vi.unstubAllGlobals();
  });

  it('starts with the experiment table, localized lifecycle states, summaries, and task routes', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([draft, published, closed, scored]);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([submission({ labId: 2, finalScore: null })])
      .mockResolvedValueOnce([submission({ labId: 3, finalScore: 88 })])
      .mockResolvedValueOnce([submission({ labId: 4, finalScore: 92 })]);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-testid="lab-teacher-index"]').exists()).toBe(true);
    expect(wrapper.get('h1').text()).toBe('实验管理');
    expect(wrapper.text()).toContain('软件工程实践');
    expect(wrapper.text()).toContain('草稿');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('已截止');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).not.toContain('DRAFT');
    expect(wrapper.get('[data-testid="summary-strip"]').text()).toContain('待批阅');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'lab-create', params: { courseId: 101 } },
      { name: 'lab-manage-detail', params: { courseId: 101, labId: 2 } },
      { name: 'lab-edit', params: { courseId: 101, labId: 1 } },
      { name: 'lab-submission-workspace', params: { courseId: 101, labId: 2 } },
      { name: 'lab-statistics', params: { courseId: 101, labId: 2 } }
    ]));
  });

  it('filters by keyword, lifecycle, and pending-review attention without refetching', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValueOnce([draft, published, closed]);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([submission({ labId: 2, finalScore: null })])
      .mockResolvedValueOnce([submission({ labId: 3, finalScore: 88 })]);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="keyword"]').setValue('Docker');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    expect(wrapper.text()).toContain('Docker IO 评测');
    expect(wrapper.text()).not.toContain('数据库索引实验');

    await wrapper.get('[name="keyword"]').setValue('');
    await wrapper.get('[name="status"]').setValue('PUBLISHED');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    expect(wrapper.text()).toContain('Docker IO 评测');
    expect(wrapper.text()).not.toContain('编译原理词法分析');

    await wrapper.get('[name="status"]').setValue('');
    await wrapper.get('[name="attention"]').setValue('review');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    expect(wrapper.text()).toContain('Docker IO 评测');
    expect(wrapper.text()).not.toContain('数据库索引实验');
    expect(labApi.listLabs).toHaveBeenCalledTimes(1);
  });

  it('confirms lifecycle operations, shows per-row pending state, and refreshes after success', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([draft, published, closed]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);
    const pending = deferred<LabExperimentSummary>();
    vi.mocked(labApi.publishLab).mockReturnValueOnce(pending.promise);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-testid="publish-lab-1"]').trigger('click');
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('编译原理词法分析'));
    expect(wrapper.get('[data-testid="publish-lab-1"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="publish-lab-1"]').text()).toContain('处理中');

    pending.resolve({ ...draft, status: 'PUBLISHED' });
    await flushPromises();
    expect(labApi.publishLab).toHaveBeenCalledWith(1);
    expect(wrapper.get('[role="status"]').text()).toContain('发布成功');

    await wrapper.get('[data-testid="close-lab-2"]').trigger('click');
    await flushPromises();
    expect(labApi.closeLab).toHaveBeenCalledWith(2);

    await wrapper.get('[data-testid="release-lab-3"]').trigger('click');
    await flushPromises();
    expect(labApi.releaseLabScores).toHaveBeenCalledWith(3);

    await wrapper.get('[data-testid="delete-lab-1"]').trigger('click');
    await flushPromises();
    expect(labApi.deleteLab).toHaveBeenCalledWith(1);
  });

  it('recovers from initial-load and lifecycle failures without losing the action', async () => {
    vi.mocked(labApi.listLabs)
      .mockRejectedValueOnce(new Error('实验服务暂不可用'))
      .mockResolvedValueOnce([draft]);
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue([]);

    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('实验服务暂不可用');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('编译原理词法分析');

    vi.mocked(labApi.deleteLab).mockRejectedValueOnce(new Error('草稿正在被编辑'));
    await wrapper.get('[data-testid="delete-lab-1"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="operation-error"]').text()).toContain('草稿正在被编辑');
    expect(wrapper.get('[data-testid="delete-lab-1"]').attributes('disabled')).toBeUndefined();
  });
});

function mountView() {
  return mount(LabTeacherView, {
    props: { courseId: 101 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function labSummary(overrides: Partial<LabExperimentSummary> = {}): LabExperimentSummary {
  return {
    id: 1,
    courseId: 101,
    title: '实验',
    status: 'DRAFT',
    deadline: '2026-08-25T23:59:00',
    maxScore: 100,
    evaluationMode: 'MIXED',
    autoEvaluate: true,
    reportRequired: true,
    publishedAt: null,
    deleted: false,
    ...overrides
  };
}

function submission(overrides: Partial<LabSubmissionHistoryItem> = {}): LabSubmissionHistoryItem {
  return {
    submissionId: 201,
    labId: 1,
    studentId: 501,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 80,
    finalScore: null,
    version: 1,
    submittedAt: '2026-08-18T10:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: false,
    ...overrides
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}
