import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

describe('CourseManagementView', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'teacher-token');
    window.localStorage.setItem('onlinejudge.userId', '101');
    window.localStorage.setItem('onlinejudge.userRole', 'TEACHER');
    window.localStorage.setItem('onlinejudge.username', 'Teacher101');
  });

  it('truncates long descriptions and opens a detail modal from the all courses view', async () => {
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
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.get('.card-desc').text().length).toBeLessThan(longDescription.length);
    const learningTaskLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('学习任务'));
    expect(learningTaskLink?.attributes('href')).toBe('/learning/tasks');
    const gradeLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('成绩分析'));
    expect(gradeLink?.attributes('href')).toBe('/courses/1/grd/grade-items');

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('课程详情');
    expect(wrapper.text()).toContain(longDescription);
    expect(wrapper.text()).toContain('预留操作区');
    expect(wrapper.text()).toContain('暂无章节目录');
  });

  it('exposes a glass style sidebar entry for the learning task center', async () => {
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

    const sidebarEntry = wrapper.get('a[data-testid="learning-task-center-entry"]');
    expect(sidebarEntry.attributes('href')).toBe('/learning/tasks');
    expect(sidebarEntry.classes()).toContain('menu-button');
    expect(sidebarEntry.text()).toContain('学习任务中心');
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

    const managedButton = wrapper.findAll('.menu-button')[1];
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

    await wrapper.findAll('.menu-button')[1].trigger('click');
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

    await wrapper.findAll('.menu-button')[1].trigger('click');
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
      chapterId: null,
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
      });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    await wrapper.findAll('.menu-button')[1].trigger('click');
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

    const joinButton = wrapper.findAll('button.card-btn').find((button) => button.text().includes('加入'));
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
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [pendingOne, pendingTwo] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...pendingOne, status: 'ACTIVE', approvedBy: 101 } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [pendingTwo] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: { ...pendingTwo, status: 'REJECTED', approvedBy: 101 } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ code: '0', message: 'success', data: [] }) })
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
    expect(wrapper.text()).not.toContain('学生 501');
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
