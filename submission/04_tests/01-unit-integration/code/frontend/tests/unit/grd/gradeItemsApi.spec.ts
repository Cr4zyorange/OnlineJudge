import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import {
  configureGradeItemAuthContext,
  updateGradeItem,
  validateGradeRules
} from '../../../src/api/grd/gradeItems';

describe('gradeItems API client', () => {
  afterEach(() => {
    configureGradeItemAuthContext(null);
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
  });

  it('calls documented grade item endpoints with auth context supplied by the integration layer', async () => {
    writeAuthStorage('onlinejudge.authToken', 'teacher-token');
    configureGradeItemAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [101]
    }));

    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
      .mockResolvedValueOnce(jsonResponse({ valid: true, totalIncludedWeight: '0.50', errors: [] }));

    await updateGradeItem(7, {
      name: '实验一',
      sourceType: 'LAB',
      sourceId: 301,
      fullScore: '100.00',
      weight: '0.50',
      includedInFinal: true,
      sortOrder: 1
    });
    await validateGradeRules(101, [
      {
        name: '课堂表现',
        sourceType: 'OTHER_COURSE_ITEM',
        sourceId: 901,
        fullScore: '10.00',
        weight: '0.10',
        includedInFinal: true,
        sortOrder: 1
      }
    ]);
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/grade-items/7', expect.objectContaining({
      method: 'PUT',
      headers: expect.objectContaining({
        Authorization: 'Bearer teacher-token'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/courses/101/grade-rules/validate', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer teacher-token'
      }),
      body: JSON.stringify({
        gradeItems: [
          {
            name: '课堂表现',
            sourceType: 'OTHER_COURSE_ITEM',
            sourceId: 901,
            fullScore: '10.00',
            weight: '0.10',
            includedInFinal: true,
            sortOrder: 1
          }
        ]
      })
    }));
  });

  it('fails fast when grade item auth context is not available', async () => {
    vi.spyOn(globalThis, 'fetch');

    await expect(validateGradeRules(101)).rejects.toThrow('当前登录态缺失');
    expect(globalThis.fetch).not.toHaveBeenCalled();
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
