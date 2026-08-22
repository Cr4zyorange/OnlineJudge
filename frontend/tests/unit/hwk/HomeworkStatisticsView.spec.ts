import { flushPromises, mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStatisticsView from '../../../src/views/hwk/HomeworkStatisticsView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type {
  HomeworkDetail,
  HomeworkStatistics,
  HomeworkSubmissionSummary,
  PageResponse
} from '../../../src/types/hwk';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

const routerReplaceMock = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const routerPushMock = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>();
  return {
    ...actual,
    useRouter: () => ({ replace: routerReplaceMock, push: routerPushMock })
  };
});
vi.mock('../../../src/api/hwk/homeworks');
vi.mock('../../../src/api/lrn/learningProgress');

const homework: HomeworkDetail = {
  id: 7,
  courseId: 101,
  chapterId: 4,
  judgeConfigId: null,
  createdBy: 9,
  title: '结构化作业',
  description: '完成四种题型练习',
  type: 'OBJECTIVE',
  status: 'PUBLISHED',
  totalScore: 100,
  deadline: '2026-08-20T23:59:59',
  allowResubmit: true,
  allowLateSubmit: false,
  showEvaluationBeforePublish: true,
  deleted: false,
  publishedAt: '2026-08-10T09:00:00',
  createdAt: '2026-08-01T08:00:00',
  updatedAt: '2026-08-19T08:00:00',
  questions: [],
  testCases: []
};

type StatisticsV225 = HomeworkStatistics & {
  autoEvaluableCount: number;
  pendingEvaluationCount: number;
  pendingReviewCount: number;
  scoredCount: number;
  scoreDistribution: Record<'0-59' | '60-69' | '70-79' | '80-89' | '90-100', number>;
  generatedAt: string;
};

const statistics: StatisticsV225 = {
  homeworkId: 7,
  courseId: 101,
  totalStudentCount: 20,
  submittedCount: 18,
  unsubmittedCount: 2,
  evaluatedCount: 16,
  reviewedCount: 15,
  autoEvaluableCount: 16,
  pendingEvaluationCount: 2,
  pendingReviewCount: 3,
  scoredCount: 16,
  averageScore: 86.5,
  maxScore: 98,
  minScore: 61,
  scoreDistribution: {
    '0-59': 1,
    '60-69': 2,
    '70-79': 3,
    '80-89': 4,
    '90-100': 6
  },
  generatedAt: '2026-08-22T10:30:00+08:00',
  unsubmittedPage: 1,
  unsubmittedSize: 20,
  unsubmittedTotal: 2,
  unsubmittedStudentIds: [702, 703]
};

const courseProgress: LearningCourseProgressAggregate = {
  courseId: 101,
  courseName: '软件工程实践',
  studentCount: 20,
  averageProgressPercent: 76,
  students: [
    { studentId: 702, studentName: '李华', progressPercent: 60, status: 'IN_PROGRESS', updatedAt: null },
    { studentId: 703, studentName: '王芳', progressPercent: 40, status: 'IN_PROGRESS', updatedAt: null }
  ]
};

