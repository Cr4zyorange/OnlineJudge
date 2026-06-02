import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import { getLearningProgress, getTeacherLearningProgress, saveLearningProgress } from '../../../src/api/lrn/learningProgress';

describe('learning progress API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
  });

  it('calls the documented progress endpoints with bearer auth', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ courses: [], total: 0 }))
      .mockResolvedValueOnce(jsonResponse({
        progressId: 1,
        courseId: 101,
        courseName: 'Java Programming',
        chapterId: 1001,
        chapterName: 'Variables',
        sourceModule: 'CRS',
        sourceId: 701,
        progressPercent: 65,
        lastPosition: 'video_play_time=1234',
        status: 'IN_PROGRESS',
        continueUrl: '/courses/101',
        updatedAt: '2026-06-01 10:00:00'
      }))
      .mockResolvedValueOnce(jsonResponse({
        courseId: 101,
        courseName: 'Java Programming',
        studentCount: 1,
        averageProgressPercent: 65,
        students: []
      }));

    await getLearningProgress(101);
    await saveLearningProgress({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'CRS',
      sourceId: 701,
      progressPercent: 65,
      lastPosition: 'video_play_time=1234'
    });
    await getTeacherLearningProgress(101);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/learning/progress?courseId=101', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/learning/progress', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      }),
      body: JSON.stringify({
        courseId: 101,
        chapterId: 1001,
        sourceModule: 'CRS',
        sourceId: 701,
        progressPercent: 65,
        lastPosition: 'video_play_time=1234'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/learning/progress/teacher?courseId=101', expect.objectContaining({
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
