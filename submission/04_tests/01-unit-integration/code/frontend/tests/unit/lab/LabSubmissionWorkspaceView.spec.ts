import { readFileSync } from 'node:fs';
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type LocationQueryRaw } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LabSubmissionWorkspaceView from '../../../src/views/lab/LabSubmissionWorkspaceView.vue';
import * as labApi from '../../../src/api/lab/labs';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type { LabExperimentDetail, LabSubmissionHistoryItem } from '../../../src/types/lab';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/lrn/learningProgress');

describe('LabSubmissionWorkspaceView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(labApi.getLabDetail).mockResolvedValue(labDetail());
    vi.mocked(labApi.listLabSubmissions).mockResolvedValue(submissions());
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress());
  });

  it('loads a queue without opening a submission and links each card to the standalone review page', async () => {
    const { wrapper } = await mountView();

    expect(labApi.getLabDetail).toHaveBeenCalledWith(7);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7, {});
    expect(labApi.getLabSubmissionDetail).not.toHaveBeenCalled();
    expect(labApi.scoreLabSubmission).not.toHaveBeenCalled();
    expect(labApi.downloadLabReport).not.toHaveBeenCalled();
    expect(labApi.evaluateLabSubmission).not.toHaveBeenCalled();

    expect(wrapper.get('#lab-workspace-title').text()).toContain('链表与队列实验');
    expect(wrapper.text()).toContain('数据结构');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('林晓');
    expect(wrapper.get('[data-submission-id="302"]').text()).toContain('周然');
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
    expect(wrapper.text()).not.toContain('ACCEPTED');
    expect(wrapper.text()).not.toContain('PENDING');
    expect(wrapper.text()).not.toContain('COMPILE_ERROR');
    expect(wrapper.text()).not.toContain('提交详情与评分');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toContainEqual({
      name: 'lab-manage-detail',
      params: { courseId: 101, labId: 7 }
    });
    expect(links).toContainEqual({
      name: 'lab-statistics',
      params: { courseId: 101, labId: 7 }
    });
    expect(links).toContainEqual({
      name: 'lab-submission-review',
      params: { courseId: 101, labId: 7, submissionId: 301 }
    });
  });

  it('filters by resolved student name locally and sends only supported queue filters to the API', async () => {
    const { wrapper, router } = await mountView({ role: 'teacher' });

    await wrapper.get('[name="keyword"]').setValue('周');
    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[name="evaluationStatus"]').setValue('PENDING');
    await wrapper.get('[name="overdue"]').setValue(true);
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenLastCalledWith(7, {
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      overdue: true
    });
    expect(wrapper.find('[name="studentId"]').exists()).toBe(false);
    expect(wrapper.find('[data-submission-id="301"]').exists()).toBe(false);
    expect(wrapper.get('[data-submission-id="302"]').text()).toContain('周然');
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周',
      status: 'LATE',
      evaluation: 'PENDING',
      overdue: 'true'
    });
    expect(router.currentRoute.value.query).not.toHaveProperty('role');
  });

  it('restores safe filters from the URL and strips role and unsupported query fields', async () => {
    const { wrapper, router } = await mountView({
      keyword: '周',
      status: 'LATE',
      evaluation: 'PENDING',
      overdue: 'true',
      role: 'teacher',
      studentId: '602'
    });

    expect(labApi.listLabSubmissions).toHaveBeenCalledWith(7, {
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      overdue: true
    });
    expect((wrapper.get('[name="keyword"]').element as HTMLInputElement).value).toBe('周');
    expect(wrapper.find('[data-submission-id="301"]').exists()).toBe(false);
    expect(wrapper.get('[data-submission-id="302"]').text()).toContain('周然');
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周',
      status: 'LATE',
      evaluation: 'PENDING',
      overdue: 'true'
    });
    const reviewLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => {
        const to = link.props('to');
        return typeof to === 'object' && to !== null && 'name' in to && to.name === 'lab-submission-review';
      });
    expect(reviewLink?.props('to')).toEqual({
      name: 'lab-submission-review',
      params: { courseId: 101, labId: 7, submissionId: 302 },
      query: {
        keyword: '周',
        status: 'LATE',
        evaluation: 'PENDING',
        overdue: 'true'
      }
    });
    expect(wrapper.text()).not.toContain('602');
  });

  it.each([
    {
      scenario: '实验编号不匹配',
      detail: labDetail({ id: 8 }),
      progress: courseProgress(),
      expectedMessage: '实验详情归属与当前页面不一致'
    },
    {
      scenario: '实验课程不匹配',
      detail: labDetail({ courseId: 102 }),
      progress: courseProgress(),
      expectedMessage: '实验详情归属与当前页面不一致'
    },
    {
      scenario: '学生名单课程不匹配',
      detail: labDetail(),
      progress: { ...courseProgress(), courseId: 102 },
      expectedMessage: '课程学生数据归属与当前页面不一致'
    }
  ])('rejects $scenario as a fatal retryable error without rendering queue data', async ({
    detail,
    progress,
    expectedMessage
  }) => {
    vi.mocked(labApi.getLabDetail).mockResolvedValueOnce(detail);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(progress);

    const { wrapper } = await mountView();

    expect(wrapper.get('[data-testid="workspace-fatal-error"]').text()).toContain(expectedMessage);
    expect(wrapper.get('[data-action="retry-workspace"]').text()).toContain('重新加载');
    expect(wrapper.find('[data-testid="summary-total"]').exists()).toBe(false);
    expect(wrapper.find('[data-submission-id="301"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('链表与队列实验');
    expect(wrapper.text()).not.toContain('数据结构');
  });

  it('retries every queue dependency after a fatal ownership mismatch', async () => {
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(labDetail({ id: 8 }))
      .mockResolvedValueOnce(labDetail());
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce(submissions())
      .mockResolvedValueOnce(submissions());
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockResolvedValueOnce(courseProgress())
      .mockResolvedValueOnce(courseProgress());

    const { wrapper } = await mountView();
    expect(wrapper.get('[data-testid="workspace-fatal-error"]').text()).toContain('归属与当前页面不一致');

    await wrapper.get('[data-action="retry-workspace"]').trigger('click');
    await flushPromises();

    expect(labApi.getLabDetail).toHaveBeenCalledTimes(2);
    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(2);
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="workspace-fatal-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="summary-total"]').text()).toContain('3');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('林晓');
  });

  it('keeps the queue usable when student names fail and never exposes a student identifier', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'))
      .mockResolvedValueOnce(courseProgress());

    const { wrapper } = await mountView();

    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('学生名单服务暂不可用');
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('学生姓名暂不可用');
    expect(wrapper.get('[data-submission-id="302"]').text()).toContain('学生姓名暂不可用');
    expect(wrapper.text()).not.toContain('601');
    expect(wrapper.text()).not.toContain('602');
    expect(wrapper.text()).not.toContain('603');

    await wrapper.get('[data-action="retry-student-names"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="student-name-warning"]').exists()).toBe(false);
    expect(wrapper.get('[data-submission-id="301"]').text()).toContain('林晓');
  });

  it('shows queue loading, recoverable error, retry, and filtered empty states', async () => {
    const firstRequest = deferred<LabSubmissionHistoryItem[]>();
    vi.mocked(labApi.listLabSubmissions)
      .mockReturnValueOnce(firstRequest.promise)
      .mockResolvedValueOnce([]);

    const { wrapper } = await mountView({}, false);
    expect(wrapper.get('[data-testid="queue-loading"]').text()).toContain('正在加载提交队列');

    firstRequest.reject(new Error('提交队列加载失败'));
    await flushPromises();
    expect(wrapper.get('[data-testid="queue-error"]').text()).toContain('提交队列加载失败');

    await wrapper.get('[data-action="retry-submissions"]').trigger('click');
    await flushPromises();
    expect(labApi.listLabSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="queue-empty"]').text()).toContain('暂无符合条件的提交');
  });

  it('ignores a stale queue response after navigating to a different lab', async () => {
    const staleRequest = deferred<LabSubmissionHistoryItem[]>();
    vi.mocked(labApi.getLabDetail)
      .mockResolvedValueOnce(labDetail())
      .mockResolvedValueOnce(labDetail({ id: 8 }));
    vi.mocked(labApi.listLabSubmissions)
      .mockReturnValueOnce(staleRequest.promise)
      .mockResolvedValueOnce([submission({
        submissionId: 401,
        labId: 8,
        studentId: 604,
        language: 'python'
      })]);
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue({
      ...courseProgress(),
      students: [
        ...courseProgress().students,
        {
          studentId: 604,
          studentName: '新同学',
          progressPercent: 0,
          status: 'NOT_STARTED',
          updatedAt: '2026-08-19T10:00:00'
        }
      ]
    });

    const { wrapper } = await mountView({}, false);
    await wrapper.setProps({ labId: 8 });
    await flushPromises();
    expect(wrapper.get('[data-submission-id="401"]').text()).toContain('新同学');

    staleRequest.resolve(submissions());
    await flushPromises();
    expect(wrapper.find('[data-submission-id="301"]').exists()).toBe(false);
    expect(wrapper.get('[data-submission-id="401"]').text()).toContain('新同学');
  });

  it('clears all filters, reloads the queue, and removes filter query state', async () => {
    const { wrapper, router } = await mountView({
      keyword: '周',
      status: 'LATE',
      evaluation: 'PENDING',
      overdue: 'true'
    });

    await wrapper.get('[data-action="reset-filters"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabSubmissions).toHaveBeenLastCalledWith(7, {});
    expect((wrapper.get('[name="keyword"]').element as HTMLInputElement).value).toBe('');
    expect(router.currentRoute.value.query).toEqual({});
  });

  it('uses a full-width card queue and keeps the filter and cards usable at 390 pixels', () => {
    const source = readFileSync('src/views/lab/LabSubmissionWorkspaceView.vue', 'utf8');

    expect(source).not.toContain('<table');
    expect(source).not.toContain('getLabSubmissionDetail');
    expect(source).not.toContain('scoreLabSubmission');
    expect(source).not.toContain('downloadLabReport');
    expect(source).not.toContain('evaluateLabSubmission');
    expect(source).toMatch(/\.lab-workspace\s*\{[\s\S]*?width:\s*100%/);
    expect(source).toMatch(/@media\s*\(max-width:\s*760px\)/);
    expect(source).toMatch(
      /@media\s*\(max-width:\s*760px\)[\s\S]*?\.filter-form\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/
    );
  });
});

