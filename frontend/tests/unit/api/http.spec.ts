import { afterEach, describe, expect, it, vi } from 'vitest';
import { removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import { configureAuthContext, request } from '../../../src/api/http';

describe('shared API request client', () => {
  afterEach(() => {
    configureAuthContext(null);
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
  });

  it('unwraps standard ApiResponse data and injects the bearer token instead of user-controlled headers', async () => {
    writeAuthStorage('onlinejudge.authToken', 'session-token');
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
        Authorization: 'Bearer session-token'
      }),
      body: JSON.stringify({ name: 'demo' })
    }));
    expect(fetchMock).not.toHaveBeenCalledWith('/api/v1/example', expect.objectContaining({
      headers: expect.objectContaining({ 'X-User-Role': expect.any(String) })
    }));
  });

  it('keeps bearer auth for multipart submissions without forcing a json content type', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ submissionId: 99 }));
    const formData = new FormData();
    formData.append('language', 'python');
    formData.append('file', new File(['print(1)'], 'main.py', { type: 'text/x-python' }));

    const result = await request<{ submissionId: number }>('/api/v1/labs/7/submissions', {
      method: 'POST',
      body: formData
    });

    expect(result).toEqual({ submissionId: 99 });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/labs/7/submissions', expect.objectContaining({
      method: 'POST',
      headers: {
        Authorization: 'Bearer student-token'
      },
      body: formData
    }));
  });

  it('uses the configured role context as header auth when no bearer token exists', async () => {
    configureAuthContext(() => ({
      userId: 701,
      username: 'teacher701',
      role: 'TEACHER',
      permissions: ['course:manage'],
      courseIds: '*',
      manageableCourseIds: '*'
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ list: [] }));

    await expect(request<{ list: unknown[] }>('/api/v1/courses')).resolves.toEqual({ list: [] });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses', expect.objectContaining({
      headers: expect.objectContaining({
        'X-User-Id': '701',
        'X-User-Role': 'TEACHER',
        'X-Username': 'teacher701',
        'X-Permissions': 'course:manage',
        'X-Course-Ids': '*',
        'X-Manageable-Course-Ids': '*'
      })
    }));
  });

  it('accepts the legacy success response code used by older backend processes', async () => {
    writeAuthStorage('onlinejudge.authToken', 'session-token');
    configureAuthContext(() => ({
      userId: 501,
      role: 'TEACHER',
      permissions: [],
      courseIds: '*',
      manageableCourseIds: '*'
    }));
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 200,
        message: 'success',
        data: { list: [] }
      })
    } as Response);

    await expect(request<{ list: unknown[] }>('/api/v1/courses')).resolves.toEqual({ list: [] });
  });

  it('throws the backend message when the standard response is not successful', async () => {
    writeAuthStorage('onlinejudge.authToken', 'session-token');
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