describe('HomeworkStatisticsView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    routerReplaceMock.mockResolvedValue(undefined);
    routerPushMock.mockResolvedValue(undefined);
  });

  it('renders the authoritative evaluation metrics, accessible five buckets, and named follow-up students', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    expect(wrapper.text()).toContain('正在加载作业统计');
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(7);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledWith(7, { page: 1, size: 20 });
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.get('h1').text()).toContain('结构化作业');
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('20');
    expect(wrapper.get('[data-testid="summary-submitted"]').text()).toContain('18');
    expect(wrapper.get('[data-testid="summary-reviewed"]').text()).toContain('15');
    expect(wrapper.get('[data-testid="summary-submission-rate"]').text()).toContain('90%');
    expect(wrapper.get('[data-testid="summary-evaluation-rate"]').text()).toContain('100%');
    expect(wrapper.get('[data-testid="summary-pending-evaluation"]').text()).toContain('2');
    expect(wrapper.get('[data-testid="summary-pending-review"]').text()).toContain('3');
    expect(wrapper.get('[data-testid="summary-scored"]').text()).toContain('16');
    expect(wrapper.get('[data-testid="summary-review-rate"]').text()).toContain('83.3%');
    const distribution = wrapper.get('[aria-label="成绩分布"]');
    expect(distribution.findAll('[data-score-bucket]').map((row) => row.attributes('data-score-bucket')))
      .toEqual(['0-59', '60-69', '70-79', '80-89', '90-100']);
    expect(distribution.text()).toContain('0–59 分1 人');
    expect(distribution.text()).toContain('90–100 分6 人');
    expect(wrapper.get('[data-testid="statistics-generated-at"]').text()).toContain('2026');
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('李华');
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('王芳');
    expect(wrapper.text()).not.toContain('702');
    expect(wrapper.text()).not.toContain('703');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'homework-manage-detail', params: { courseId: 101, homeworkId: 7 } },
      { name: 'homework-submission-workspace', params: { courseId: 101, homeworkId: 7 } }
    ]));
  });

  it('keeps metrics usable when names fail and never exposes internal student ids', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedCount: 1,
      unsubmittedTotal: 1,
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

  it.each([
    ['TEXT', '文本作业'],
    ['FILE', '文件作业']
  ] as const)('renders evaluation completion as not applicable when %s has no auto-evaluable submissions', async (type, typeName) => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce({
      ...homework,
      type,
      title: typeName
    });
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      autoEvaluableCount: 0,
      evaluatedCount: 7,
      reviewedCount: 12
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-evaluated"]').text()).toContain('7');
    expect(wrapper.get('[data-testid="summary-submission-rate"]').text()).toContain('90%');
    expect(wrapper.get('[data-testid="summary-review-rate"]').text()).toContain('66.7%');
    expect(wrapper.get('[data-testid="summary-evaluation-rate"]').text()).toContain('不适用');
  });

  it('filters synthetic LRN student labels without exposing the embedded student id', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedCount: 2,
      unsubmittedTotal: 2,
      unsubmittedStudentIds: [702, 703]
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce({
      ...courseProgress,
      students: [
        {
          studentId: 702,
          studentName: '学生 702',
          progressPercent: 60,
          status: 'IN_PROGRESS',
          updatedAt: null
        },
        {
          studentId: 703,
          studentName: '703',
          progressPercent: 40,
          status: 'IN_PROGRESS',
          updatedAt: null
        }
      ]
    });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('姓名暂不可用');
    expect(wrapper.get('[role="status"]').text()).toContain('部分学生姓名尚未同步');
    expect(wrapper.text()).not.toContain('学生 702');
    expect(wrapper.text()).not.toContain('702');
    expect(wrapper.text()).not.toContain('703');
  });

  it('exposes three count-backed follow-up links and attention-aware queue deep links', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[aria-label="待处理名单"]')
      .findAllComponents(RouterLinkStub)
      .map((link: VueWrapper) => ({
        text: link.text(),
        to: (link.props() as { to: unknown }).to
      })))
      .toEqual([
        {
          text: '未提交 2',
          to: {
            name: 'homework-statistics',
            params: { courseId: 101, homeworkId: 7 },
            query: {}
          }
        },
        {
          text: '待评测 2',
          to: {
            name: 'homework-statistics',
            params: { courseId: 101, homeworkId: 7 },
            query: { attention: 'EVALUATION_PENDING' }
          }
        },
        {
          text: '待批阅 3',
          to: {
            name: 'homework-statistics',
            params: { courseId: 101, homeworkId: 7 },
            query: { attention: 'REVIEW_PENDING' }
          }
        }
      ]);
  });

  it('restores an evaluation-attention deep link with independent pagination and privacy-safe names', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedPage: 1,
      unsubmittedSize: 1,
      unsubmittedTotal: 1,
      unsubmittedStudentIds: [702]
    });
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      page: 2,
      size: 1,
      total: 3,
      list: [submission({ submissionId: 803, studentId: 999, evaluationStatus: 'RUNNING' })]
    }));
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'));

    const wrapper = mount(HomeworkStatisticsView, {
      props: {
        courseId: 101,
        homeworkId: 7,
        pageSize: 1,
        initialAttention: 'EVALUATION_PENDING',
        initialPage: 2
      },
      global: { stubs: { RouterLink: RouterLinkStub } }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledTimes(1);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledTimes(1);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(1);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledWith(7, { page: 1, size: 1 });
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(7, {
      attention: 'EVALUATION_PENDING',
      page: 2,
      size: 1
    });
    expect(wrapper.get('[aria-label="待评测学生名单"]').text()).toContain('姓名暂不可用');
    expect(wrapper.get('[aria-label="待评测学生名单"]').text()).toContain('评测中');
    expect(wrapper.get('[aria-label="待评测学生分页"]').text()).toContain('第 2 / 3 页');
    expect(wrapper.text()).not.toContain('999');
    expect(wrapper.text()).toContain('第 2 / 3 页');
    expect(routerReplaceMock).not.toHaveBeenCalledWith({
      query: { attention: 'EVALUATION_PENDING' }
    });

    const routes = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(routes).toContainEqual({
      name: 'homework-submission-workspace',
      params: { courseId: 101, homeworkId: 7 },
      query: { attention: 'EVALUATION_PENDING', page: '2' }
    });
    expect(routes).toContainEqual({
      name: 'homework-submission-review',
      params: { courseId: 101, homeworkId: 7, submissionId: 803 },
      query: { attention: 'EVALUATION_PENDING', page: '2' }
    });
  });

  it('pushes user pagination into history while preserving the active attention query', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics);
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage({
        page: 1,
        size: 1,
        total: 2,
        list: [submission({ studentId: 702, evaluationStatus: 'PENDING' })]
      }))
      .mockResolvedValueOnce(submissionPage({
        page: 2,
        size: 1,
        total: 2,
        list: [submission({ submissionId: 804, studentId: 703, evaluationStatus: 'RUNNING' })]
      }));
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress);

    const wrapper = mount(HomeworkStatisticsView, {
      props: {
        courseId: 101,
        homeworkId: 7,
        pageSize: 1,
        initialAttention: 'EVALUATION_PENDING'
      },
      global: { stubs: { RouterLink: RouterLinkStub } }
    });
    await flushPromises();

    await wrapper.get('[data-action="next-follow-up-page"]').trigger('click');
    await wrapper.setProps({ initialPage: 2 });
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledWith({
      query: { attention: 'EVALUATION_PENDING', page: '2' }
    });
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(7, {
      attention: 'EVALUATION_PENDING',
      page: 2,
      size: 1
    });
  });

  it('shows a recoverable ownership error and reloads all dependencies', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce({ ...homework, courseId: 102 })
      .mockResolvedValueOnce(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress);

    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('归属与当前页面不一致');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledTimes(2);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledTimes(2);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('20');
  });

  it('paginates the unsubmitted list with the API-provided page contract', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics)
      .mockResolvedValueOnce({ ...statistics, unsubmittedSize: 1, unsubmittedTotal: 2, unsubmittedStudentIds: [702] })
      .mockResolvedValueOnce({ ...statistics, unsubmittedPage: 2, unsubmittedSize: 1, unsubmittedTotal: 2, unsubmittedStudentIds: [703] });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress);

    const wrapper = mount(HomeworkStatisticsView, {
      props: { courseId: 101, homeworkId: 7, pageSize: 1 },
      global: { stubs: { RouterLink: RouterLinkStub } }
    });
    await flushPromises();

    await wrapper.get('[data-action="next-unsubmitted-page"]').trigger('click');
    await wrapper.setProps({ initialPage: 2 });
    await flushPromises();

    expect(homeworkApi.getHomeworkStatistics).toHaveBeenLastCalledWith(7, { page: 2, size: 1 });
    expect(routerPushMock).toHaveBeenLastCalledWith({ query: { page: '2' } });
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('王芳');
  });

  it('loads the follow-up page restored from the statistics deep link', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      unsubmittedPage: 2,
      unsubmittedSize: 1,
      unsubmittedTotal: 2,
      unsubmittedStudentIds: [703]
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress);

    const wrapper = mount(HomeworkStatisticsView, {
      props: { courseId: 101, homeworkId: 7, pageSize: 1, initialPage: 2 },
      global: { stubs: { RouterLink: RouterLinkStub } }
    });
    await flushPromises();

    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledWith(7, { page: 2, size: 1 });
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('王芳');
  });

  it('recovers to the last valid page when the unsubmitted total shrinks', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework);
    vi.mocked(homeworkApi.getHomeworkStatistics)
      .mockResolvedValueOnce({
        ...statistics,
        unsubmittedPage: 1,
        unsubmittedSize: 1,
        unsubmittedTotal: 2,
        unsubmittedStudentIds: [702]
      })
      .mockResolvedValueOnce({
        ...statistics,
        unsubmittedCount: 1,
        unsubmittedPage: 2,
        unsubmittedSize: 1,
        unsubmittedTotal: 1,
        unsubmittedStudentIds: []
      })
      .mockResolvedValueOnce({
        ...statistics,
        unsubmittedCount: 1,
        unsubmittedPage: 1,
        unsubmittedSize: 1,
        unsubmittedTotal: 1,
        unsubmittedStudentIds: [702]
      });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress);

    const wrapper = mount(HomeworkStatisticsView, {
      props: { courseId: 101, homeworkId: 7, pageSize: 1 },
      global: { stubs: { RouterLink: RouterLinkStub } }
    });
    await flushPromises();
    await wrapper.get('[data-action="next-unsubmitted-page"]').trigger('click');
    await wrapper.setProps({ initialPage: 2 });
    await flushPromises();
    await wrapper.setProps({ initialPage: 1 });
    await flushPromises();

    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledTimes(3);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenLastCalledWith(7, { page: 1, size: 1 });
    expect(wrapper.get('[aria-label="未提交学生名单"]').text()).toContain('李华');
    expect(wrapper.text()).not.toContain('全员已提交');
  });
});

function mountView() {
  return mount(HomeworkStatisticsView, {
    props: { courseId: 101, homeworkId: 7 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function submission(overrides: Partial<HomeworkSubmissionSummary> = {}): HomeworkSubmissionSummary {
  return {
    submissionId: 801,
    homeworkId: 7,
    studentId: 702,
    submitType: 'OBJECTIVE',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'PENDING',
    reviewStatus: 'UNREVIEWED',
    autoScore: null,
    manualScore: null,
    finalScore: null,
    version: 1,
    final: true,
    submittedAt: '2026-08-22T09:30:00+08:00',
    ...overrides
  };
}

function submissionPage(
  overrides: Partial<PageResponse<HomeworkSubmissionSummary>> = {}
): PageResponse<HomeworkSubmissionSummary> {
  return {
    list: [submission()],
    total: 1,
    page: 1,
    size: 20,
    ...overrides
  };
}
