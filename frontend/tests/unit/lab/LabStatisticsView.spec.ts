import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabStatisticsView from '../../../src/views/lab/LabStatisticsView.vue';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type { LabExperimentDetail, LabStatistics } from '../../../src/types/lab';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/lrn/learningProgress');

const lab: LabExperimentDetail = {
  id: 7,
  courseId: 101,
  chapterId: 4,
  title: '链表综合实验',
  description: '完成链表的增删改查',
  status: 'PUBLISHED',
  deadline: '2026-08-20T23:59:59',
  maxScore: 100,
  attachmentIds: [],
  allowedLanguages: 'java,python',
  evaluationMode: 'DOCKER_IO',
  autoEvaluate: true,
  reportRequired: true,
  timeLimitMs: 2000,
  memoryLimitKb: 262144,
  testcases: [],
  publishedAt: '2026-08-10T09:00:00',
  deleted: false
};

const statistics: LabStatistics = {
  labId: 7,
  courseId: 101,
  totalStudentCount: 20,
  submittedCount: 18,
  unsubmittedCount: 2,
  evaluatedCount: 17,
  submissionRate: 90,
  evaluationCompletionRate: 85,
  averageScore: 86.5,
  lateSubmissionCount: 1,
  unsubmittedStudentIds: [702, 703],
  scoreDistribution: {
    '0-59': 1,
    '60-69': 2,
    '70-79': 3,
    '80-89': 5,
    '90-100': 7
  },
  generatedAt: '2026-08-19T10:30:00'
};

const courseProgress: LearningCourseProgressAggregate = {
  courseId: 101,
  courseName: 'Java 程序设计',
  studentCount: 20,
  averageProgressPercent: 76,
  students: [
    {
      studentId: 702,
      studentName: '李华',
      progressPercent: 60,
      status: 'IN_PROGRESS',
      updatedAt: '2026-08-19T08:00:00'
    },
    {
      studentId: 703,
      studentName: '王芳',
      progressPercent: 40,
      status: 'IN_PROGRESS',
      updatedAt: '2026-08-18T08:00:00'
    }
  ]
};

describe('LabStatisticsView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads the experiment statistics and renders named teacher-facing summaries', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab);
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();

    expect(wrapper.text()).toContain('正在加载实验统计');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(labApi.getLabStatistics).toHaveBeenCalledWith(7);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.text()).toContain('链表综合实验');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('20');
    expect(wrapper.get('[data-testid="summary-submitted"]').text()).toContain('18');
    expect(wrapper.get('[data-testid="summary-unsubmitted"]').text()).toContain('2');
    expect(wrapper.get('[data-testid="summary-submission-rate"]').text()).toContain('90%');
    expect(wrapper.get('[data-testid="summary-evaluation-rate"]').text()).toContain('85%');
    expect(wrapper.get('[data-testid="summary-average-score"]').text()).toContain('86.5');
    expect(wrapper.get('[data-testid="summary-late"]').text()).toContain('1');
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('李华');
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('王芳');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('702');
    expect(wrapper.text()).not.toContain('703');

    const distribution = wrapper.get('[data-testid="score-distribution-chart"]');
    expect(distribution.attributes('role')).toBe('img');
    expect(distribution.attributes('aria-label')).toContain('90–100 分 7 人');
    expect(distribution.findAll('[data-testid="score-distribution-bar"]')).toHaveLength(5);

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'lab-manage-detail', params: { courseId: 101, labId: 7 } },
      { name: 'lab-submission-workspace', params: { courseId: 101, labId: 7 } }
    ]));
  });

  it('keeps the statistics usable when student names cannot be synchronized', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(lab);
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedCount: 1,
      unsubmittedStudentIds: [999]
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-submitted"]').text()).toContain('18');
    expect(wrapper.text()).toContain('未能同步学生姓名');
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('姓名暂不可用');
    expect(wrapper.text()).not.toContain('999');
  });

  it('shows an all-submitted state and a named empty score state', async () => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce({ ...lab, title: '零提交实验' });
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedCount: 0,
      unsubmittedStudentIds: [],
      averageScore: null,
      scoreDistribution: {}
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('全员已提交');
    expect(wrapper.get('[data-testid="summary-average-score"]').text()).toContain('暂无成绩');
    expect(wrapper.get('[data-testid="score-distribution-chart"]').text()).toContain('暂无分数分布数据');
  });

  it('shows a recoverable error and reloads all page data', async () => {
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(lab)
      .mockResolvedValueOnce(lab);
    vi.mocked(labApi.getLabStatistics)
      .mockRejectedValueOnce(new Error('统计服务暂不可用'))
      .mockResolvedValueOnce(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockResolvedValueOnce(courseProgress)
      .mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('统计服务暂不可用');
    expect(wrapper.text()).not.toContain('链表综合实验');

    await wrapper.get('[data-action="retry-statistics"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(labApi.getLabStatistics).toHaveBeenCalledTimes(2);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('链表综合实验');
  });

  it.each([
    {
      scenario: '实验详情编号',
      detail: { ...lab, id: 8 },
      summary: statistics,
      progress: courseProgress,
      expectedMessage: '实验详情归属与当前页面不一致'
    },
    {
      scenario: '实验详情课程',
      detail: { ...lab, courseId: 102 },
      summary: statistics,
      progress: courseProgress,
      expectedMessage: '实验详情归属与当前页面不一致'
    },
    {
      scenario: '统计实验编号',
      detail: lab,
      summary: { ...statistics, labId: 8 },
      progress: courseProgress,
      expectedMessage: '实验统计归属与当前页面不一致'
    },
    {
      scenario: '统计课程',
      detail: lab,
      summary: { ...statistics, courseId: 102 },
      progress: courseProgress,
      expectedMessage: '实验统计归属与当前页面不一致'
    },
    {
      scenario: '学生名单课程',
      detail: lab,
      summary: statistics,
      progress: { ...courseProgress, courseId: 102 },
      expectedMessage: '课程学生数据归属与当前页面不一致'
    }
  ])('rejects a mismatched $scenario before rendering statistics', async ({
    detail,
    summary,
    progress,
    expectedMessage
  }) => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(detail);
    vi.mocked(labApi.getLabStatistics).mockResolvedValueOnce(summary);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(progress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain(expectedMessage);
    expect(wrapper.find('[data-testid="summary-total"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('链表综合实验');
    expect(wrapper.text()).not.toContain('李华');
  });

  it('retries all statistics dependencies after an ownership mismatch', async () => {
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(lab)
      .mockResolvedValueOnce(lab);
    vi.mocked(labApi.getLabStatistics)
      .mockResolvedValueOnce({ ...statistics, courseId: 102 })
      .mockResolvedValueOnce(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockResolvedValueOnce(courseProgress)
      .mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('归属与当前页面不一致');

    await wrapper.get('[data-action="retry-statistics"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(labApi.getLabStatistics).toHaveBeenCalledTimes(2);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('20');
  });
});

function mountView() {
  return mount(LabStatisticsView, {
    props: { courseId: 101, labId: 7 },
    global: {
      stubs: { RouterLink: RouterLinkStub }
    }
  });
}
