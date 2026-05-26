import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeacherGradeTableView from '../../../src/views/grd/TeacherGradeTableView.vue';
import * as gradeRecordsApi from '../../../src/api/grd/gradeRecords';

vi.mock('../../../src/api/grd/gradeRecords');

describe('TeacherGradeTableView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('syncs source grades and renders calculated final score and incomplete status', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.syncSourceGrades).mockResolvedValueOnce({
      calculationBatchId: 12,
      affectedItemCount: 2,
      affectedStudentCount: 3,
      syncedCount: 3,
      missingCount: 2,
      ungradedCount: 1
    });
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce({
      records: [
        {
          studentId: 601,
          summary: {
            id: 1,
            courseId: 101,
            studentId: 601,
            finalScore: '84.00',
            finalStatus: 'CALCULATED',
            publishStatus: 'UNPUBLISHED'
          },
          records: []
        },
        {
          studentId: 603,
          summary: {
            id: 3,
            courseId: 101,
            studentId: 603,
            finalScore: null,
            finalStatus: 'INCOMPLETE',
            publishStatus: 'UNPUBLISHED'
          },
          records: []
        },
      ],
      total: 3,
      page: 1,
      size: 20
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无成绩记录');

    await wrapper.findAll('button').find((button) => button.text() === '同步来源成绩')?.trigger('click');
    await flushPromises();

    expect(gradeRecordsApi.syncSourceGrades).toHaveBeenCalledWith(101);
    expect(wrapper.text()).toContain('同步完成：3 条有效成绩，1 条未评分，2 条缺失');
    expect(wrapper.text()).toContain('共 3 名学生');
    expect(wrapper.text()).toContain('601');
    expect(wrapper.text()).toContain('84.00');
    expect(wrapper.text()).toContain('603');
    expect(wrapper.text()).toContain('INCOMPLETE');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