async function mountView(query: LocationQueryRaw = {}, settle = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/courses/:courseId/labs/:labId/manage/submissions',
        name: 'lab-submission-workspace',
        component: { template: '<div />' }
      }
    ]
  });
  await router.push({
    name: 'lab-submission-workspace',
    params: { courseId: 101, labId: 7 },
    query
  });
  await router.isReady();
  const wrapper = mount(LabSubmissionWorkspaceView, {
    props: { courseId: 101, labId: 7 },
    global: {
      plugins: [router],
      stubs: { RouterLink: RouterLinkStub }
    }
  });
  if (settle) {
    await flushPromises();
  } else {
    await Promise.resolve();
  }
  return { wrapper, router };
}

function labDetail(overrides: Partial<LabExperimentDetail> = {}): LabExperimentDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: 12,
    title: '链表与队列实验',
    description: '完成链表和队列的基础实现',
    status: 'PUBLISHED',
    deadline: '2026-08-25T23:59:59',
    maxScore: 100,
    evaluationMode: 'MIXED',
    autoEvaluate: true,
    reportRequired: true,
    publishedAt: '2026-08-19T08:00:00',
    deleted: false,
    attachmentIds: [],
    allowedLanguages: 'python,java,cpp',
    timeLimitMs: 1000,
    memoryLimitKb: 262144,
    testcases: [],
    ...overrides
  };
}

