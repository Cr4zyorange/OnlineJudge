import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabManageView from '../../../src/views/lab/LabManageView.vue';
import * as labApi from '../../../src/api/lab/labs';
import { currentCourse } from '../../../src/app/runtimeContext';
import type { LabExperimentDetail, LabStatistics } from '../../../src/types/lab';

vi.mock('../../../src/api/lab/labs');

describe('LabManageView', () => {
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
  });

  it('shows one experiment context with localized configuration, metrics, and teacher routes', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValue(detail());
    vi.mocked(labApi.getLabStatistics).mockResolvedValue(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('容器评测实验');
    expect(wrapper.text()).toContain('软件工程实践');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('自动评测 + 教师评分');
    expect(wrapper.text()).toContain('提交率');
    expect(wrapper.text()).toContain('75%');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('MIXED');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'lab-manage', params: { courseId: 101 } },
      { name: 'lab-submission-workspace', params: { courseId: 101, labId: 7 } },
      { name: 'lab-statistics', params: { courseId: 101, labId: 7 } }
    ]));
    expect(links).not.toContainEqual({ name: 'lab-edit', params: { courseId: 101, labId: 7 } });
    expect(wrapper.text()).toContain('配置已锁定');
  });

  it('renders a recoverable detail error and does not request statistics until detail succeeds', async () => {
    vi.mocked(labApi.getLabDetail)
      .mockRejectedValueOnce(new Error('实验不存在或无权访问'))
      .mockResolvedValueOnce(detail());
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('实验不存在或无权访问');
    expect(labApi.getLabStatistics).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('h1').text()).toBe('容器评测实验');
    expect(labApi.getLabStatistics).toHaveBeenCalledWith(7);
  });

  it('keeps the teacher hub usable when statistics are temporarily unavailable', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValue(detail());
    vi.mocked(labApi.getLabStatistics).mockRejectedValueOnce(new Error('统计聚合失败'));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('容器评测实验');
    expect(wrapper.get('[data-testid="statistics-warning"]').text()).toContain('统计聚合失败');
    expect(wrapper.findAllComponents(RouterLinkStub)
      .some((link) => {
        const to = link.props('to');
        return typeof to === 'object' && to.name === 'lab-submission-workspace';
      })).toBe(true);
  });
});

function mountView() {
  return mount(LabManageView, {
    props: { courseId: 101, labId: 7 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function detail(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 8,
    title: '容器评测实验',
    description: '完成容器输入输出评测。',
    status: 'PUBLISHED',
    deadline: '2026-08-25T23:59:00',
    maxScore: 100,
    attachmentIds: [10],
    allowedLanguages: 'python,java',
    evaluationMode: 'MIXED',
    autoEvaluate: true,
    reportRequired: true,
    timeLimitMs: 60000,
    memoryLimitKb: 262144,
    testcases: [],
    publishedAt: '2026-08-19T08:00:00',
    deleted: false,
    ...overrides
  };
}

function statistics(overrides: Partial<LabStatistics> = {}): LabStatistics {
  return {
    labId: 7,
    courseId: 101,
    totalStudentCount: 20,
    submittedCount: 15,
    unsubmittedCount: 5,
    evaluatedCount: 12,
    submissionRate: 75,
    evaluationCompletionRate: 80,
    averageScore: 86,
    lateSubmissionCount: 2,
    unsubmittedStudentIds: [501, 502],
    scoreDistribution: { '0-59': 1, '60-69': 1, '70-79': 2, '80-89': 4, '90-100': 4 },
    generatedAt: '2026-08-19T10:00:00',
    ...overrides
  };
}
