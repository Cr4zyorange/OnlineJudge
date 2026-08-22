import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkTeacherView from '../../../src/views/hwk/HomeworkTeacherView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import { currentCourse } from '../../../src/app/runtimeContext';
import type {
  HomeworkDetail,
  HomeworkStatistics,
  HomeworkStatus,
  HomeworkSummary,
  HomeworkType
} from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

const draft = homeworkSummary({ id: 1, title: '软件需求草稿', status: 'DRAFT', type: 'TEXT' });
const published = homeworkSummary({ id: 2, title: '数组与循环', status: 'PUBLISHED', type: 'CODE' });
const closed = homeworkSummary({ id: 3, title: '数据库设计', status: 'CLOSED', type: 'FILE' });
const scored = homeworkSummary({ id: 4, title: '选择题复习', status: 'SCORE_PUBLISHED', type: 'OBJECTIVE' });

describe('HomeworkTeacherView', () => {
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
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-08-20T00:00:00Z').getTime());
    vi.stubGlobal('confirm', vi.fn(() => true));
  });

  afterEach(() => {
    currentCourse.value = null;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('starts with existing homework, localized lifecycle summaries, and teacher task routes', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([draft, published, closed, scored]));
    mockStatistics({
      2: statistics({ homeworkId: 2, submittedCount: 18, unsubmittedCount: 2, reviewedCount: 12 }),
      3: statistics({ homeworkId: 3, submittedCount: 20, unsubmittedCount: 0, reviewedCount: 20 }),
      4: statistics({ homeworkId: 4, submittedCount: 19, unsubmittedCount: 1, reviewedCount: 19 })
    });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-testid="homework-teacher-index"]').exists()).toBe(true);
    expect(wrapper.get('h1').text()).toBe('作业管理');
    expect(wrapper.text()).toContain('软件工程实践');
    expect(wrapper.text()).toContain('软件需求草稿');
    expect(wrapper.text()).toContain('草稿');
    expect(wrapper.text()).toContain('已发布');
    expect(wrapper.text()).toContain('已关闭');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).toContain('已完成批阅');
    expect(wrapper.get('[data-testid="summary-strip"]').text()).toContain('51');
    expect(wrapper.text()).toContain('12 份已完成批阅');
    expect(wrapper.text()).not.toContain('未完成批阅');
    expect(wrapper.text()).not.toContain('待批阅');
    expect(wrapper.text()).not.toContain('DRAFT');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.find('form[aria-label="作业创建与编辑"]').exists()).toBe(false);
    expect(homeworkApi.getHomeworkStatistics).toHaveBeenCalledTimes(3);

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toEqual(expect.arrayContaining([
      { name: 'homework-create', params: { courseId: 101 } },
      { name: 'homework-manage-detail', params: { courseId: 101, homeworkId: 2 } },
      { name: 'homework-edit', params: { courseId: 101, homeworkId: 1 } },
      { name: 'homework-submission-workspace', params: { courseId: 101, homeworkId: 2 } },
      { name: 'homework-statistics', params: { courseId: 101, homeworkId: 2 } }
    ]));
    expect(wrapper.get('[data-testid="delete-homework-1"]').text()).toContain('删除草稿');
    expect(wrapper.find('[data-testid="delete-homework-2"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="delete-homework-3"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="delete-homework-4"]').exists()).toBe(false);
  });

  it('keeps only the draft attention filter and scopes it explicitly to the current server page', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValue(page(
      [draft, published, closed],
      { total: 41 }
    ));
    mockStatistics({
      2: statistics({ homeworkId: 2, submittedCount: 18, reviewedCount: 12 }),
      3: statistics({ homeworkId: 3, submittedCount: 20, reviewedCount: 20 })
    });
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[name="keyword"]').setValue('数组');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('数组与循环');
    expect(wrapper.text()).not.toContain('数据库设计');
    expect(homeworkApi.listHomeworks).toHaveBeenLastCalledWith({
      courseId: 101,
      page: 1,
      size: 20,
      keyword: '数组'
    });

    await wrapper.get('[name="keyword"]').setValue('');
    await wrapper.get('[name="status"]').setValue('DRAFT');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('软件需求草稿');
    expect(wrapper.text()).not.toContain('数组与循环');
    expect(homeworkApi.listHomeworks).toHaveBeenLastCalledWith({
      courseId: 101,
      page: 1,
      size: 20,
      status: 'DRAFT'
    });

    await wrapper.get('[name="status"]').setValue('');
    await wrapper.get('[name="attention"]').setValue('draft');
    await wrapper.get('[data-testid="filter-bar"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[name="attention"]').element.closest('label')?.textContent).toContain('本页待处理');
    expect(wrapper.get('[name="attention"]').text()).toContain('本页待发布草稿');
    expect(wrapper.get('[name="attention"]').text()).not.toContain('批阅');
    expect(wrapper.get('[data-testid="attention-scope-note"]').text()).toContain('待发布草稿条件只细化当前页');
    expect(wrapper.text()).toContain('软件需求草稿');
    expect(wrapper.text()).not.toContain('数组与循环');
    expect(wrapper.text()).not.toContain('数据库设计');
    expect(wrapper.get('nav[aria-label="作业分页"]').text()).toContain('服务端筛选共 41 份');
    expect(wrapper.get('nav[aria-label="作业分页"]').text()).toContain('本页显示 1 份');
    expect(wrapper.get('[data-testid="summary-strip"]').text()).toContain('服务端匹配');
    expect(wrapper.get('[data-action="next-homework-page"]').attributes('disabled')).toBeUndefined();
    expect(homeworkApi.listHomeworks).toHaveBeenCalledTimes(4);
  });

  it('uses the paginated list contract so homework after the first page remains reachable', async () => {
    const firstPageItems = Array.from({ length: 20 }, (_, index) => homeworkSummary({
      id: index + 1,
      title: `作业 ${index + 1}`
    }));
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce(page(firstPageItems, { page: 1, size: 20, total: 101 }))
      .mockResolvedValueOnce(page([
        homeworkSummary({ id: 21, title: '作业 21' })
      ], { page: 2, size: 20, total: 101 }));
    const wrapper = mountView();
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenNthCalledWith(1, {
      courseId: 101,
      page: 1,
      size: 20
    });
    expect(wrapper.get('[data-testid="summary-strip"]').text()).toContain('101');

    await wrapper.get('[data-action="next-homework-page"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenLastCalledWith({
      courseId: 101,
      page: 2,
      size: 20
    });
    expect(wrapper.text()).toContain('作业 21');
    expect(wrapper.text()).toContain('第 2 / 6 页');
  });

  it('confirms lifecycle operations, prevents duplicate actions, and refreshes after success', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValue(page([draft, published, closed]));
    vi.mocked(homeworkApi.getHomeworkStatistics).mockResolvedValue(statistics());
    const publishPending = deferred<HomeworkDetail>();
    vi.mocked(homeworkApi.publishHomework).mockReturnValueOnce(publishPending.promise);
    vi.mocked(homeworkApi.closeHomework).mockResolvedValueOnce(homeworkDetail({ id: 2, status: 'CLOSED' }));
    vi.mocked(homeworkApi.publishHomeworkScores).mockResolvedValueOnce(homeworkDetail({ id: 3, status: 'SCORE_PUBLISHED' }));
    const wrapper = mountView();
    await flushPromises();

    const publishButton = wrapper.get('[data-testid="publish-homework-1"]');
    await publishButton.trigger('click');
    await publishButton.trigger('click');
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('软件需求草稿'));
    expect(homeworkApi.publishHomework).toHaveBeenCalledTimes(1);
    expect(publishButton.attributes('disabled')).toBeDefined();
    expect(publishButton.text()).toContain('处理中');

    publishPending.resolve(homeworkDetail({ id: 1, title: draft.title, status: 'PUBLISHED', type: 'TEXT' }));
    await flushPromises();
    expect(wrapper.get('[data-testid="operation-feedback"]').text()).toContain('发布成功');

    await wrapper.get('[data-testid="close-homework-2"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.closeHomework).toHaveBeenCalledWith(2);
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining('数组与循环'));

    await wrapper.get('[data-testid="release-homework-3"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.publishHomeworkScores).toHaveBeenCalledWith(3);
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining('数据库设计'));
    expect(wrapper.find('[data-testid="delete-homework-1"]').exists()).toBe(true);
  });

  it('keeps the homework row and action available after a lifecycle failure', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([draft]));
    vi.mocked(homeworkApi.publishHomework).mockRejectedValueOnce(new Error('题目分值合计与满分不一致'));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-testid="publish-homework-1"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="operation-error"]').text()).toContain('题目分值合计与满分不一致');
    expect(wrapper.text()).toContain('软件需求草稿');
    expect(wrapper.get('[data-testid="publish-homework-1"]').attributes('disabled')).toBeUndefined();
  });

  it('cancels draft deletion without sending a request', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([draft]));
    vi.mocked(confirm).mockReturnValueOnce(false);
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-testid="delete-homework-1"]').trigger('click');
    await flushPromises();

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('软件需求草稿'));
    expect(homeworkApi.deleteHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('软件需求草稿');
  });

  it('deduplicates draft deletion, disables lifecycle actions, and refreshes after success', async () => {
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce(page([draft]))
      .mockResolvedValueOnce(page([]));
    const deletePending = deferred<HomeworkDetail>();
    vi.mocked(homeworkApi.deleteHomework).mockReturnValueOnce(deletePending.promise);
    const wrapper = mountView();
    await flushPromises();

    const deleteButton = wrapper.get('[data-testid="delete-homework-1"]');
    const publishButton = wrapper.get('[data-testid="publish-homework-1"]');
    await deleteButton.trigger('click');
    await deleteButton.trigger('click');

    expect(homeworkApi.deleteHomework).toHaveBeenCalledTimes(1);
    expect(deleteButton.attributes('disabled')).toBeDefined();
    expect(deleteButton.text()).toContain('处理中');
    expect(publishButton.attributes('disabled')).toBeDefined();

    deletePending.resolve(homeworkDetail({ id: 1, title: draft.title, deleted: true }));
    await flushPromises();

    expect(wrapper.get('[data-testid="operation-feedback"]').text()).toContain('已删除');
    expect(wrapper.find('[data-testid="delete-homework-1"]').exists()).toBe(false);
    expect(homeworkApi.listHomeworks).toHaveBeenCalledTimes(2);
  });

  it('retains a draft after deletion failure and allows retry', async () => {
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce(page([draft]))
      .mockResolvedValueOnce(page([]));
    vi.mocked(homeworkApi.deleteHomework)
      .mockRejectedValueOnce(new Error('草稿状态已变化，请刷新后重试'))
      .mockResolvedValueOnce(homeworkDetail({ id: 1, title: draft.title, deleted: true }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-testid="delete-homework-1"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="operation-error"]').text()).toContain('草稿状态已变化');
    expect(wrapper.text()).toContain('软件需求草稿');
    expect(wrapper.get('[data-testid="delete-homework-1"]').attributes('disabled')).toBeUndefined();

    await wrapper.get('[data-testid="delete-homework-1"]').trigger('click');
    await flushPromises();
    expect(homeworkApi.deleteHomework).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="delete-homework-1"]').exists()).toBe(false);
  });

  it('falls back to the last valid page after deleting the final row', async () => {
    const firstPageItems = Array.from({ length: 20 }, (_, index) => homeworkSummary({
      id: index + 1,
      title: `作业 ${index + 1}`
    }));
    const finalDraft = homeworkSummary({ id: 21, title: '末页草稿' });
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce(page(firstPageItems, { page: 1, total: 21 }))
      .mockResolvedValueOnce(page([finalDraft], { page: 2, total: 21 }))
      .mockResolvedValueOnce(page([], { page: 2, total: 20 }))
      .mockResolvedValueOnce(page(firstPageItems, { page: 1, total: 20 }));
    vi.mocked(homeworkApi.deleteHomework).mockResolvedValueOnce(homeworkDetail({
      id: 21,
      title: finalDraft.title,
      deleted: true
    }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-action="next-homework-page"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="delete-homework-21"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworks).toHaveBeenNthCalledWith(4, {
      courseId: 101,
      page: 1,
      size: 20
    });
    expect(homeworkApi.deleteHomework).toHaveBeenCalledWith(21);
    expect(wrapper.get('nav[aria-label="作业分页"]').text()).toContain('第 1 / 1 页');
    expect(wrapper.find('[data-testid="delete-homework-21"]').exists()).toBe(false);
  });

  it('does not offer draft-only or released-only actions for a NOT_OPEN legacy state', async () => {
    const notOpen = homeworkSummary({ id: 5, title: '等待开放的作业', status: 'NOT_OPEN', type: 'TEXT' });
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([notOpen]));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('等待开放的作业');
    expect(wrapper.find('[data-testid="manage-homework-5"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="edit-homework-5"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="publish-homework-5"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="submissions-homework-5"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="statistics-homework-5"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="delete-homework-5"]').exists()).toBe(false);
    expect(homeworkApi.getHomeworkStatistics).not.toHaveBeenCalled();
  });

  it('does not offer draft deletion for an archived homework', async () => {
    const archived = homeworkSummary({ id: 7, title: '归档作业', status: 'ARCHIVED', type: 'TEXT' });
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([archived]));
    mockStatistics({ 7: statistics({ homeworkId: 7 }) });
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('归档作业');
    expect(wrapper.find('[data-testid="delete-homework-7"]').exists()).toBe(false);
  });

  it('keeps FILE drafts editable but blocks publication until issue 214 supplies the upload contract', async () => {
    const fileDraft = homeworkSummary({ id: 5, title: '课程报告附件', status: 'DRAFT', type: 'FILE' });
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([fileDraft]));
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('[data-testid="file-contract-blocked-5"]').text()).toContain('#214');
    expect(wrapper.find('[data-testid="publish-homework-5"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="edit-homework-5"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="delete-homework-5"]').exists()).toBe(true);
  });

  it('blocks direct publication of a legacy CODE draft that still enables unsupported sandbox languages', async () => {
    const legacyCodeDraft = homeworkSummary({ id: 6, title: '旧版 Java 作业', status: 'DRAFT', type: 'CODE' });
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce(page([legacyCodeDraft]));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail({
      id: 6,
      title: legacyCodeDraft.title,
      status: 'DRAFT',
      type: 'CODE',
      languageLimitJson: '["python","java"]'
    }));
    const wrapper = mountView();
    await flushPromises();

    await wrapper.get('[data-testid="publish-homework-6"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(6);
    expect(homeworkApi.publishHomework).not.toHaveBeenCalled();
    expect(confirm).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="operation-error"]').text()).toContain('Python');
    expect(wrapper.get('[data-testid="operation-error"]').text()).toContain('移除');
  });

  it('shows loading, recoverable failure, and an empty state with the create entry', async () => {
    const firstLoad = deferred<ReturnType<typeof page>>();
    vi.mocked(homeworkApi.listHomeworks)
      .mockReturnValueOnce(firstLoad.promise)
      .mockRejectedValueOnce(new Error('作业服务暂不可用'))
      .mockResolvedValueOnce(page([]));
    const wrapper = mountView();

    expect(wrapper.get('[data-state="loading"]').text()).toContain('正在加载作业');
    firstLoad.reject(new Error('网络连接失败'));
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('作业管理加载失败');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('作业服务暂不可用');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="data-table-empty"]').text()).toContain('创建第一份作业');
    expect(wrapper.find('[data-testid="create-homework"]').exists()).toBe(true);
  });
});

function mountView() {
  return mount(HomeworkTeacherView, {
    props: { courseId: 101 },
    global: { stubs: { RouterLink: RouterLinkStub } }
  });
}

function page(
  list: HomeworkSummary[],
  pagination: Partial<{ page: number; size: number; total: number }> = {}
) {
  return { list, page: 1, size: 20, total: list.length, ...pagination };
}

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 1,
    courseId: 101,
    title: '作业',
    description: '完成本周学习任务。',
    type: 'OBJECTIVE' as HomeworkType,
    status: 'DRAFT' as HomeworkStatus,
    deadline: '2026-08-25T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    ...overrides
  };
}

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    ...homeworkSummary(),
    chapterId: null,
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: null,
    createdAt: '2026-08-18T12:00:00',
    updatedAt: '2026-08-18T12:00:00',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function statistics(overrides: Partial<HomeworkStatistics> = {}): HomeworkStatistics {
  return {
    homeworkId: 2,
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

function mockStatistics(byHomework: Record<number, HomeworkStatistics>) {
  vi.mocked(homeworkApi.getHomeworkStatistics).mockImplementation(async (homeworkId) => {
    const value = byHomework[homeworkId];
    if (!value) throw new Error('统计不存在');
    return value;
  });
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
