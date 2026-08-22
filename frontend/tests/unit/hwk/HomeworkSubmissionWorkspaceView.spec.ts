import { readFileSync } from 'node:fs';
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkSubmissionWorkspaceView from '../../../src/views/hwk/HomeworkSubmissionWorkspaceView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';
import type {
  HomeworkDetail,
  HomeworkSubmissionSummary,
  PageResponse
} from '../../../src/types/hwk';
import type { LearningCourseProgressAggregate } from '../../../src/types/lrn';

vi.mock('../../../src/api/hwk/homeworks');
vi.mock('../../../src/api/lrn/learningProgress');

const STUDENT_REF_601 = '37090d82ef8c0fac';
const STUDENT_REF_602 = '37091082ef8c14c5';

describe('HomeworkSubmissionWorkspaceView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValue(homework());
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValue(submissionPage());
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValue(courseProgress());
  });

  it('loads the homework queue with resolved names, localized states, versions, and safe deep links', async () => {
    const { wrapper } = await mountView();

    expect(homeworkApi.getHomeworkDetail).toHaveBeenCalledWith(11);
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(11, { page: 1, size: 20 });
    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledWith(101);
    expect(wrapper.get('h1').text()).toContain('数据结构作业');
    expect(wrapper.text()).toContain('数据结构');
    expect(wrapper.text()).toContain('林晓');
    expect(wrapper.text()).toContain('周然');
    expect(wrapper.text()).toContain('版本 3');
    expect(wrapper.text()).toContain('当前有效提交');
    expect(wrapper.text()).toContain('历史版本');
    expect(wrapper.text()).toContain('待批阅');
    expect(wrapper.get('.summary-grid').text()).toContain('本页已完成批阅版本0个');
    expect(wrapper.get('.summary-grid').text()).not.toContain('未完成批阅');
    expect(wrapper.text()).not.toContain('601');
    expect(wrapper.text()).not.toContain('602');
    expect(wrapper.text()).not.toContain('NEED_REVIEW');

    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'));
    expect(links).toContainEqual({
      name: 'homework-manage-detail',
      params: { courseId: 101, homeworkId: 11 }
    });
    expect(links).toContainEqual({
      name: 'homework-submission-review',
      params: { courseId: 101, homeworkId: 11, submissionId: 301 }
    });
  });

  it('paginates and applies only supported status filters while preserving them in review links', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage())
      .mockResolvedValueOnce(submissionPage({
        page: 1,
        total: 21,
        list: [submission({ submissionId: 302, studentId: 602, submitStatus: 'LATE' })]
      }))
      .mockResolvedValueOnce(submissionPage({
        page: 2,
        total: 21,
        list: [submission({ submissionId: 303, studentId: 603, version: 1 })]
      }));
    const { wrapper, router } = await mountView();

    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[name="evaluationStatus"]').setValue('PENDING');
    await wrapper.get('[name="reviewStatus"]').setValue('UNREVIEWED');
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 20,
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      reviewStatus: 'UNREVIEWED'
    });
    expect(router.currentRoute.value.query).toEqual({
      submit: 'LATE',
      evaluation: 'PENDING',
      review: 'UNREVIEWED'
    });
    expect(wrapper.find('[name="studentKeyword"]').exists()).toBe(false);
    expect(wrapper.find('[name="studentId"]').exists()).toBe(false);

    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      page: 2,
      total: 21,
      list: [submission({ submissionId: 303, studentId: 603, version: 1 })]
    }));
    await wrapper.get('[data-action="next-page"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 2,
      size: 20,
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      reviewStatus: 'UNREVIEWED'
    });
    expect(router.currentRoute.value.query).toEqual({
      submit: 'LATE',
      evaluation: 'PENDING',
      review: 'UNREVIEWED',
      page: '2'
    });

    const reviewLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'homework-submission-review');
    expect(reviewLink?.props('to')).toEqual({
      name: 'homework-submission-review',
      params: { courseId: 101, homeworkId: 11, submissionId: 303 },
      query: {
        submit: 'LATE',
        evaluation: 'PENDING',
        review: 'UNREVIEWED',
        page: '2'
      }
    });
  });

  it('restores an attention queue deep link and preserves it through review without leaking roster ids', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      page: 2,
      total: 21,
      list: [submission({
        submissionId: 303,
        studentId: 999,
        evaluationStatus: 'RUNNING',
        version: 1
      })]
    }));
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('学生名单服务暂不可用'));

    const { wrapper, router } = await mountView(
      true,
      '/courses/101/homeworks/11/manage/submissions?attention=EVALUATION_PENDING&page=2'
    );

    expect((wrapper.get('[name="attention"]').element as HTMLSelectElement).value)
      .toBe('EVALUATION_PENDING');
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(11, {
      page: 2,
      size: 20,
      attention: 'EVALUATION_PENDING'
    });
    expect(router.currentRoute.value.query).toEqual({
      attention: 'EVALUATION_PENDING',
      page: '2'
    });
    expect(wrapper.get('.summary-grid').text()).toContain('当前筛选结果21个版本');
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('学生姓名暂不可用');
    expect(wrapper.text()).not.toContain('999');

    const reviewLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'homework-submission-review');
    expect(reviewLink?.props('to')).toEqual({
      name: 'homework-submission-review',
      params: { courseId: 101, homeworkId: 11, submissionId: 303 },
      query: { attention: 'EVALUATION_PENDING', page: '2' }
    });
  });

  it('adds user-selected attention filters to browser history and restores them on back', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockImplementation(async (_homeworkId, query) => (
      submissionPage({
        total: query?.attention ? 1 : 21,
        list: [submission({ evaluationStatus: query?.attention ? 'PENDING' : 'ACCEPTED' })]
      })
    ));
    const { wrapper, router } = await mountView();

    await wrapper.get('[name="attention"]').setValue('EVALUATION_PENDING');
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(router.currentRoute.value.query).toEqual({ attention: 'EVALUATION_PENDING' });
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 20,
      attention: 'EVALUATION_PENDING'
    });

    router.back();
    await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({}));
    await flushPromises();

    expect((wrapper.get('[name="attention"]').element as HTMLSelectElement).value).toBe('');
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, { page: 1, size: 20 });
  });

  it('filters by a roster identity while exposing only its name and opaque reference in the URL', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage())
      .mockResolvedValueOnce(submissionPage({
        total: 1,
        size: 100,
        list: [submission({ submissionId: 302, studentId: 602, submitStatus: 'LATE' })]
      }));
    const { wrapper, router } = await mountView();

    const nameFilter = wrapper.get('[name="studentName"]');
    const zhouRanOption = nameFilter.findAll('option').find((option) => option.text() === '周然');
    expect(zhouRanOption?.attributes('value')).toBe(STUDENT_REF_602);
    await nameFilter.setValue(STUDENT_REF_602);
    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '602',
      submitStatus: 'LATE'
    });
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_602,
      submit: 'LATE'
    });
    expect(router.currentRoute.value.query).not.toHaveProperty('studentId');
    expect(router.currentRoute.value.query).not.toHaveProperty('studentKeyword');
    expect(wrapper.find('[name="studentId"]').exists()).toBe(false);
    expect(wrapper.find('[name="studentKeyword"]').exists()).toBe(false);
    expect(wrapper.get('[name="studentName"]').text()).toContain('林晓');
    expect(wrapper.get('[name="studentName"]').text()).toContain('周然');
    expect(wrapper.get('[name="studentName"]').text()).not.toContain('601');
    expect(wrapper.get('[name="studentName"]').text()).not.toContain('602');

    const reviewLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'homework-submission-review');
    expect(routeTarget(reviewLink?.props('to')).query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_602,
      submit: 'LATE'
    });
  });

  it('collects every fuzzy candidate page, keeps only the exact student, and paginates the exact versions locally', async () => {
    const firstTwentyExact = Array.from({ length: 20 }, (_, index) => submission({
      submissionId: 400 + index,
      studentId: 602,
      submitStatus: 'LATE',
      version: index + 1
    }));
    const fuzzyWrongStudents = Array.from({ length: 80 }, (_, index) => submission({
      submissionId: 500 + index,
      studentId: 1602,
      submitStatus: 'LATE',
      version: 100 + index
    }));
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage())
      .mockResolvedValueOnce(submissionPage({
        page: 1,
        size: 100,
        total: 101,
        list: [...firstTwentyExact, ...fuzzyWrongStudents]
      }))
      .mockResolvedValueOnce(submissionPage({
        page: 2,
        size: 100,
        total: 101,
        list: [submission({
          submissionId: 420,
          studentId: 602,
          submitStatus: 'LATE',
          version: 21
        })]
      }));
    const { wrapper, router } = await mountView();

    await wrapper.get('[name="studentName"]').setValue(STUDENT_REF_602);
    await wrapper.get('[name="submitStatus"]').setValue('LATE');
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenNthCalledWith(2, 11, {
      page: 1,
      size: 100,
      studentKeyword: '602',
      submitStatus: 'LATE'
    });
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenNthCalledWith(3, 11, {
      page: 2,
      size: 100,
      studentKeyword: '602',
      submitStatus: 'LATE'
    });
    expect(wrapper.text()).toContain('全部提交21个版本');
    expect(wrapper.text()).toContain('第 1 / 2 页');
    expect(wrapper.findAllComponents(RouterLinkStub)
      .filter((link) => routeTarget(link.props('to')).name === 'homework-submission-review')).toHaveLength(20);
    expect(wrapper.text()).not.toContain('版本 100');

    await wrapper.get('[data-action="next-page"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledTimes(3);
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_602,
      submit: 'LATE',
      page: '2'
    });
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('版本 21');
    expect(wrapper.findAllComponents(RouterLinkStub)
      .filter((link) => routeTarget(link.props('to')).name === 'homework-submission-review')).toHaveLength(1);
  });

  it('restores the same student by stable opaque reference after same-name roster insertions', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress({
      studentCount: 3,
      students: [
        { studentId: 1, studentName: '周然', progressPercent: 90, status: 'IN_PROGRESS', updatedAt: null },
        { studentId: 60, studentName: '周然', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null },
        { studentId: 601, studentName: '周然', progressPercent: 70, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      total: 2,
      size: 100,
      list: [
        submission({ submissionId: 302, studentId: 601, version: 4 }),
        submission({ submissionId: 999, studentId: 1601, version: 99 })
      ]
    }));
    const { wrapper, router } = await mountView(
      true,
      `/courses/101/homeworks/11/manage/submissions?keyword=周然&studentRef=${STUDENT_REF_601}`
    );

    const nameFilter = wrapper.get('[name="studentName"]');
    expect(nameFilter.text()).toContain('周然（同名 1）');
    expect(nameFilter.text()).toContain('周然（同名 2）');
    expect(nameFilter.text()).toContain('周然（同名 3）');
    expect(nameFilter.text()).not.toContain('60');
    expect(nameFilter.text()).not.toContain('601');
    expect((nameFilter.element as HTMLSelectElement).value).toBe(STUDENT_REF_601);

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '601'
    });
    expect(router.currentRoute.value.query).toEqual({ keyword: '周然', studentRef: STUDENT_REF_601 });
    expect(router.currentRoute.value.query).not.toHaveProperty('studentId');
    expect(router.currentRoute.value.query).not.toHaveProperty('studentKeyword');
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('版本 4');
    expect(wrapper.get('[data-testid="queue-list"]').text()).not.toContain('版本 99');
    expect(wrapper.text()).toContain('全部提交1个版本');

    const reviewLink = wrapper.findAllComponents(RouterLinkStub)
      .find((link) => routeTarget(link.props('to')).name === 'homework-submission-review');
    expect(routeTarget(reviewLink?.props('to')).query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_601
    });
  });

  it('blocks an expired name filter instead of silently loading the unfiltered queue', async () => {
    const { wrapper, router } = await mountView(
      true,
      '/courses/101/homeworks/11/manage/submissions?keyword=周然&studentRef=ffffffffffffffff&review=UNREVIEWED'
    );

    expect(homeworkApi.listHomeworkSubmissions).not.toHaveBeenCalled();
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周然',
      studentRef: 'ffffffffffffffff',
      review: 'UNREVIEWED'
    });
    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('姓名筛选已过期或无法定位');
    expect(wrapper.get('[name="studentName"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="queue-error"]').text()).toContain('清除姓名筛选后重新选择学生');
    expect(wrapper.find('[data-testid="queue-list"]').exists()).toBe(false);
  });

  it('blocks an unresolved name filter when the roster fails, then restores it safely after retry', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockRejectedValueOnce(new Error('课程名单暂时不可用'))
      .mockResolvedValueOnce(courseProgress());
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValue(submissionPage({
      total: 1,
      size: 100,
      list: [submission({ studentId: 602, submitStatus: 'LATE', reviewStatus: 'UNREVIEWED' })]
    }));
    const { wrapper, router } = await mountView(
      true,
      '/courses/101/homeworks/11/manage/submissions?keyword=周然&submit=LATE'
    );

    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('课程名单暂时不可用');
    expect(wrapper.get('[data-testid="student-name-warning"]').text()).toContain('姓名筛选');
    expect(wrapper.get('[name="studentName"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[name="submitStatus"]').attributes('disabled')).toBeUndefined();
    expect((wrapper.get('[name="submitStatus"]').element as HTMLSelectElement).value).toBe('LATE');
    expect(homeworkApi.listHomeworkSubmissions).not.toHaveBeenCalled();
    expect(router.currentRoute.value.query).toEqual({ keyword: '周然', submit: 'LATE' });
    expect(wrapper.text()).toContain('当前姓名筛选无法验证');
    expect(wrapper.text()).not.toContain('601');
    expect(wrapper.text()).not.toContain('602');
    expect(wrapper.find('[data-testid="queue-list"]').exists()).toBe(false);

    await wrapper.get('[name="reviewStatus"]').setValue('UNREVIEWED');
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).not.toHaveBeenCalled();

    await wrapper.get('[data-action="retry-student-names"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.getTeacherLearningProgress).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="student-name-warning"]').exists()).toBe(false);
    expect(wrapper.get('[name="studentName"]').attributes('disabled')).toBeUndefined();
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledTimes(1);
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '602',
      submitStatus: 'LATE',
      reviewStatus: 'UNREVIEWED'
    });
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_602,
      submit: 'LATE',
      review: 'UNREVIEWED'
    });
    expect(wrapper.text()).toContain('林晓');
  });

  it('hides the roster synthetic fallback name because it contains the raw student identifier', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress({
      students: [
        { studentId: 601, studentName: '学生 601', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain('学生姓名暂不可用');
    expect(wrapper.text()).not.toContain('601');
  });

  it('hides a pure numeric roster fallback name just like the statistics page', async () => {
    vi.mocked(learningProgressApi.getTeacherLearningProgress).mockResolvedValueOnce(courseProgress({
      students: [
        { studentId: 601, studentName: '601', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain('学生姓名暂不可用');
    expect(wrapper.text()).not.toContain('601');
  });

  it('does not count NONE for text or file submissions as an evaluation still in progress', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      total: 1,
      list: [submission({ submitType: 'TEXT', evaluationStatus: 'NONE' })]
    }));
    const { wrapper } = await mountView();

    expect(wrapper.get('.summary-grid').text()).toContain('本页评测处理中0份');
  });

  it.each(['OBJECTIVE', 'CODE'] as const)(
    'counts NONE for %s submissions as evaluation pending',
    async (submitType) => {
      vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
        total: 1,
        list: [submission({ submitType, evaluationStatus: 'NONE' })]
      }));
      const { wrapper } = await mountView();

      expect(wrapper.get('.summary-grid').text()).toContain('本页评测处理中1份');
    }
  );

  it('invalidates an old queue response as soon as homework props switch', async () => {
    const oldQueue = deferred<PageResponse<HomeworkSubmissionSummary>>();
    const newHomework = deferred<HomeworkDetail>();
    const newRoster = deferred<LearningCourseProgressAggregate>();
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homework())
      .mockReturnValueOnce(newHomework.promise);
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockResolvedValueOnce(courseProgress())
      .mockReturnValueOnce(newRoster.promise);
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockReturnValueOnce(oldQueue.promise)
      .mockResolvedValueOnce(submissionPage({
        total: 1,
        list: [submission({ submissionId: 901, homeworkId: 12, studentId: 701, version: 1 })]
      }));
    const { wrapper } = await mountView(false);
    await flushPromises();
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(11, { page: 1, size: 20 });

    await wrapper.setProps({ courseId: 102, homeworkId: 12 });
    oldQueue.resolve(submissionPage({
      total: 1,
      list: [submission({ submissionId: 999, homeworkId: 11, version: 9 })]
    }));
    await flushPromises();

    expect(wrapper.find('[data-testid="queue-list"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('版本 9');

    newHomework.resolve(homework({ id: 12, courseId: 102, title: '新作业' }));
    newRoster.resolve(courseProgress({
      courseId: 102,
      courseName: '算法设计',
      studentCount: 1,
      students: [
        { studentId: 701, studentName: '新学生', progressPercent: 50, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(12, { page: 1, size: 20 });
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('新学生');
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('版本 1');
    expect(wrapper.text()).not.toContain('版本 9');
  });

  it('invalidates an in-flight fuzzy candidate page before it can write into switched homework', async () => {
    const oldCandidatePage = deferred<PageResponse<HomeworkSubmissionSummary>>();
    const newHomework = deferred<HomeworkDetail>();
    const newRoster = deferred<LearningCourseProgressAggregate>();
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockResolvedValueOnce(homework())
      .mockReturnValueOnce(newHomework.promise);
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockResolvedValueOnce(courseProgress())
      .mockReturnValueOnce(newRoster.promise);
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage())
      .mockResolvedValueOnce(submissionPage({
        page: 1,
        size: 100,
        total: 101,
        list: [submission({ submissionId: 801, studentId: 602, version: 1 })]
      }))
      .mockReturnValueOnce(oldCandidatePage.promise)
      .mockResolvedValueOnce(submissionPage({
        page: 1,
        size: 100,
        total: 1,
        list: [submission({ submissionId: 901, homeworkId: 12, studentId: 602, version: 2 })]
      }));
    const { wrapper } = await mountView();
    await wrapper.get('[name="studentName"]').setValue(STUDENT_REF_602);
    await wrapper.get('[data-action="filter-submissions"]').trigger('submit');
    await flushPromises();
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenNthCalledWith(3, 11, {
      page: 2,
      size: 100,
      studentKeyword: '602'
    });

    await wrapper.setProps({ courseId: 102, homeworkId: 12 });
    oldCandidatePage.resolve(submissionPage({
      page: 2,
      size: 100,
      total: 101,
      list: [submission({ submissionId: 999, homeworkId: 11, studentId: 602, version: 99 })]
    }));
    await flushPromises();

    expect(wrapper.find('[data-testid="queue-list"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('版本 99');

    newHomework.resolve(homework({ id: 12, courseId: 102, title: '新作业' }));
    newRoster.resolve(courseProgress({
      courseId: 102,
      courseName: '算法设计',
      studentCount: 1,
      students: [
        { studentId: 602, studentName: '周然', progressPercent: 50, status: 'IN_PROGRESS', updatedAt: null }
      ]
    }));
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(12, {
      page: 1,
      size: 100,
      studentKeyword: '602'
    });
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('版本 2');
    expect(wrapper.text()).not.toContain('版本 99');
  });

  it('invalidates old homework and roster responses before they can start a queue for switched props', async () => {
    const oldHomework = deferred<HomeworkDetail>();
    const oldRoster = deferred<LearningCourseProgressAggregate>();
    vi.mocked(homeworkApi.getHomeworkDetail)
      .mockReturnValueOnce(oldHomework.promise)
      .mockResolvedValueOnce(homework({ id: 12, courseId: 102, title: '新作业' }));
    vi.mocked(learningProgressApi.getTeacherLearningProgress)
      .mockReturnValueOnce(oldRoster.promise)
      .mockResolvedValueOnce(courseProgress({
        courseId: 102,
        courseName: '算法设计',
        studentCount: 1,
        students: [
          { studentId: 701, studentName: '新学生', progressPercent: 50, status: 'IN_PROGRESS', updatedAt: null }
        ]
      }));
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockResolvedValueOnce(submissionPage({
      total: 1,
      list: [submission({ submissionId: 901, homeworkId: 12, studentId: 701, version: 1 })]
    }));
    const { wrapper } = await mountView(false);

    oldHomework.resolve(homework({ title: '旧作业' }));
    oldRoster.resolve(courseProgress({ courseName: '旧课程' }));
    await wrapper.setProps({ courseId: 102, homeworkId: 12 });
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledTimes(1);
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(12, { page: 1, size: 20 });
    expect(wrapper.get('h1').text()).toContain('新作业');
    expect(wrapper.text()).toContain('算法设计');
    expect(wrapper.text()).toContain('新学生');
    expect(wrapper.text()).not.toContain('旧作业');
    expect(wrapper.text()).not.toContain('旧课程');
  });

  it('recovers an out-of-range deep-linked page to the last real page', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockResolvedValueOnce(submissionPage({ page: 999, total: 21, list: [] }))
      .mockResolvedValueOnce(submissionPage({
        page: 2,
        total: 21,
        list: [submission({ submissionId: 303, studentId: 603, version: 1 })]
      }));
    const { wrapper, router } = await mountView(true, '/courses/101/homeworks/11/manage/submissions?page=999');

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenNthCalledWith(1, 11, { page: 999, size: 20 });
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, { page: 2, size: 20 });
    expect(router.currentRoute.value.query).toEqual({ page: '2' });
    expect(wrapper.get('[data-testid="queue-list"]').text()).toContain('程一');
    expect(wrapper.text()).toContain('第 2 / 2 页');
  });

  it('restores the safe student name and status filters on refresh and browser back navigation', async () => {
    vi.mocked(homeworkApi.listHomeworkSubmissions).mockImplementation(async (_homeworkId, query) => {
      const studentId = query?.studentKeyword === '602' ? 602 : 601;
      return submissionPage({
        page: 1,
        size: 100,
        total: 1,
        list: [submission({ studentId })]
      });
    });
    const { wrapper, router } = await mountView(
      true,
      `/courses/101/homeworks/11/manage/submissions?keyword=周然&studentRef=${STUDENT_REF_602}&review=REVIEWED&studentId=602&studentKeyword=602`
    );

    expect((wrapper.get('[name="studentName"]').element as HTMLSelectElement).value).toBe(STUDENT_REF_602);
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '602',
      reviewStatus: 'REVIEWED'
    });
    expect(router.currentRoute.value.query).toEqual({
      keyword: '周然',
      studentRef: STUDENT_REF_602,
      review: 'REVIEWED'
    });

    await router.push({ query: { keyword: '林晓', studentRef: STUDENT_REF_601, submit: 'LATE' } });
    await flushPromises();

    expect((wrapper.get('[name="studentName"]').element as HTMLSelectElement).value).toBe(STUDENT_REF_601);
    expect((wrapper.get('[name="submitStatus"]').element as HTMLSelectElement).value).toBe('LATE');
    expect((wrapper.get('[name="reviewStatus"]').element as HTMLSelectElement).value).toBe('');
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '601',
      submitStatus: 'LATE'
    });

    router.back();
    await vi.waitFor(() => {
      expect(router.currentRoute.value.query).toEqual({
        keyword: '周然',
        studentRef: STUDENT_REF_602,
        review: 'REVIEWED'
      });
    });
    await flushPromises();

    expect((wrapper.get('[name="studentName"]').element as HTMLSelectElement).value).toBe(STUDENT_REF_602);
    expect((wrapper.get('[name="reviewStatus"]').element as HTMLSelectElement).value).toBe('REVIEWED');
    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenLastCalledWith(11, {
      page: 1,
      size: 100,
      studentKeyword: '602',
      reviewStatus: 'REVIEWED'
    });
  });

  it('shows loading, retryable queue errors, and an explicit empty state', async () => {
    const request = deferred<PageResponse<HomeworkSubmissionSummary>>();
    vi.mocked(homeworkApi.listHomeworkSubmissions)
      .mockReturnValueOnce(request.promise)
      .mockResolvedValueOnce(submissionPage({ list: [], total: 0 }));
    const mounting = mountView(false);
    const { wrapper } = await mounting;

    expect(wrapper.get('[data-testid="queue-loading"]').text()).toContain('正在加载提交队列');
    request.reject(new Error('提交服务暂时不可用'));
    await flushPromises();

    expect(wrapper.get('[data-testid="queue-error"]').text()).toContain('提交服务暂时不可用');
    await wrapper.get('[data-action="retry-submissions"]').trigger('click');
    await flushPromises();

    expect(homeworkApi.listHomeworkSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="queue-empty"]').text()).toContain('暂无符合条件的提交');
  });

  it('rejects homework context mismatches without rendering submission data', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homework({ courseId: 999 }));
    const { wrapper } = await mountView();

    expect(wrapper.get('[data-testid="workspace-fatal-error"]').text()).toContain('作业与当前课程不匹配');
    expect(wrapper.find('[data-testid="queue-list"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('林晓');
  });

  it('keeps a single-column submission list at phone width', () => {
    const source = readFileSync('src/views/hwk/HomeworkSubmissionWorkspaceView.vue', 'utf8');

    expect(source).toMatch(/\.homework-submission-workspace\s*\{[\s\S]*?width:\s*100%/);
    expect(source).toMatch(/@media\s*\(max-width:\s*760px\)/);
    expect(source).toMatch(
      /@media\s*\(max-width:\s*760px\)[\s\S]*?\.submission-list\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/
    );
  });
});

async function mountView(
  waitForRequests = true,
  initialLocation = '/courses/101/homeworks/11/manage/submissions'
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/courses/:courseId/homeworks/:homeworkId/manage/submissions',
        name: 'homework-submission-workspace',
        component: { template: '<div />' }
      }
    ]
  });
  await router.push(initialLocation);
  await router.isReady();
  const wrapper = mount(HomeworkSubmissionWorkspaceView, {
    props: { courseId: 101, homeworkId: 11 },
    global: {
      plugins: [router],
      stubs: { RouterLink: RouterLinkStub }
    }
  });
  if (waitForRequests) {
    await flushPromises();
  }
  return { wrapper, router };
}

