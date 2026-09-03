import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkManageView from '../../../src/views/hwk/HomeworkManageView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import { currentCourse } from '../../../src/app/runtimeContext';
import type { HomeworkDetail, HomeworkStatistics } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkManageView', () => {
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
      updatedAt: '2026-08-19T08:00:00'
    };
  });

  it('shows one homework context with localized configuration, submission metrics, and deep links', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(detail());
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('数组与循环');
    expect(wrapper.text()).toContain('软件工程实践');
    expect(wrapper.text()).toContain('已发布');
    expect(wrapper.text()).toContain('代码作业');
    expect(wrapper.text()).toContain('100 分');
    expect(wrapper.text()).toContain('2026-08-25 23:59');
    expect(wrapper.text()).toContain('已提交');
    expect(wrapper.text()).toContain('18');
    expect(wrapper.text()).toContain('已完成批阅');
    expect(wrapper.text()).toContain('12');
    expect(wrapper.text()).not.toContain('未完成批阅');
    expect(wrapper.text()).not.toContain('待批阅');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('CODE');
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledWith(7, { page: 1, size: 20 });

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'homework-manage', params: { courseId: 101 } },
      { name: 'homework-submission-workspace', params: { courseId: 101, homeworkId: 7 } },
      { name: 'homework-statistics', params: { courseId: 101, homeworkId: 7 } }
    ]));
    expect(links).not.toContainEqual({ name: 'homework-edit', params: { courseId: 101, homeworkId: 7 } });
    expect(wrapper.get('[data-testid="configuration-locked"]').text()).toContain('配置已锁定');
    expect(wrapper.text()).not.toContain('删除');
  });

  it('keeps the management context usable when statistics are temporarily unavailable', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(detail());
    vi.mocked(homeworkApi.getHomeworkStatistics).mockRejectedValueOnce(new Error('统计聚合失败'));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('数组与循环');
    expect(wrapper.get('[data-testid="statistics-warning"]').text()).toContain('统计聚合失败');
    expect(wrapper.findAllComponents(RouterLinkStub).some((link) => {
      const to = link.props('to');
      return typeof to === 'object' && to.name === 'homework-submission-workspace';
    })).toBe(true);
  });

  it('does not expose the draft editor for a NOT_OPEN legacy state', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(detail({ status: 'NOT_OPEN' }));
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('未开放');
    expect(wrapper.findAllComponents(RouterLinkStub).some((link) => {
      const to = link.props('to');
      return typeof to === 'object' && to.name === 'homework-edit';
    })).toBe(false);
  });

  it('does not show an obsolete publication blocker for a FILE draft', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(detail({
      title: '课程报告附件',
      type: 'FILE',
      status: 'DRAFT',
      publishedAt: null,
      judgeConfigId: null,
      testCases: []
    }));
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-testid="file-contract-warning"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('#214');
    expect(wrapper.text()).not.toContain('附件上传与安全提交链路完成前不可发布');
    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toContainEqual({ name: 'homework-edit', params: { courseId: 101, homeworkId: 7 } });
    expect(links).not.toContainEqual({
      name: 'homework-submission-workspace',
      params: { courseId: 101, homeworkId: 7 }
    });
  });

  it('renders a recoverable context error and does not request statistics until detail succeeds', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(detail({ courseId: 202 }))
      .mockResolvedValueOnce(detail());
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce(statistics());
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('作业与当前课程不匹配');
    expect(homeworkApi.getHomeworkStatistics).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('h1').text()).toBe('数组与循环');
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledWith(7, { page: 1, size: 20 });
  });
});

function mountView() {
  return mount(HomeworkManageView, {
    props: { courseId: 101, homeworkId: 7 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function detail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 8,
    judgeConfigId: 9,
    title: '数组与循环',
    description: '完成数组遍历与循环控制练习。',
    type: 'CODE',
    status: 'PUBLISHED',
    deadline: '2026-08-25T23:59:00',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    createdBy: 9,
    publishedAt: '2026-08-19T09:00:00',
    createdAt: '2026-08-18T09:00:00',
    updatedAt: '2026-08-19T09:00:00',
    languageLimitJson: '["java","python"]',
    timeLimitMs: 2000,
    memoryLimitKb: 131072,
    outputCompareMode: 'TRIM',
    questions: [],
    testCases: [
      {
        id: 70,
        homeworkId: 7,
        inputData: '1 2 3',
        expectedOutput: '6',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 2000,
        memoryLimitKb: 131072,
        sortOrder: 1
      }
    ],
    deleted: false,
    ...overrides
  };
}

function statistics(overrides: Partial<HomeworkStatistics> = {}): HomeworkStatistics {
  return {
    homeworkId: 7,
    courseId: 101,
    totalStudentCount: 20,
    submittedCount: 18,
    unsubmittedCount: 2,
    evaluatedCount: 16,
    reviewedCount: 12,
    autoEvaluableCount: 16,
    pendingEvaluationCount: 0,
    pendingReviewCount: 6,
    scoredCount: 16,
    averageScore: 84,
    maxScore: 100,
    minScore: 48,
    scoreDistribution: {
      '0-59': 1,
      '60-69': 1,
      '70-79': 2,
      '80-89': 4,
      '90-100': 8
    },
    generatedAt: '2026-08-22T10:30:00+08:00',
    unsubmittedPage: 1,
    unsubmittedSize: 20,
    unsubmittedTotal: 2,
    unsubmittedStudentIds: [501, 502],
    ...overrides
  };
}
