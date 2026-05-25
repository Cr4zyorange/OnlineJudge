import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import CourseManagementView from '../../src/views/crs/CourseManagementView.vue';

const course = {
  id: 1,
  name: '软件工程基础',
  description: '课程创建与管理主流程，覆盖教师建课、信息维护、学生加入、教学资源组织与后续课程运维需求，保证课程展示卡片不会因为简介过长而影响排版，同时为后续加入课程、成员管理、公告发布、成绩联动等操作预留足够清晰的展示空间。',
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
  it('truncates long descriptions and opens a detail modal from the all courses view', async () => {
    const page = (list = [course], total = list.length) => ({
      code: 200,
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.get('.card-desc').text().length).toBeLessThan(course.description.length);
    await wrapper.get('.course-card').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('课程详情');
    expect(wrapper.text()).toContain(course.description);
    expect(wrapper.text()).toContain('预留操作区');
  });

  it('uses different layouts for all courses and managed courses, then creates a course', async () => {
    const page = (list = [course], total = list.length) => ({
      code: 200,
      message: 'success',
      data: { list, total, page: 1, size: 20 }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([course], 1) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 200, message: 'success', data: { ...course, id: 2, name: '数据结构' } })
      })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, id: 2, name: '数据结构' }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, id: 2, name: '数据结构' }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([{ ...course, id: 2, name: '数据结构' }], 1) })
      .mockResolvedValueOnce({ ok: true, json: async () => page([], 0) });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(CourseManagementView);
    await flushPromises();

    expect(wrapper.text()).toContain('软件工程基础');
    expect(wrapper.text()).toContain('师生共用课程列表');
    expect(wrapper.find('input[placeholder="例如：软件工程基础"]').exists()).toBe(false);

    const managedButton = wrapper.findAll('button').find((button) => button.text().includes('我管理的'));
    expect(managedButton).toBeTruthy();
    await managedButton!.trigger('click');
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
});
