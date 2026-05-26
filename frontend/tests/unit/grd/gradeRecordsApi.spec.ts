import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  configureGradeRecordAuthContext,
  listCourseGrades,
  recalculateCourseGrades,
  syncSourceGrades
} from '../../../src/api/grd/gradeRecords';

describe('gradeRecords API client', () => {
  afterEach(() => {
    configureGradeRecordAuthContext(null);
    vi.restoreAllMocks();
  });

  it('calls documented grade sync, recalculation, and table endpoints with teacher course auth', async () => {
    configureGradeRecordAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ syncedCount: 2 }))
      .mockResolvedValueOnce(jsonResponse({ affectedCount: 2 }))
      .mockResolvedValueOnce(jsonResponse([]));

    await syncSourceGrades(101);
    await recalculateCourseGrades(101);
    await listCourseGrades(101);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/courses/101/grades/sync', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        'X-User-Id': '501',
        'X-User-Role': 'TEACHER',
        'X-Manageable-Course-Ids': '101'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/courses/101/grades/recalculate', expect.objectContaining({
      method: 'POST'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/courses/101/grades', expect.objectContaining({
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
