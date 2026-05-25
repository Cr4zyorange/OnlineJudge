import { afterEach, describe, expect, it, vi } from 'vitest';
import { configureAuthContext, request } from '../../../src/api/http';

describe('shared API request client', () => {
  afterEach(() => {
    configureAuthContext(null);
    vi.restoreAllMocks();
  });

  it('unwraps standard ApiResponse data and injects the shared auth headers', async () => {
    configureAuthContext(() => ({
      userId: 501,
      username: 'teacher01',
      role: 'TEACHER',
      permissions: ['grade:manage'],
      courseIds: [101],
      manageableCourseIds: [101]
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ ok: true }));

    const result = await request<{ ok: boolean }>('/api/v1/example', {
      method: 'POST',
      body: { name: 'demo' }
    });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/example', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        'Content-Type': 'application/json',
        'X-User-Id': '501',
        'X-Username': 'teacher01',
        'X-User-Role': 'TEACHER',
        'X-Permissions': 'grade:manage',
        'X-Course-Ids': '101',
        'X-Manageable-Course-Ids': '101'
      }),
      body: JSON.stringify({ name: 'demo' })
    }));
  });

  it('throws the backend message when the standard response is not successful', async () => {
    configureAuthContext(() => ({
      userId: 601,
      role: 'STUDENT',
      permissions: [],
      courseIds: [101],
      manageableCourseIds: []
    }));
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      json: async () => ({
        code: 'ERR-AUTH-03',
        message: '无权限访问',
        data: null
      })
    } as Response);

    await expect(request('/api/v1/forbidden')).rejects.toThrow('无权限访问');
  });

  it('fails fast before network calls when no login context exists', async () => {
    vi.spyOn(globalThis, 'fetch');

    await expect(request('/api/v1/example')).rejects.toThrow('当前登录态缺失');
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
