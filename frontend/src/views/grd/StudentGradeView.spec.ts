import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import StudentGradeView from './StudentGradeView.vue';
import { getMyPublishedGrades } from '../../api/grd/gradeRecords';

vi.mock('../../api/grd/gradeRecords', () => ({
  getMyPublishedGrades: vi.fn()
}));

const mockedGetMyPublishedGrades = vi.mocked(getMyPublishedGrades);

describe('StudentGradeView', () => {
  it('loads the current student published grades with sources and feedback', async () => {
    mockedGetMyPublishedGrades.mockResolvedValueOnce({
      studentId: 601,
      summary: {
        id: 31,
        courseId: 101,
        studentId: 601,
        finalScore: '84.00',
        finalStatus: 'CALCULATED',
        publishStatus: 'PUBLISHED',
        publishedAt: '2026-05-30T10:00:00'
      },
      records: [
        {
          id: 11,
          courseId: 101,
          studentId: 601,
          gradeItemId: 1,
          sourceType: 'LAB',
          sourceId: 301,
          rawScore: '90.00',
          weightedScore: '36.00',
          gradeStatus: 'SCORED',
          publishStatus: 'PUBLISHED',
          publishedAt: '2026-05-30T10:00:00',
          comment: '实验完成度良好'
        },
        {
          id: 12,
          courseId: 101,
          studentId: 601,
          gradeItemId: 2,
          sourceType: 'HWK',
          sourceId: 401,
          rawScore: '80.00',
          weightedScore: '48.00',
          gradeStatus: 'SCORED',
          publishStatus: 'PUBLISHED',
          publishedAt: '2026-05-30T10:00:00',
          comment: null
        }
      ]
    });

    const wrapper = mount(StudentGradeView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(mockedGetMyPublishedGrades).toHaveBeenCalledWith(101);
    expect(wrapper.text()).toContain('84.00');
    expect(wrapper.text()).toContain('LAB #301');
    expect(wrapper.text()).toContain('HWK #401');
    expect(wrapper.text()).toContain('36.00');
    expect(wrapper.text()).toContain('48.00');
    expect(wrapper.text()).toContain('实验完成度良好');
  });

  it('shows an unpublished state without leaking score cells when the api rejects', async () => {
    mockedGetMyPublishedGrades.mockRejectedValueOnce(new Error('成绩未发布，不能查看未公开成绩'));

    const wrapper = mount(StudentGradeView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('成绩未发布，不能查看未公开成绩');
    expect(wrapper.find('table').exists()).toBe(false);
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