function homework(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 11,
    courseId: 101,
    chapterId: 2,
    judgeConfigId: 7,
    title: '数据结构作业',
    description: '完成队列实现。',
    type: 'CODE',
    status: 'PUBLISHED',
    totalScore: 100,
    deadline: '2026-08-20T23:59:00',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    createdBy: 9,
    publishedAt: '2026-08-18T10:00:00',
    createdAt: '2026-08-17T10:00:00',
    updatedAt: '2026-08-18T10:00:00',
    languageLimitJson: '["java"]',
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    outputCompareMode: 'TRIM',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function submission(overrides: Partial<HomeworkSubmissionSummary> = {}): HomeworkSubmissionSummary {
  return {
    submissionId: 301,
    homeworkId: 11,
    studentId: 601,
    submitType: 'CODE',
    answerText: 'public class Main {}',
    answerJson: null,
    fileUrl: null,
    language: 'java',
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'ACCEPTED',
    reviewStatus: 'NEED_REVIEW',
    autoScore: 86,
    manualScore: null,
    finalScore: null,
    comment: null,
    version: 3,
    final: true,
    submittedAt: '2026-08-20T10:30:00',
    ...overrides
  };
}

function submissionPage(
  overrides: Partial<PageResponse<HomeworkSubmissionSummary>> = {}
): PageResponse<HomeworkSubmissionSummary> {
  return {
    list: [
      submission(),
      submission({
        submissionId: 302,
        studentId: 602,
        version: 2,
        final: false,
        submitStatus: 'LATE',
        evaluationStatus: 'PENDING',
        reviewStatus: 'UNREVIEWED'
      })
    ],
    total: 21,
    page: 1,
    size: 20,
    ...overrides
  };
}

function courseProgress(
  overrides: Partial<LearningCourseProgressAggregate> = {}
): LearningCourseProgressAggregate {
  return {
    courseId: 101,
    courseName: '数据结构',
    studentCount: 3,
    averageProgressPercent: 68,
    students: [
      { studentId: 601, studentName: '林晓', progressPercent: 80, status: 'IN_PROGRESS', updatedAt: null },
      { studentId: 602, studentName: '周然', progressPercent: 70, status: 'IN_PROGRESS', updatedAt: null },
      { studentId: 603, studentName: '程一', progressPercent: 40, status: 'IN_PROGRESS', updatedAt: null }
    ],
    ...overrides
  };
}

function routeTarget(target: string | Record<string, unknown> | undefined) {
  return typeof target === 'object' && target !== null ? target : {};
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
