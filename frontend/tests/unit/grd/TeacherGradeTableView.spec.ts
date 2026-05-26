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
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce([]);
    vi.mocked(gradeRecordsApi.syncSourceGrades).mockResolvedValueOnce({
      calculationBatchId: 0,
      affectedItemCount: 2,
      affectedStudentCount: 2,
      syncedCount: 3,
      missingCount: 0,
      ungradedCount: 1
    });
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce([
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
        studentId: 602,
        summary: {
          id: 2,
          courseId: 101,
          studentId: 602,
          finalScore: null,
          finalStatus: 'INCOMPLETE',
          publishStatus: 'UNPUBLISHED'
        },
        records: []
      }
    ]);

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
    expect(wrapper.text()).toContain('同步完成：3 条有效成绩，1 条未评分');
    expect(wrapper.text()).toContain('601');
    expect(wrapper.text()).toContain('84.00');
    expect(wrapper.text()).toContain('INCOMPLETE');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
