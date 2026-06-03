import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StudentGradeView from './StudentGradeView.vue';
import {
  getMyPublishedGrades,
  listMyGradeReviewRequests,
  submitGradeReviewRequest
} from '../../api/grd/gradeRecords';

vi.mock('../../api/grd/gradeRecords', () => ({
  getMyPublishedGrades: vi.fn(),
  listMyGradeReviewRequests: vi.fn(),
  submitGradeReviewRequest: vi.fn()
}));

const mockedGetMyPublishedGrades = vi.mocked(getMyPublishedGrades);
const mockedListMyGradeReviewRequests = vi.mocked(listMyGradeReviewRequests);
const mockedSubmitGradeReviewRequest = vi.mocked(submitGradeReviewRequest);

describe('StudentGradeView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockedListMyGradeReviewRequests.mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      size: 20
    });
  });

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

  it('submits a final score review request and renders its pending status', async () => {
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
      records: []
    });
    mockedSubmitGradeReviewRequest.mockResolvedValueOnce({
      requestId: 41,
      status: 'PENDING',
      submittedAt: '2026-06-03T09:00:00'
    });
    mockedListMyGradeReviewRequests
      .mockResolvedValueOnce({
        records: [],
        total: 0,
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
      });

    const wrapper = mount(StudentGradeView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="review-target-type"]').setValue('FINAL_SCORE');
    await wrapper.get('[data-testid="review-reason"]').setValue('总评未计入补交成绩');
    await wrapper.get('[data-testid="submit-grade-review"]').trigger('submit');
    await flushPromises();

    expect(mockedSubmitGradeReviewRequest).toHaveBeenCalledWith(101, {
      targetType: 'FINAL_SCORE',
      gradeItemId: undefined,
      reason: '总评未计入补交成绩'
    });
    expect(wrapper.text()).toContain('异议已提交，等待教师复核');
    expect(wrapper.text()).toContain('PENDING');
    expect(wrapper.text()).toContain('总评未计入补交成绩');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
