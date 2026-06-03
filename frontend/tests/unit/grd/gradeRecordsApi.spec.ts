import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import {
  configureGradeRecordAuthContext,
  adjustGradeRecord,
  adjustCourseFinalScore,
  getCourseGradeAnalysis,
  getGradeItemCompletion,
  listGradeChangeLogs,
  listCourseGrades,
  listGradePublishRecords,
  listCourseGradeReviewRequests,
  listMyGradeReviewRequests,
  processGradeReviewRequest,
  publishCourseGrades,
  type GradeTableQuery,
  recalculateCourseGrades,
  submitGradeReviewRequest,
  syncSourceGrades
} from '../../../src/api/grd/gradeRecords';

describe('gradeRecords API client', () => {
  afterEach(() => {
    configureGradeRecordAuthContext(null);
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
  });

  it('calls documented grade sync, recalculation, and table endpoints with teacher course auth', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ syncedCount: 2 }))
      .mockResolvedValueOnce(jsonResponse({ affectedCount: 2 }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0, page: 2, size: 10 }));

    await syncSourceGrades(101);
    await recalculateCourseGrades(101);
    const query: GradeTableQuery = {
      studentKeyword: '603',
      gradeStatus: 'MISSING',
      publishStatus: 'UNPUBLISHED',
      page: 2,
      size: 10
    };
    await listCourseGrades(101, query);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/courses/101/grades/sync', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer teacher-token'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/courses/101/grades/recalculate', expect.objectContaining({
      method: 'POST'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/courses/101/grades?studentKeyword=603&gradeStatus=MISSING&publishStatus=UNPUBLISHED&page=2&size=10', expect.objectContaining({
      method: 'GET'
    }));
  });

  it('calls documented grade adjustment and change log endpoints', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ recordId: 9, oldScore: '90.00', newScore: '95.00' }))
      .mockResolvedValueOnce(jsonResponse({ summaryId: 5, oldScore: '84.00', newScore: '88.00' }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0, page: 1, size: 20 }));

    await adjustGradeRecord(9, {
      newScore: '95.00',
      reason: '复核测试用例后修正'
    });
    await adjustCourseFinalScore(5, {
      newScore: '88.00',
      reason: '课程总评复核修正'
    });
    await listGradeChangeLogs(101, {
      studentId: 601,
      gradeItemId: 1,
      page: 1,
      size: 20
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/grade-records/9/adjust', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        newScore: '95.00',
        reason: '复核测试用例后修正'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/course-grade-summaries/5/adjust', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        newScore: '88.00',
        reason: '课程总评复核修正'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/courses/101/grade-change-logs?studentId=601&gradeItemId=1&page=1&size=20', expect.objectContaining({
      method: 'GET'
    }));
  });

  it('calls documented grade publish and publish record endpoints', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ publishId: 7, publishedCount: 1, notificationStatus: 'SENT' }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0, page: 1, size: 20 }));

    await publishCourseGrades(101, {
      publishScope: 'PARTIAL_STUDENTS',
      studentIds: [601],
      gradeItemIds: []
    });
    await listGradePublishRecords(101, { page: 1, size: 20 });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/courses/101/grades/publish', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        publishScope: 'PARTIAL_STUDENTS',
        studentIds: [601],
        gradeItemIds: []
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/courses/101/grade-publish-records?page=1&size=20', expect.objectContaining({
      method: 'GET'
    }));
  });

  it('calls documented grade analysis endpoint with target filters', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        targetType: 'GRADE_ITEM',
        gradeItemId: 2,
        averageScore: '80.00',
        distribution: []
      }));

    await getCourseGradeAnalysis(101, {
      targetType: 'GRADE_ITEM',
      gradeItemId: 2
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/101/grade-analysis?targetType=GRADE_ITEM&gradeItemId=2', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer teacher-token'
      })
    }));
  });

  it('calls documented grade item completion endpoint', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        gradeItemId: 2,
        submittedCount: 2,
        missingCount: 1,
        ungradedCount: 1,
        averageScore: '80.00',
        completionRate: '0.3333'
      }));

    await getGradeItemCompletion(101, 2);

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/101/grade-items/2/completion', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer teacher-token'
      })
    }));
  });

  it('calls documented grade review request endpoints', async () => {
    writeAuthStorage('onlinejudge.authToken', 'grade-review-token');
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ requestId: 31, status: 'PENDING' }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0, page: 1, size: 20 }))
      .mockResolvedValueOnce(jsonResponse({ records: [], total: 0, page: 1, size: 20 }))
      .mockResolvedValueOnce(jsonResponse({ requestId: 31, status: 'APPROVED' }));

    await submitGradeReviewRequest(101, {
      targetType: 'ITEM_SCORE',
      gradeItemId: 7,
      reason: '实验报告评分漏看附录'
    });
    await listMyGradeReviewRequests(101, {
      status: 'PENDING',
      page: 1,
      size: 20
    });
    await listCourseGradeReviewRequests(101, {
      studentId: 601,
      gradeItemId: 7,
      status: 'PENDING',
      page: 1,
      size: 20
    });
    await processGradeReviewRequest(31, {
      action: 'APPROVE',
      adjustedScore: '95.00',
      responseComment: '确认评分遗漏'
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/courses/101/grade-review-requests', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        targetType: 'ITEM_SCORE',
        gradeItemId: 7,
        reason: '实验报告评分漏看附录'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/courses/101/my-grade-review-requests?status=PENDING&page=1&size=20', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/courses/101/grade-review-requests?studentId=601&gradeItemId=7&status=PENDING&page=1&size=20', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/grade-review-requests/31/process', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        action: 'APPROVE',
        adjustedScore: '95.00',
        responseComment: '确认评分遗漏'
      })
    }));
  });
});

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'ok',
      data
    })
  } as Response;
}
