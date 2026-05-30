import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import {
  configureLearningTaskAuthContext,
  listLearningTasks
} from '../../../src/api/lrn/learningTasks';

describe('learning tasks API client', () => {
  afterEach(() => {
    configureLearningTaskAuthContext(null);
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
  });

  it('calls the documented task center endpoint with filters and student auth', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    configureLearningTaskAuthContext(() => ({
      userId: 601,
      userRole: 'STUDENT',
      courseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        records: [],
        total: 0,
        page: 2,
        size: 10
      }));

    await listLearningTasks({
      taskType: ['HOMEWORK', 'EXPERIMENT'],
      status: 'IN_PROGRESS',
      courseId: 101,
      sortBy: 'deadline',
      order: 'desc',
      page: 2,
      size: 10
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/learning/tasks?taskType=HOMEWORK%2CEXPERIMENT&status=IN_PROGRESS&courseId=101&sortBy=deadline&order=desc&page=2&size=10', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });
});

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'success',
      data
    })
  } as Response;
}