function courseProgress(): LearningCourseProgressAggregate {
  return {
    courseId: 101,
    courseName: '数据结构',
    studentCount: 2,
    averageProgressPercent: 76,
    students: [
      {
        studentId: 601,
        studentName: '林晓',
        progressPercent: 92,
        status: 'COMPLETED',
        updatedAt: '2026-08-19T09:00:00'
      },
      {
        studentId: 602,
        studentName: '周然',
        progressPercent: 60,
        status: 'IN_PROGRESS',
        updatedAt: '2026-08-19T09:00:00'
      }
    ]
  };
}

function submissions(): LabSubmissionHistoryItem[] {
  return [
    submission(),
    submission({
      submissionId: 302,
      studentId: 602,
      language: 'java',
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      autoScore: null,
      finalScore: null,
      version: 1,
      submittedAt: '2026-08-19T11:00:00',
      hasFile: true
    }),
    submission({
      submissionId: 303,
      studentId: 603,
      language: 'cpp',
      submitStatus: 'WITHDRAWN',
      evaluationStatus: 'COMPILE_ERROR',
      autoScore: 0,
      finalScore: null,
      version: 3,
      submittedAt: '2026-08-19T12:00:00',
      isLatest: false,
      isFinal: false,
      isScoringBasis: false
    })
  ];
}

function submission(overrides: Partial<LabSubmissionHistoryItem> = {}): LabSubmissionHistoryItem {
  return {
    submissionId: 301,
    labId: 7,
    studentId: 601,
    language: 'python',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    autoScore: 92,
    finalScore: 95,
    version: 2,
    submittedAt: '2026-08-19T10:00:00',
    isLatest: true,
    isFinal: true,
    isScoringBasis: true,
    hasFile: false,
    ...overrides
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
