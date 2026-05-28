import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import {
  configureGradeRecordAuthContext,
  adjustGradeRecord,
  adjustCourseFinalScore,
  listGradeChangeLogs,
  listCourseGrades,
  type GradeTableQuery,
  recalculateCourseGrades,
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
