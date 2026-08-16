import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routerKey, type Router } from 'vue-router';
import CourseManagementView from '../../src/views/crs/CourseManagementView.vue';

const longDescription = '课程创建与管理主流程，覆盖教师建课、信息维护、学生加入、教学资源组织与后续课程运维需求，保证课程展示卡片不会因为简介过长而影响排版，同时为后续加入课程、成员管理、公告发布、成绩联动等操作预留足够清晰的展示空间。';

const course = {
  id: 1,
  name: '软件工程基础',
  description: longDescription,
  teacherId: 101,
  teacherName: '教师101',
  semester: '2026春',
  category: '软件工程',
  coverUrl: '',
  enrollmentMode: 'PUBLIC',
  inviteCode: '',
  maxStudents: 60,
  startDate: '2026-03-01',
  endDate: '2026-07-01',
  status: 'ACTIVE',
  memberCount: 1,
  member: true,
  manageable: true,
  createdAt: '2026-05-25T00:00:00',
  updatedAt: '2026-05-25T00:00:00'
};

const homeSummary = (targetCourse = course, announcements: unknown[] = [], recentTasks: unknown[] = []) => ({
  code: '0',
  message: 'success',
  data: {
    course: targetCourse,
    announcements,
    recentTasks
  }
});

describe('CourseManagementView', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    window.localStorage.setItem('onlinejudge.userId', '101');
    window.localStorage.setItem('onlinejudge.userRole', 'TEACHER');
    window.localStorage.setItem('onlinejudge.username', 'Teacher101');
    window.history.replaceState({}, '', '/courses');
  });

  it('truncates long descriptions and enters the course home page from the all courses view', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.get('.card-desc').text().length).toBeLessThan(longDescription.length);
    const learningTaskLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('学习任务'));
    expect(learningTaskLink).toBeUndefined();
    const learningProgressLink = wrapper.findAll('.navbar-menu a').find((link) => link.attributes('href') === '/learning/progress');
    expect(learningProgressLink).toBeUndefined();
    const gradeLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('成绩分析'));
    expect(gradeLink).toBeUndefined();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(window.location.pathname).toBe('/courses/1');
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false);
    expect(wrapper.find('.course-modal').exists()).toBe(false);
    expect(wrapper.find('[data-testid="course-detail-page"]').exists()).toBe(true);
    expect(wrapper.find('.course-home').exists()).toBe(true);
    expect(wrapper.text()).toContain('课程详情');
    expect(wrapper.text()).toContain(longDescription);
    expect(wrapper.text()).toContain('操作区');
    expect(wrapper.text()).toContain('暂无章节目录');
  });

  it('delegates course entry to Vue Router when mounted in the routed application', async () => {
    const page = {
      code: '0',
      message: 'success',
      data: { list: [course], total: 1, page: 1, size: 20 }
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => page }));
    const push = vi.fn().mockResolvedValue(undefined);

    const wrapper = mount(CourseManagementView, {
      global: {
        provide: {
          [routerKey as symbol]: { push } as unknown as Router
        }
      }
    });
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(push).toHaveBeenCalledWith('/courses/1');
  });

  it('opens chapter, resource, and announcement management from the teacher detail action area', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/home-summary')) {
        return { ok: true, json: async () => homeSummary(course) };
      }
      if (url.includes('/chapters') || url.includes('/resources') || url.includes('/announcements') || url.includes('/members')) {
        return { ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) };
      }
      return { ok: true, json: async () => page([course], 1) };
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    async function openFromDetail(label: string) {
      window.history.replaceState({}, '', '/courses');
      window.dispatchEvent(new Event('onlinejudge:navigation'));
      await flushPromises();
      await wrapper.get('.course-card').trigger('click');
      await flushPromises();
      const button = wrapper.findAll('.modal-actions-placeholder button').find((item) => item.text().includes(label));
      expect(button).toBeTruthy();
      await button!.trigger('click');
      await flushPromises();
    }

    await openFromDetail('管理章节');
    expect(wrapper.text()).toContain('创建章节');
    await wrapper.findAll('button').find((item) => item.text().includes('返回课程'))!.trigger('click');
    await flushPromises();

    await openFromDetail('管理资源');
    expect(wrapper.text()).toContain('上传资源');
    await wrapper.findAll('button').find((item) => item.text().includes('返回课程'))!.trigger('click');
    await flushPromises();

    await openFromDetail('管理公告');
    expect(wrapper.text()).toContain('发布公告');
  });

  it('shows course announcements in the detail sidebar and lets teachers publish one', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const announcements = [{
      id: 71,
      courseId: 1,
      title: 'Pinned notice',
      content: 'Read chapter 1 before class.',
      top: true,
      publisherId: 101,
      publisherName: 'Teacher101',
      createdAt: '2026-06-02T09:00:00',
      updatedAt: '2026-06-02T09:00:00'
    }];
    const createdAnnouncement = {
      ...announcements[0],
      id: 72,
      title: 'Lab reminder',
      content: 'Bring your laptop.',
      top: false
    };
    const recentTasks = [{
      taskId: 501,
      taskType: 'HOMEWORK',
      title: 'Submit homework 1',
      courseId: 1,
      courseName: course.name,
      deadline: '2026-06-10 23:59:00',
      progress: 20,
      status: 'IN_PROGRESS',
      actionUrl: '/courses/1/homeworks/501'
    }];
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course, announcements, recentTasks) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: announcements }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: createdAnnouncement }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [createdAnnouncement, ...announcements] }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    const sidebar = wrapper.get('[data-testid="course-announcement-sidebar"]');
    expect(sidebar.text()).toContain('Pinned notice');
    expect(sidebar.text()).toContain('Read chapter 1 before class.');
    expect(wrapper.get('[data-testid="course-recent-tasks"]').text()).toContain('Submit homework 1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/home-summary', expect.objectContaining({ method: 'GET' }));

    await sidebar.find('button').trigger('click');
    await flushPromises();

    await wrapper.get('[data-testid="announcement-title"]').setValue('Lab reminder');
    await wrapper.get('[data-testid="announcement-content"]').setValue('Bring your laptop.');
    await wrapper.get('[data-testid="announcement-form"]').trigger('submit.prevent');
    await flushPromises();

    const createCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/announcements' && options.method === 'POST');
    expect(createCall).toBeTruthy();
    expect(JSON.parse(createCall![1].body)).toEqual({
      title: 'Lab reminder',
      content: 'Bring your laptop.',
      isTop: false
    });
    expect(wrapper.text()).toContain('公告发布成功');
  });

  it('keeps the original detail modal for students who have not joined a course', async () => {
    const publicCourse = {
      ...course,
      id: 81,
      member: false,
      manageable: false
    };
    const page = (list = [publicCourse], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([publicCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(window.location.pathname).toBe('/courses/81');
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false);
    expect(wrapper.find('.course-modal').exists()).toBe(false);
    expect(wrapper.find('.course-home').exists()).toBe(true);
    expect(wrapper.find('.course-home').classes()).not.toContain('course-home-expanded');
    expect(wrapper.find('[data-testid="course-announcement-sidebar"]').exists()).toBe(false);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/chapters'))).toBe(false);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/resources'))).toBe(false);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/announcements'))).toBe(false);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/home-summary'))).toBe(false);
  });

  it('keeps LRN secondary entries out of the course sidebar', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) }));

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.find('a[data-testid="learning-progress-entry"]').exists()).toBe(false);
    expect(wrapper.find('a[data-testid="learning-statistics-entry"]').exists()).toBe(false);
    expect(wrapper.find('a[data-testid="learning-reminders-entry"]').exists()).toBe(false);
    const learningTaskLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('学习任务'));
    expect(learningTaskLink).toBeUndefined();
  });

  it('loads the mine course scope from the all courses sidebar entry', async () => {
    const mineCourse = { ...course, id: 9, name: '我的课程示例' };
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([mineCourse], 1) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    const mineButton = wrapper.findAll('.menu-button').find((button) => button.text().includes('我的课程'));
    expect(mineButton).toBeTruthy();
    await mineButton!.trigger('click');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses?page=1&size=20&scope=mine', expect.objectContaining({ method: 'GET' }));
    expect(wrapper.text()).toContain('我的课程示例');
  });

  it('uses different layouts for all courses and managed courses, then creates a course', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const newCourse = { ...course, id: 2, name: '数据结构', category: '计算机基础' };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: newCourse }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([newCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([newCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([newCourse], 1) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.text()).toContain('软件工程基础');
    expect(wrapper.text()).toContain('师生共用课程列表');
    expect(wrapper.find('input[placeholder="例如：软件工程基础"]').exists()).toBe(false);

    const managedButton = wrapper.findAll('.menu-button')[2];
    await managedButton.trigger('click');
    await flushPromises();

    await wrapper.find('input[placeholder="例如：软件工程基础"]').setValue('数据结构');
    await wrapper.find('input[placeholder="2026春"]').setValue('2026春');
    await wrapper.find('input[placeholder="软件工程"]').setValue('计算机基础');
    const dateInputs = wrapper.findAll('input[type="date"]');
    await dateInputs[0].setValue('2026-03-01');
    await dateInputs[1].setValue('2026-07-01');
    await wrapper.find('textarea').setValue('面向软件工程专业的数据结构课程');
    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses', expect.objectContaining({ method: 'POST' }));
    expect(wrapper.text()).toContain('课程创建成功');
    expect(wrapper.text()).toContain('数据结构');
    expect(wrapper.text()).toContain('已归档');
  });

  it('opens chapter management from a manageable course and saves a nested chapter', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const chapters = [
      {
        id: 11,
        courseId: 1,
        parentId: null,
        chapterName: '课程导论',
        objective: '目标与安排',
        sortOrder: 1,
        visibleStatus: 1,
        chapterType: 1,
        children: [],
        createdAt: '2026-05-25T00:00:00',
        updatedAt: '2026-05-25T00:00:00'
      }
    ];
    const nested = { ...chapters[0], id: 12, parentId: 11, chapterName: '开发环境', objective: '安装 JDK 与 IDE' };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: chapters }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: nested }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [{ ...chapters[0], children: [nested] }] }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.findAll('.menu-button')[2].trigger('click');
    await flushPromises();

    const chapterButton = wrapper.findAll('button').find((button) => button.text().includes('章节'));
    expect(chapterButton).toBeTruthy();
    await chapterButton!.trigger('click');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/chapters', expect.objectContaining({ method: 'GET' }));
    expect(wrapper.text()).toContain('课程导论');

    await wrapper.find('[data-testid="chapter-title"]').setValue('开发环境');
    await wrapper.find('[data-testid="chapter-parent"]').setValue('11');
    await wrapper.find('[data-testid="chapter-content"]').setValue('安装 JDK 与 IDE');
    await wrapper.find('[data-testid="chapter-form"]').trigger('submit.prevent');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/chapters', expect.objectContaining({ method: 'POST' }));
    expect(wrapper.text()).toContain('开发环境');
  });

  it('reorders same-level chapters by drag and drop', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const firstChapter = {
      id: 11,
      courseId: 1,
      parentId: null,
      chapterName: '课程导论',
      objective: '目标与安排',
      sortOrder: 1,
      visibleStatus: 1,
      chapterType: 1,
      children: [],
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    };
    const secondChapter = { ...firstChapter, id: 12, chapterName: '实践准备', sortOrder: 2 };
    const reordered = [
      { ...secondChapter, sortOrder: 1 },
      { ...firstChapter, sortOrder: 2 }
    ];
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [firstChapter, secondChapter] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...secondChapter, sortOrder: 1 } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: reordered }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.findAll('.menu-button')[2].trigger('click');
    await flushPromises();

    const chapterButton = wrapper.findAll('button').find((button) => button.text().includes('章节'));
    await chapterButton!.trigger('click');
    await flushPromises();

    const rows = wrapper.findAll('.chapter-row');
    const dataTransfer = {
      effectAllowed: '',
      dropEffect: '',
      setData: vi.fn(),
      getData: vi.fn()
    };
    await rows[1].trigger('dragstart', { dataTransfer });
    await rows[0].trigger('dragover', { dataTransfer });
    await rows[0].trigger('drop', { dataTransfer });
    await flushPromises();

    const updateCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/chapters/12' && options.method === 'PUT');
    expect(updateCall).toBeTruthy();
    expect(JSON.parse(updateCall![1].body)).toEqual(expect.objectContaining({ sortOrder: 1, chapterName: '实践准备' }));
    expect(wrapper.text()).toContain('实践准备');
  });

  it('downloads resources from the rendered action through bearer-authenticated fetch', async () => {
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const resources = [{
      id: 21,
      courseId: 1,
      chapterId: 11,
      name: 'Lesson PDF',
      resourceType: 'DOCUMENT',
      visibility: 'STUDENT',
      publishAt: null,
      originalFilename: 'lesson.pdf',
      contentType: 'application/pdf',
      fileSize: 15,
      uploadUserId: 101,
      downloadUrl: '/api/v1/courses/1/resources/21/download',
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }];
    const fileBlob = new Blob(['course material'], { type: 'application/pdf' });
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:lesson'),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: resources }) })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'Content-Disposition': "attachment; filename*=UTF-8''lesson.pdf" }),
        blob: async () => fileBlob
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: '0', message: 'success', data: {
          progressId: 1,
          courseId: 1,
          courseName: '软件工程基础',
          chapterId: 11,
          chapterName: '课程导论',
          sourceModule: 'CRS',
          sourceId: 21,
          progressPercent: 100,
          lastPosition: 'resourceId=21',
          status: 'COMPLETED',
          continueUrl: '/courses/1?chapterId=11&resourceId=21',
          updatedAt: '2026-06-01 10:00:00'
        } })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: '0', message: 'success', data: {
          id: 1,
          courseId: 1,
          courseName: '软件工程基础',
          sourceModule: 'CRS',
          sourceId: 21,
          actionType: 'DOWNLOAD',
          durationSeconds: 0,
          startedAt: '2026-06-01 10:00:00',
          endedAt: '2026-06-01 10:00:00'
        } })
      });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.findAll('.menu-button')[2].trigger('click');
    await flushPromises();

    const resourceButton = wrapper.findAll('button').find((button) => button.text().includes('资源'));
    expect(resourceButton).toBeTruthy();
    await resourceButton!.trigger('click');
    await flushPromises();

    const downloadButton = wrapper.findAll('button').find((button) => button.text().includes('下载'));
    expect(downloadButton).toBeTruthy();
    await downloadButton!.trigger('click');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/resources/21/download', expect.objectContaining({
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer teacher-token'
      }
    }));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/learning/progress', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        courseId: 1,
        chapterId: 11,
        sourceModule: 'CRS',
        sourceId: 21,
        progressPercent: 100,
        lastPosition: 'resourceId=21'
      })
    }));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/learning/records', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        courseId: 1,
        sourceModule: 'CRS',
        sourceId: 21,
        actionType: 'DOWNLOAD',
        durationSeconds: 0
      })
    }));
  });

  it('shows unbound resources in the all resources filter for students and lets them download', async () => {
    window.localStorage.setItem('onlinejudge.authToken', 'student-token');
    window.localStorage.setItem('onlinejudge.userId', '201');
    window.localStorage.setItem('onlinejudge.userRole', 'STUDENT');
    window.localStorage.setItem('onlinejudge.username', 'Student201');
    const studentCourse = {
      ...course,
      member: true,
      manageable: false
    };
    const page = (list = [studentCourse], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const chapters = [{
      id: 11,
      courseId: 1,
      parentId: null,
      chapterName: '课程导论',
      sortOrder: 1,
      objective: '',
      visibleStatus: 1,
      chapterType: 1,
      children: [],
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }];
    const resources = [{
      id: 31,
      courseId: 1,
      chapterId: null,
      name: 'Course Syllabus',
      resourceType: 'DOCUMENT',
      visibility: 'STUDENT',
      publishAt: null,
      originalFilename: 'syllabus.pdf',
      contentType: 'application/pdf',
      fileSize: 18,
      uploadUserId: 101,
      downloadUrl: '/api/v1/courses/1/resources/31/download',
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }, {
      id: 32,
      courseId: 1,
      chapterId: 11,
      name: 'Chapter Lesson',
      resourceType: 'DOCUMENT',
      visibility: 'STUDENT',
      publishAt: null,
      originalFilename: 'chapter.pdf',
      contentType: 'application/pdf',
      fileSize: 22,
      uploadUserId: 101,
      downloadUrl: '/api/v1/courses/1/resources/32/download',
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }];
    const fileBlob = new Blob(['course syllabus'], { type: 'application/pdf' });
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:syllabus'),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([studentCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([studentCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(studentCourse) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: chapters }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: resources }) })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'Content-Disposition': "attachment; filename*=UTF-8''syllabus.pdf" }),
        blob: async () => fileBlob
      })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: {} }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: {} }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    const resourceFilter = wrapper.find('.detail-block .resource-filter select');
    expect((resourceFilter.element as HTMLSelectElement).value).toBe('all');
    expect(wrapper.text()).toContain('Course Syllabus');
    expect(wrapper.text()).toContain('未绑定章节');
    expect(wrapper.text()).toContain('Chapter Lesson');

    await resourceFilter.setValue('11');
    await flushPromises();
    expect(wrapper.text()).not.toContain('Course Syllabus');
    expect(wrapper.text()).toContain('Chapter Lesson');

    await resourceFilter.setValue('unbound');
    await flushPromises();
    expect(wrapper.text()).toContain('Course Syllabus');
    expect(wrapper.text()).not.toContain('Chapter Lesson');

    await resourceFilter.setValue('all');
    await flushPromises();
    const unboundRow = wrapper.findAll('.resource-row').find((row) => row.text().includes('Course Syllabus'));
    expect(unboundRow).toBeTruthy();
    await unboundRow!.find('button').trigger('click');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/resources/31/download', expect.objectContaining({
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer student-token'
      }
    }));
  });

  it('restores the course chapter context from the resume query', async () => {
    window.history.replaceState({}, '', '/courses/1?chapterId=11&resourceId=21&resume=resourceId%3D21');
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const chapters = [{
      id: 11,
      courseId: 1,
      parentId: null,
      chapterName: '课程导论',
      sortOrder: 1,
      objective: '',
      visibleStatus: 1,
      chapterType: 1,
      children: [],
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }];
    const resources = [{
      id: 21,
      courseId: 1,
      chapterId: 11,
      name: 'Lesson PDF',
      resourceType: 'DOCUMENT',
      visibility: 'STUDENT',
      publishAt: null,
      originalFilename: 'lesson.pdf',
      contentType: 'application/pdf',
      fileSize: 15,
      uploadUserId: 101,
      downloadUrl: '/api/v1/courses/1/resources/21/download',
      createdAt: '2026-05-25T00:00:00',
      updatedAt: '2026-05-25T00:00:00'
    }];
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: course }) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: chapters }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: resources }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) }));

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect((wrapper.get('.resource-filter select').element as HTMLSelectElement).value).toBe('11');
    expect(wrapper.text()).toContain('已恢复上次学习位置');
    expect(wrapper.text()).toContain('Lesson PDF');
  });

  it('sends the entered invite code when a student joins an invite course', async () => {
    const inviteCourse = {
      ...course,
      id: 31,
      enrollmentMode: 'INVITE',
      inviteCode: undefined,
      member: false,
      manageable: false,
      memberCount: 1
    };
    const joinedCourse = {
      ...inviteCourse,
      member: true,
      memberCount: 2
    };
    const page = (list = [inviteCourse], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    vi.spyOn(window, 'prompt').mockReturnValue('JOIN-31');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([inviteCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([inviteCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: '0',
          message: 'success',
          data: { courseId: 31, userId: 201, member: true, teacher: false, role: 'STUDENT', status: 'ACTIVE' }
        })
      })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: joinedCourse }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    const joinButton = wrapper.findAll('button.card-btn').find((button) => button.text().includes('输入邀请码'));
    expect(joinButton).toBeTruthy();
    await joinButton!.trigger('click');
    await flushPromises();

    const joinCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/31/join' && options.method === 'POST');
    expect(joinCall).toBeTruthy();
    expect(JSON.parse(joinCall![1].body)).toEqual({ inviteCode: 'JOIN-31' });
  });

  it('shows a pending success notice and refreshes the list for review-mode joins', async () => {
    const reviewCourse = {
      ...course,
      id: 41,
      enrollmentMode: 'REVIEW',
      member: false,
      manageable: false,
      memberCount: 1
    };
    const page = (list = [reviewCourse], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([reviewCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([reviewCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: '0',
          message: 'success',
          data: { courseId: 41, userId: 201, member: false, teacher: false, role: 'STUDENT', status: 'PENDING' }
        })
      })
      .mockResolvedValueOnce({ ok: true, json: async () => page([reviewCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([reviewCourse], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    const joinButton = wrapper.findAll('button.card-btn').find((button) => button.text().includes('加入'));
    expect(joinButton).toBeTruthy();
    await joinButton!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('申请已提交');
    const refreshedAllCourses = fetchMock.mock.calls.filter(([url, init]) =>
      url === '/api/v1/courses?page=1&size=20&scope=all' && init?.method === 'GET'
    );
    expect(refreshedAllCourses.length).toBeGreaterThan(1);
  });

  it('lets a teacher approve and reject pending course members', async () => {
    const pendingOne = {
      courseId: 1,
      userId: 501,
      role: 'STUDENT',
      status: 'PENDING',
      joinMethod: 'REVIEW',
      approvedBy: null,
      joinedAt: null
    };
    const pendingTwo = {
      ...pendingOne,
      userId: 502
    };
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [pendingOne, pendingTwo] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...pendingOne, status: 'ACTIVE', approvedBy: 101 } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [pendingTwo] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [{ ...pendingOne, status: 'ACTIVE', approvedBy: 101 }, pendingTwo] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...pendingTwo, status: 'REJECTED', approvedBy: 101 } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [{ ...pendingOne, status: 'ACTIVE', approvedBy: 101 }] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('学生 501');
    expect(wrapper.text()).toContain('学生 502');

    const approveButton = wrapper.findAll('button.card-btn').find((button) => button.text().includes('通过'));
    expect(approveButton).toBeTruthy();
    await approveButton!.trigger('click');
    await flushPromises();

    const approveCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/members/501' && options.method === 'PUT');
    expect(approveCall).toBeTruthy();
    expect(JSON.parse(approveCall![1].body)).toEqual({ role: 'STUDENT', status: 'ACTIVE' });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/members?status=PENDING', expect.objectContaining({ method: 'GET' }));
    expect(wrapper.text()).toContain('学生 501');
    expect(wrapper.text()).toContain('学生 502');

    const rejectButton = wrapper.findAll('button.card-btn').find((button) => button.text().includes('拒绝'));
    expect(rejectButton).toBeTruthy();
    await rejectButton!.trigger('click');
    await flushPromises();

    const rejectCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/members/502' && options.method === 'PUT');
    expect(rejectCall).toBeTruthy();
    expect(JSON.parse(rejectCall![1].body)).toEqual({ role: 'STUDENT', status: 'REJECTED' });
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/courses/1/members?status=PENDING')).toHaveLength(3);
    expect(wrapper.text()).toContain('暂无待审核申请');
  });

  it('lets a teacher change active member roles and remove course members', async () => {
    const teacherMember = {
      courseId: 1,
      userId: 101,
      role: 'TEACHER',
      status: 'ACTIVE',
      joinMethod: 'CREATED',
      approvedBy: 101,
      joinedAt: '2026-03-01T08:00:00'
    };
    const studentMember = {
      courseId: 1,
      userId: 601,
      role: 'STUDENT',
      status: 'ACTIVE',
      joinMethod: 'PUBLIC',
      approvedBy: null,
      joinedAt: '2026-03-02T08:00:00'
    };
    const assistantMember = { ...studentMember, role: 'ASSISTANT' };
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [teacherMember, studentMember] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: assistantMember }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [teacherMember, assistantMember] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: null }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [teacherMember] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, memberCount: 1 }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, memberCount: 1 }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, memberCount: 1 }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('601');

    const studentRoleSelect = wrapper.findAll('select').find((select) => select.element.value === 'STUDENT');
    expect(studentRoleSelect).toBeTruthy();
    await studentRoleSelect!.setValue('ASSISTANT');
    await flushPromises();

    const roleCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/members/601' && options.method === 'PUT');
    expect(roleCall).toBeTruthy();
    expect(JSON.parse(roleCall![1].body)).toEqual({ role: 'ASSISTANT', status: 'ACTIVE' });
    expect(wrapper.text()).toContain('601');

    const studentRow = wrapper.findAll('.resource-row').find((row) => row.text().includes('601'));
    expect(studentRow).toBeTruthy();
    const removeButton = studentRow!.find('button.card-btn.danger');
    expect(removeButton).toBeTruthy();
    await removeButton.trigger('click');
    await flushPromises();

    const removeCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/members/601' && options.method === 'DELETE');
    expect(removeCall).toBeTruthy();
  });

  it('allows teacher rows to request role changes and lets the backend enforce last-teacher rules', async () => {
    const teacherMember = {
      courseId: 1,
      userId: 101,
      role: 'TEACHER',
      status: 'ACTIVE',
      joinMethod: 'CREATED',
      approvedBy: 101,
      joinedAt: '2026-03-01T08:00:00'
    };
    const extraTeacherMember = {
      ...teacherMember,
      userId: 602
    };
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [teacherMember, extraTeacherMember] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...extraTeacherMember, role: 'ASSISTANT' } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [teacherMember, { ...extraTeacherMember, role: 'ASSISTANT' }] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    const teacherRow = wrapper.findAll('.resource-row').find((row) => row.text().includes('602'));
    expect(teacherRow).toBeTruthy();
    const teacherRoleSelect = teacherRow!.find('select');
    expect(teacherRoleSelect.attributes('disabled')).toBeUndefined();

    await teacherRoleSelect.setValue('ASSISTANT');
    await flushPromises();

    const roleCall = fetchMock.mock.calls.find(([url, options]) => url === '/api/v1/courses/1/members/602' && options.method === 'PUT');
    expect(roleCall).toBeTruthy();
    expect(JSON.parse(roleCall![1].body)).toEqual({ role: 'ASSISTANT', status: 'ACTIVE' });
  });

  it('keeps the course detail modal open and refreshes members after last-teacher removal is rejected', async () => {
    const onlyTeacher = {
      courseId: 1,
      userId: 101,
      role: 'TEACHER',
      status: 'ACTIVE',
      joinMethod: 'CREATED',
      approvedBy: 101,
      joinedAt: '2026-03-01T08:00:00'
    };
    const page = (list = [course], total = list.length) => ({
      code: '0',
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => homeSummary(course) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [onlyTeacher] }) })
      .mockResolvedValueOnce({ ok: false, json: async () => ({ code: '409', message: 'CANNOT_REMOVE_SELF', data: null }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: course }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [onlyTeacher] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    const teacherRow = wrapper.findAll('.resource-row').find((row) => row.text().includes('101'));
    expect(teacherRow).toBeTruthy();
    await teacherRow!.find('button.card-btn.danger').trigger('click');
    await flushPromises();

    expect(wrapper.find('.course-home').exists()).toBe(true);
    expect(wrapper.find('.course-modal').exists()).toBe(false);
    expect(wrapper.text()).toContain('课程详情');
    expect(wrapper.text()).toContain('CANNOT_REMOVE_SELF');
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/courses/1/members')).toHaveLength(2);
  });
});

function installLocalStorageMock() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear())
    }
  });
}
