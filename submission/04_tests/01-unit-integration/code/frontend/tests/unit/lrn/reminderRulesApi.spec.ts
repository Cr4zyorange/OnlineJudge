import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import { getReminderRules, saveReminderRules } from '../../../src/api/lrn/reminderRules';
import type { ReminderRuleOverview } from '../../../src/types/lrn';

describe('reminder rules API client', () => {
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

  it('calls the documented reminder rule query endpoint with bearer auth', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(reminderOverview));

    await getReminderRules();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/reminder-rules', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });

  it('saves rules and notification preferences through API-LRN-11', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(reminderOverview));

    await saveReminderRules(reminderOverview);

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/reminder-rules', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(reminderOverview),
      headers: expect.objectContaining({
        Authorization: 'Bearer student-token'
      })
    }));
  });
});

const reminderOverview: ReminderRuleOverview = {
  rules: [
    {
      reminderType: 'HOMEWORK_DEADLINE',
      sourceModule: 'HWK',
      aheadMinutes: 1440,
      enabled: true,
      required: false
    }
  ],
  settings: {
    enableExperiment: true,
    enableHomework: true,
    enableGrade: true,
    enableAnnouncement: true,
    enableNonCriticalReminder: true
  }
};

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
