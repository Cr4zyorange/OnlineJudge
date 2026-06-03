import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import { deleteNotification, listNotifications, markNotificationsRead } from '../../../src/api/lrn/notifications';

describe('notifications API client', () => {
  beforeEach(() => {
    installLocalStorageMock();
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    writeAuthStorage('onlinejudge.userId', '601');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
    removeAuthStorage('onlinejudge.userId');
    if (typeof window.localStorage.clear === 'function') {
      window.localStorage.clear();
    }
  });

  it('calls the documented notification list endpoint with filters and bearer auth', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        records: [],
        total: 0,
        page: 2,
        size: 10,
        unreadCount: 3
      }));

    await listNotifications({
      type: 'GRADE',
      isRead: false,
      startTime: '2026-06-01T00:00:00',
      endTime: '2026-06-03T00:00:00',
      page: 2,
      size: 10
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/notifications?type=GRADE&isRead=false&startTime=2026-06-01T00%3A00%3A00&endTime=2026-06-03T00%3A00%3A00&page=2&size=10', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });

  it('calls the documented batch read endpoint with notification ids', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        updatedCount: 2
      }));

    await markNotificationsRead({
      notificationIds: [10, 11],
      readAll: false
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/notifications/read', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        notificationIds: [10, 11],
        readAll: false
      }),
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });

  it('calls the documented single delete endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        updatedCount: 1
      }));

    await deleteNotification(10);

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/notifications/10', expect.objectContaining({
      method: 'DELETE',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });
});

function jsonResponse(data: unknown) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'success',
      data
    })
  } as Response;
}

function installLocalStorageMock() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        values.set(key, value);
      }),
      removeItem: vi.fn((key: string) => {
        values.delete(key);
      }),
      clear: vi.fn(() => {
        values.clear();
      })
    }
  });
}
