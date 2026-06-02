import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import {
  getCachedLearningStatistics,
  flushQueuedLearningRecords,
  getLearningStatistics,
  getQueuedLearningRecords,
  reportLearningRecord
} from '../../../src/api/lrn/learningRecords';

describe('learning records API client', () => {
  beforeEach(() => {
    installLocalStorageMock();
    window.history.pushState({}, '', '/');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
    removeAuthStorage('onlinejudge.userId');
    if (typeof window.localStorage.clear === 'function') {
      window.localStorage.clear();
    }
    window.history.pushState({}, '', '/');
  });

  it('calls the documented statistics and record endpoints with bearer auth', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(statistics))
      .mockResolvedValueOnce(jsonResponse({
        id: 3,
        courseId: 101,
        courseName: 'Java Programming',
        sourceModule: 'LAB',
        sourceId: 301,
        actionType: 'SUBMIT',
        durationSeconds: 240,
        startedAt: '2026-06-02 10:00:00',
        endedAt: '2026-06-02 10:04:00'
      }));

    await getLearningStatistics(101);
    await reportLearningRecord({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 301,
      actionType: 'SUBMIT',
      durationSeconds: 240
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/learning/statistics?courseId=101', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/learning/records', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      }),
      body: JSON.stringify({
        courseId: 101,
        sourceModule: 'LAB',
        sourceId: 301,
        actionType: 'SUBMIT',
        durationSeconds: 240
      })
    }));
    expect(getCachedLearningStatistics(101)).toEqual(statistics);
  });

  it('returns cached statistics and queues failed record reports for offline recovery', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(statistics))
      .mockRejectedValueOnce(new Error('network down'))
      .mockRejectedValueOnce(new Error('network down'));

    await getLearningStatistics(101);
    const cached = await getLearningStatistics(101);
    await reportLearningRecord({
      courseId: 101,
      sourceModule: 'CRS',
      sourceId: 701,
      actionType: 'ACCESS',
      durationSeconds: 60
    });

    expect(cached.fromCache).toBe(true);
    expect(cached.summary.totalDurationSeconds).toBe(420);
    expect(getQueuedLearningRecords(101)).toHaveLength(1);
    expect(getQueuedLearningRecords(101)[0]).toEqual(expect.objectContaining({
      courseId: 101,
      sourceModule: 'CRS',
      sourceId: 701,
      actionType: 'ACCESS',
      durationSeconds: 60
    }));
  });

  it('flushes queued behavior records after reconnect and keeps failed leftovers', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
    vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('network down'))
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce(jsonResponse(recordResponse(701)))
      .mockRejectedValueOnce(new Error('still offline'));

    await reportLearningRecord({
      courseId: 101,
      sourceModule: 'CRS',
      sourceId: 701,
      actionType: 'ACCESS',
      durationSeconds: 60
    });
    await reportLearningRecord({
      courseId: 101,
      sourceModule: 'LAB',
      sourceId: 301,
      actionType: 'STUDY',
      durationSeconds: 120
    });

    const result = await flushQueuedLearningRecords();

    expect(result).toEqual({ sent: 1, remaining: 1 });
    expect(getQueuedLearningRecords(101)).toEqual([
      expect.objectContaining({
        courseId: 101,
        sourceModule: 'LAB',
        sourceId: 301,
        actionType: 'STUDY',
        durationSeconds: 120
      })
    ]);
    expect(globalThis.fetch).toHaveBeenNthCalledWith(3, '/api/v1/learning/records', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      }),
      body: expect.stringContaining('"sourceId":701')
    }));
    expect(globalThis.fetch).toHaveBeenNthCalledWith(4, '/api/v1/learning/records', expect.objectContaining({
      body: expect.stringContaining('"sourceId":301')
    }));
  });

  it('replays queued behavior records when the browser comes back online', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
    vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce(jsonResponse(recordResponse(701)));

    await reportLearningRecord({
      courseId: 101,
      sourceModule: 'CRS',
      sourceId: 701,
      actionType: 'ACCESS',
      durationSeconds: 60
    });

    window.dispatchEvent(new Event('online'));
    await flushPromises();

    expect(getQueuedLearningRecords(101)).toHaveLength(0);
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });

  it('keeps cached statistics isolated by current user and course', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-a-token');
    writeAuthStorage('onlinejudge.userId', '601');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(statistics))
      .mockRejectedValueOnce(new Error('network down'));

    await getLearningStatistics(101);

    writeAuthStorage('onlinejudge.authToken', 'student-b-token');
    writeAuthStorage('onlinejudge.userId', '602');

    await expect(getLearningStatistics(101)).rejects.toThrow('network down');
    expect(getCachedLearningStatistics(101)).toBeNull();
  });

  it('does not use cached statistics for forbidden or expired sessions', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(statistics))
      .mockResolvedValueOnce(errorResponse('ERR-AUTH-05', 'forbidden'))
      .mockResolvedValueOnce(errorResponse('ERR-AUTH-04', 'expired'));

    await getLearningStatistics(101);

    await expect(getLearningStatistics(101)).rejects.toThrow('forbidden');
    expect(window.location.pathname).toBe('/403');

    window.history.pushState({}, '', '/');
    await expect(getLearningStatistics(101)).rejects.toThrow('expired');
    expect(window.location.pathname).toBe('/session-expired');
  });
});

const statistics = {
  summary: {
    totalDurationSeconds: 420,
    resourceAccessCount: 3,
    completedTaskCount: 1,
    submittedTaskCount: 1,
    totalRecordCount: 5
  },
  trends: [
    { date: '2026-05-27', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-28', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-29', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-30', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-05-31', durationSeconds: 180, resourceAccessCount: 1, completedTaskCount: 1 },
    { date: '2026-06-01', durationSeconds: 0, resourceAccessCount: 0, completedTaskCount: 0 },
    { date: '2026-06-02', durationSeconds: 240, resourceAccessCount: 2, completedTaskCount: 0 }
  ],
  recentRecords: []
};

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

function errorResponse(code: string, message: string) {
  return {
    ok: false,
    json: async () => ({
      code,
      message,
      data: null
    })
  } as Response;
}

function recordResponse(sourceId: number) {
  return {
    id: sourceId,
    courseId: 101,
    courseName: 'Java Programming',
    sourceModule: sourceId === 701 ? 'CRS' : 'LAB',
    sourceId,
    actionType: sourceId === 701 ? 'ACCESS' : 'STUDY',
    durationSeconds: sourceId === 701 ? 60 : 120,
    startedAt: '2026-06-02 10:00:00',
    endedAt: '2026-06-02 10:01:00'
  };
}

async function flushPromises() {
  for (let tick = 0; tick < 6; tick += 1) {
    await Promise.resolve();
  }
}

function installLocalStorageMock() {
  const store = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        store.set(key, value);
      }),
      removeItem: vi.fn((key: string) => {
        store.delete(key);
      }),
      clear: vi.fn(() => {
        store.clear();
      })
    }
  });
}
