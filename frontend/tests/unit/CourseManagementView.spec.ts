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
  manageable: true,
  createdAt: '2026-05-25T00:00:00',
  updatedAt: '2026-05-25T00:00:00'
};

describe('CourseManagementView', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    installLocalStorageMock();
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
    const gradeLink = wrapper.findAll('.navbar-menu a').find((link) => link.text().includes('成绩分析'));
    expect(gradeLink?.attributes('href')).toBe('/courses/1/grd/grade-items');

    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('课程详情');
    expect(wrapper.text()).toContain(longDescription);
    expect(wrapper.text()).toContain('预留操作区');
    expect(wrapper.text()).toContain('暂无章节目录');
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
        title: '课程导论',
        content: '目标与安排',
        orderNum: 1,
        children: [],
        createdAt: '2026-05-25T00:00:00',
        updatedAt: '2026-05-25T00:00:00'
      }
    ];
    const nested = { ...chapters[0], id: 12, parentId: 11, title: '开发环境', content: '安装 JDK 与 IDE' };
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
