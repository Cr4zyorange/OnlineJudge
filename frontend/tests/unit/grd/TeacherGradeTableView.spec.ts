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

  it('queries the grade table with filters and page navigation', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades)
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: null,
            records: []
          }
        ],
        total: 30,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 603,
            summary: null,
            records: []
          }
        ],
        total: 12,
        page: 1,
        size: 10
      })
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 613,
            summary: null,
            records: []
          }
        ],
        total: 12,
        page: 2,
        size: 10
      });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="student-keyword"]').setValue('603');
    await wrapper.get('[data-testid="grade-item-id"]').setValue('7');
    await wrapper.get('[data-testid="grade-status"]').setValue('MISSING');
    await wrapper.get('[data-testid="publish-status"]').setValue('UNPUBLISHED');
    await wrapper.get('[data-testid="page-size"]').setValue('10');
    await wrapper.get('[data-testid="grade-filter-form"]').trigger('submit');
    await flushPromises();

    expect(gradeRecordsApi.listCourseGrades).toHaveBeenNthCalledWith(2, 101, {
      studentKeyword: '603',
      gradeItemId: 7,
      gradeStatus: 'MISSING',
      publishStatus: 'UNPUBLISHED',
      page: 1,
      size: 10
    });
    expect(wrapper.text()).toContain('603');
    expect(wrapper.text()).toContain('第 1 / 2 页');

    await wrapper.get('[data-testid="next-page"]').trigger('click');
    await flushPromises();

    expect(gradeRecordsApi.listCourseGrades).toHaveBeenNthCalledWith(3, 101, {
      studentKeyword: '603',
      gradeItemId: 7,
      gradeStatus: 'MISSING',
      publishStatus: 'UNPUBLISHED',
      page: 2,
      size: 10
    });
    expect(wrapper.text()).toContain('613');
    expect(wrapper.text()).toContain('第 2 / 2 页');
  });

  it('shows student details, submits a reasoned grade adjustment, and renders change logs', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades)
      .mockResolvedValueOnce({
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
            records: [
              {
                id: 9,
                courseId: 101,
                studentId: 601,
                gradeItemId: 1,
                sourceType: 'LAB',
                sourceId: 301,
                rawScore: '90.00',
                weightedScore: '36.00',
                gradeStatus: 'SCORED',
                publishStatus: 'UNPUBLISHED'
              }
            ]
          }
        ],
        total: 1,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: {
              id: 1,
              courseId: 101,
              studentId: 601,
              finalScore: '86.00',
              finalStatus: 'CALCULATED',
              publishStatus: 'UNPUBLISHED'
            },
            records: [
              {
                id: 9,
                courseId: 101,
                studentId: 601,
                gradeItemId: 1,
                sourceType: 'LAB',
                sourceId: 301,
                rawScore: '95.00',
                weightedScore: '38.00',
                gradeStatus: 'ADJUSTED',
                publishStatus: 'UNPUBLISHED'
              }
            ]
          }
        ],
        total: 1,
        page: 1,
        size: 20
      });
    vi.mocked(gradeRecordsApi.listGradeChangeLogs).mockResolvedValue({
      records: [
        {
          id: 3,
          courseId: 101,
          studentId: 601,
          gradeItemId: 1,
          changeType: 'RECORD_ADJUST',
          oldValue: '90.00',
          newValue: '95.00',
          reason: '复核测试用例后修正',
          operatorId: 501,
          createdAt: '2026-05-26T18:30:00'
        }
      ],
      total: 1,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.adjustGradeRecord).mockResolvedValue({
      recordId: 9,
      studentId: 601,
      gradeItemId: 1,
      oldScore: '90.00',
      newScore: '95.00',
      reason: '复核测试用例后修正',
      updatedAt: '2026-05-26T18:30:00'
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="detail-student-601"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('成绩明细');
    expect(wrapper.text()).toContain('90.00');

    await wrapper.get('[data-testid="adjustment-score"]').setValue('95.00');
    await wrapper.get('[data-testid="adjustment-reason"]').setValue('复核测试用例后修正');
    await wrapper.get('[data-testid="submit-adjustment"]').trigger('submit');
    await flushPromises();

    expect(gradeRecordsApi.adjustGradeRecord).toHaveBeenCalledWith(9, {
      newScore: '95.00',
      reason: '复核测试用例后修正'
    });
    expect(gradeRecordsApi.listGradeChangeLogs).toHaveBeenCalledWith(101, {
      studentId: 601,
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('调整完成：90.00 -> 95.00');
    expect(wrapper.text()).toContain('复核测试用例后修正');
    expect(wrapper.text()).toContain('ADJUSTED');
  });

  it('submits a reasoned final-score adjustment from the student detail panel', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades)
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: {
              id: 5,
              courseId: 101,
              studentId: 601,
              finalScore: '84.00',
              finalStatus: 'CALCULATED',
              publishStatus: 'UNPUBLISHED'
            },
            records: []
          }
        ],
        total: 1,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: {
              id: 5,
              courseId: 101,
              studentId: 601,
              finalScore: '88.00',
              finalStatus: 'ADJUSTED',
              publishStatus: 'UNPUBLISHED'
            },
            records: []
          }
        ],
        total: 1,
        page: 1,
        size: 20
      });
    vi.mocked(gradeRecordsApi.listGradeChangeLogs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.adjustCourseFinalScore).mockResolvedValue({
      summaryId: 5,
      studentId: 601,
      oldScore: '84.00',
      newScore: '88.00',
      reason: '课程总评复核修正',
      updatedAt: '2026-05-26T18:30:00'
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="detail-student-601"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="final-score"]').setValue('88.00');
    await wrapper.get('[data-testid="final-reason"]').setValue('课程总评复核修正');
    await wrapper.get('[data-testid="submit-final-adjustment"]').trigger('submit');
    await flushPromises();

    expect(gradeRecordsApi.adjustCourseFinalScore).toHaveBeenCalledWith(5, {
      newScore: '88.00',
      reason: '课程总评复核修正'
    });
    expect(wrapper.text()).toContain('总评调整完成：84.00 -> 88.00');
    expect(wrapper.text()).toContain('ADJUSTED');
  });

  it('publishes selected student grades and renders the publish record', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades)
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: {
              id: 5,
              courseId: 101,
              studentId: 601,
              finalScore: '84.00',
              finalStatus: 'CALCULATED',
              publishStatus: 'UNPUBLISHED'
            },
            records: []
          }
        ],
        total: 1,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            studentId: 601,
            summary: {
              id: 5,
              courseId: 101,
              studentId: 601,
              finalScore: '84.00',
              finalStatus: 'CALCULATED',
              publishStatus: 'PUBLISHED',
              publishedAt: '2026-05-29T10:00:00'
            },
            records: []
          }
        ],
        total: 1,
        page: 1,
        size: 20
      });
    vi.mocked(gradeRecordsApi.publishCourseGrades).mockResolvedValue({
      publishId: 7,
      publishedCount: 1,
      publishedAt: '2026-05-29T10:00:00',
      notificationStatus: 'SENT'
    });
    vi.mocked(gradeRecordsApi.listGradePublishRecords).mockResolvedValue({
      records: [
        {
          id: 7,
          courseId: 101,
          publishScope: 'PARTIAL_STUDENTS',
          publishedCount: 1,
          publishedBy: 501,
          publishedAt: '2026-05-29T10:00:00',
          notificationStatus: 'SENT',
          remark: 'students=601'
        }
      ],
      total: 1,
      page: 1,
      size: 20
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="detail-student-601"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="publish-selected-student"]').trigger('click');
    await flushPromises();

    expect(gradeRecordsApi.publishCourseGrades).toHaveBeenCalledWith(101, {
      publishScope: 'PARTIAL_STUDENTS',
      studentIds: [601],
      gradeItemIds: []
    });
    expect(gradeRecordsApi.listGradePublishRecords).toHaveBeenCalledWith(101, {
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('发布完成：1 名学生可查看成绩，通知状态 SENT');
    expect(wrapper.text()).toContain('PARTIAL_STUDENTS');
    expect(wrapper.text()).toContain('PUBLISHED');
  });

  it('loads publish records when the grade table first renders', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValueOnce({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listGradePublishRecords).mockResolvedValue({
      records: [
        {
          id: 7,
          courseId: 101,
          publishScope: 'PARTIAL_STUDENTS',
          publishedCount: 1,
          publishedBy: 501,
          publishedAt: '2026-05-29T10:00:00',
          notificationStatus: 'SENT',
          remark: 'students=1;gradeItems=2'
        }
      ],
      total: 1,
      page: 1,
      size: 20
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(gradeRecordsApi.listGradePublishRecords).toHaveBeenCalledWith(101, {
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('PARTIAL_STUDENTS');
    expect(wrapper.text()).toContain('通知 SENT');
  });

  it('renders course grade analysis and refreshes a selected grade item analysis', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listGradePublishRecords).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.getCourseGradeAnalysis)
      .mockResolvedValueOnce({
        targetType: 'COURSE_TOTAL',
        gradeItemId: null,
        totalStudentCount: 3,
        completedCount: 1,
        missingCount: 2,
        unsubmittedCount: 0,
        ungradedCount: 0,
        averageScore: '84.00',
        maxScore: '84.00',
        minScore: '84.00',
        passRate: '1.0000',
        completionRate: '0.3333',
        distribution: [
          { label: '0-59', count: 0 },
          { label: '60-69', count: 0 },
          { label: '70-79', count: 0 },
          { label: '80-89', count: 1 },
          { label: '90-100', count: 0 }
        ],
        sourceDataTime: '2026-06-03T10:00:00',
        generatedAt: '2026-06-03T10:00:01'
      });
    vi.mocked(gradeRecordsApi.getGradeItemCompletion)
      .mockResolvedValueOnce({
        gradeItemId: 2,
        totalStudentCount: 3,
        submittedCount: 2,
        completedCount: 1,
        missingCount: 1,
        unsubmittedCount: 0,
        ungradedCount: 1,
        averageScore: '80.00',
        completionRate: '0.3333',
        sourceDataTime: '2026-06-03T10:01:00',
        generatedAt: '2026-06-03T10:01:01'
      });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(gradeRecordsApi.getCourseGradeAnalysis).toHaveBeenCalledWith(101, {
      targetType: 'COURSE_TOTAL'
    });
    expect(wrapper.text()).toContain('教学分析');
    expect(wrapper.text()).toContain('均分');
    expect(wrapper.text()).toContain('84.00');
    expect(wrapper.text()).toContain('缺失 2');
    expect(wrapper.text()).toContain('80-89：1');

    await wrapper.get('[data-testid="analysis-target-type"]').setValue('GRADE_ITEM');
    await wrapper.get('[data-testid="analysis-grade-item-id"]').setValue('2');
    await wrapper.get('[data-testid="analysis-form"]').trigger('submit');
    await flushPromises();

    expect(gradeRecordsApi.getGradeItemCompletion).toHaveBeenCalledWith(101, 2);
    expect(wrapper.text()).toContain('成绩项 2');
    expect(wrapper.text()).toContain('80.00');
    expect(wrapper.text()).toContain('已提交 2');
    expect(wrapper.text()).toContain('待评分 1');
  });

  it('filters grade review requests by approved status and renders processed results without action buttons', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listGradePublishRecords).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listCourseGradeReviewRequests)
      .mockResolvedValueOnce({
        records: [],
        total: 0,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            requestId: 42,
            courseId: 101,
            studentId: 602,
            gradeItemId: null,
            targetType: 'FINAL_SCORE',
            reason: '复核后确认补交成绩',
            status: 'APPROVED',
            originalScore: '84.00',
            adjustedScore: '88.00',
            responseComment: '确认补交成绩有效',
            submittedAt: '2026-06-03T09:00:00',
            processedBy: 501,
            processedAt: '2026-06-03T09:10:00'
          }
        ],
        total: 1,
        page: 1,
        size: 20
      });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(gradeRecordsApi.listCourseGradeReviewRequests).toHaveBeenCalledWith(101, {
      status: 'PENDING',
      page: 1,
      size: 20
    });

    await wrapper.get('[data-testid="review-status-filter"]').setValue('APPROVED');
    await flushPromises();

    expect(gradeRecordsApi.listCourseGradeReviewRequests).toHaveBeenNthCalledWith(2, 101, {
      status: 'APPROVED',
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('APPROVED');
    expect(wrapper.text()).toContain('复核后确认补交成绩');
    expect(wrapper.text()).toContain('确认补交成绩有效');
    expect(wrapper.text()).toContain('88.00');
    expect(wrapper.text()).toContain('2026-06-03T09:10:00');
    expect(wrapper.find('[data-testid="approve-review-42"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="reject-review-42"]').exists()).toBe(false);
  });

  it('loads pending grade review requests and lets the teacher process one', async () => {
    vi.mocked(gradeRecordsApi.listCourseGrades).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listGradePublishRecords).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
    vi.mocked(gradeRecordsApi.listCourseGradeReviewRequests)
      .mockResolvedValueOnce({
        records: [
          {
            requestId: 41,
            courseId: 101,
            studentId: 601,
            gradeItemId: null,
            targetType: 'FINAL_SCORE',
            reason: '总评未计入补交成绩',
            status: 'PENDING',
            originalScore: '84.00',
            adjustedScore: null,
            responseComment: null,
            submittedAt: '2026-06-03T09:00:00',
            processedBy: null,
            processedAt: null
          }
        ],
        total: 1,
        page: 1,
        size: 20
      })
      .mockResolvedValueOnce({
        records: [
          {
            requestId: 41,
            courseId: 101,
            studentId: 601,
            gradeItemId: null,
            targetType: 'FINAL_SCORE',
            reason: '总评未计入补交成绩',
            status: 'APPROVED',
            originalScore: '84.00',
            adjustedScore: '88.00',
            responseComment: '确认补交成绩有效',
            submittedAt: '2026-06-03T09:00:00',
            processedBy: 501,
            processedAt: '2026-06-03T09:10:00'
          }
        ],
        total: 1,
        page: 1,
        size: 20
      });
    vi.mocked(gradeRecordsApi.processGradeReviewRequest).mockResolvedValue({
      requestId: 41,
      status: 'APPROVED',
      processedAt: '2026-06-03T09:10:00'
    });

    const wrapper = mount(TeacherGradeTableView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(gradeRecordsApi.listCourseGradeReviewRequests).toHaveBeenCalledWith(101, {
      status: 'PENDING',
      page: 1,
      size: 20
    });
    expect(wrapper.text()).toContain('成绩复核');
    expect(wrapper.text()).toContain('总评未计入补交成绩');

    await wrapper.get('[data-testid="review-adjusted-score-41"]').setValue('88.00');
    await wrapper.get('[data-testid="review-response-comment-41"]').setValue('确认补交成绩有效');
    await wrapper.get('[data-testid="approve-review-41"]').trigger('click');
    await flushPromises();

    expect(gradeRecordsApi.processGradeReviewRequest).toHaveBeenCalledWith(41, {
      action: 'APPROVE',
      adjustedScore: '88.00',
      responseComment: '确认补交成绩有效'
    });
    expect(wrapper.text()).toContain('复核已处理：APPROVED');
    expect(wrapper.text()).toContain('确认补交成绩有效');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
