import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStatisticsView from '../../../src/views/hwk/HomeworkStatisticsView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type { HomeworkDetail, HomeworkStatistics } from '../../../src/types/hwk';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

const routerReplaceMock = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>();
  return { ...actual, useRouter: () => ({ replace: routerReplaceMock }) };
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

const statistics: HomeworkStatistics = {
  homeworkId: 7,
  courseId: 101,
  totalStudentCount: 20,
  submittedCount: 18,
  unsubmittedCount: 2,
  evaluatedCount: 16,
  reviewedCount: 15,
  averageScore: 86.5,
  maxScore: 98,
  minScore: 61,
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
  });

  it('renders real homework metrics, derived completion rates, and named follow-up students', async () => {
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
    expect(wrapper.find('[data-testid="summary-evaluation-rate"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="statistics-semantics-note"]').text())
      .toContain('精确评测完成率由 #225 补充统计口径');
    expect(wrapper.get('[data-testid="summary-review-rate"]').text()).toContain('83.3%');
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
    ['FILE', '文件作业'],
    ['CODE', '代码作业']
  ] as const)('never derives an evaluation rate for %s homework', async (type, typeName) => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce({
      ...homework,
      type,
      title: typeName
    });
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValueOnce({
      ...statistics,
      evaluatedCount: 7,
      reviewedCount: 12
    });
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-evaluated"]').text()).toContain('7');
    expect(wrapper.get('[data-testid="summary-submission-rate"]').text()).toContain('90%');
    expect(wrapper.get('[data-testid="summary-review-rate"]').text()).toContain('66.7%');
    expect(wrapper.find('[data-testid="summary-evaluation-rate"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="statistics-semantics-note"]').text())
      .toContain('精确评测完成率由 #225 补充统计口径');
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
    await flushPromises();

    expect(homeworkApi.getHomeworkStatistics).toHaveBeenLastCalledWith(7, { page: 2, size: 1 });
    expect(routerReplaceMock).toHaveBeenLastCalledWith({ query: { page: '2' } });
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
