import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentCourse, currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';
import { readAuthStorage, removeAuthStorage, writeAuthStorage } from '../../../src/api/auth/storage';
import { configureAuthContext, request, requestBlob } from '../../../src/api/http';

describe('shared API request client', () => {
  afterEach(() => {
    configureAuthContext(null);
    resetRuntimeContext();
    vi.restoreAllMocks();
    removeAuthStorage('onlinejudge.authToken');
    removeAuthStorage('onlinejudge.userId');
    removeAuthStorage('onlinejudge.username');
    removeAuthStorage('onlinejudge.userRole');
    removeAuthStorage('onlinejudge.role');
    window.history.pushState({}, '', '/');
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

  it('does not use configured role context as runtime header auth when no bearer token exists', async () => {
    configureAuthContext(() => ({
      userId: 701,
      username: 'teacher701',
      role: 'TEACHER',
      permissions: ['course:manage'],
      courseIds: '*',
      manageableCourseIds: '*'
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    await expect(request<{ list: unknown[] }>('/api/v1/courses')).rejects.toThrow('当前登录态缺失');

    expect(fetchMock).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe('/login');
  });

  it('downloads binary resources through bearer-authenticated fetch', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    const blob = new Blob(['course material'], { type: 'application/pdf' });
    const headers = new Headers({
      'Content-Disposition': "attachment; filename*=UTF-8''lesson.pdf"
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      headers,
      blob: async () => blob
    } as Response);

    const result = await requestBlob('/api/v1/courses/1/resources/2/download');

    expect(result.blob).toBe(blob);
    expect(result.filename).toBe('lesson.pdf');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/courses/1/resources/2/download', expect.objectContaining({
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer student-token'
      }
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
        code: 'ERR-GRD-01',
        message: '无权限访问',
        data: null
      })
    } as Response);

    await expect(request('/api/v1/forbidden')).rejects.toThrow('无权限访问');
  });

  it('reports plain-text server errors without leaking json parse failures', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      statusText: 'Forbidden',
      text: async () => 'Invalid CORS request'
    } as Response);

    await expect(request('/api/v1/auth/login', {
      method: 'POST',
      auth: false,
      body: { account: 'student', password: 'pass' }
    })).rejects.toThrow('Invalid CORS request');
  });

  it('clears persisted and cached session state for authenticated API 401/session-expired responses', async () => {
    writeAuthStorage('onlinejudge.authToken', 'expired-token');
    writeAuthStorage('onlinejudge.userId', '601');
    writeAuthStorage('onlinejudge.userRole', 'STUDENT');
    seedRuntimeContext();
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({
        code: 'ERR-AUTH-04',
        message: '登录已失效，请重新登录',
        data: null
      })
    } as Response);

    await expect(request('/api/v1/users/me')).rejects.toThrow('登录已失效，请重新登录');

    expect(readAuthStorage('onlinejudge.authToken')).toBeNull();
    expect(currentUser.value).toBeNull();
    expect(currentCourse.value).toBeNull();
    expect(window.location.pathname).toBe('/session-expired');
  });

  it('clears persisted and cached session state for disabled or locked account responses', async () => {
    writeAuthStorage('onlinejudge.authToken', 'locked-token');
    writeAuthStorage('onlinejudge.userId', '602');
    writeAuthStorage('onlinejudge.userRole', 'STUDENT');
    seedRuntimeContext();
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({
        code: 'ERR-AUTH-03',
        message: '账号已被禁用、冻结或锁定',
        data: null
      })
    } as Response);

    await expect(request('/api/v1/users/me')).rejects.toThrow('账号已被禁用、冻结或锁定');

    expect(readAuthStorage('onlinejudge.authToken')).toBeNull();
    expect(currentUser.value).toBeNull();
    expect(currentCourse.value).toBeNull();
    expect(window.location.pathname).toBe('/account-disabled');
  });

  it('routes forbidden responses to the 403 page without clearing the active session', async () => {
    writeAuthStorage('onlinejudge.authToken', 'student-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      json: async () => ({
        code: 'ERR-AUTH-05',
        message: '无权限访问',
        data: null
      })
    } as Response);

    await expect(request('/api/v1/admin/roles')).rejects.toThrow('无权限访问');

    expect(readAuthStorage('onlinejudge.authToken')).toBe('student-token');
    expect(window.location.pathname).toBe('/403');
  });

  it('fails fast before network calls when no login context exists', async () => {
    vi.spyOn(globalThis, 'fetch');

    await expect(request('/api/v1/example')).rejects.toThrow('当前登录态缺失');
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe('/login');
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

function seedRuntimeContext() {
  currentUser.value = {
    id: 601,
    username: 'student601',
    userType: 'STUDENT',
    displayName: '学生 601',
    roles: ['STUDENT'],
    permissions: []
  };
  currentCourse.value = {
    id: 9501,
    name: '软件工程基础',
    teacherId: 501,
    teacherName: '教师 501',
    enrollmentMode: 'PUBLIC',
    status: 'ACTIVE',
    memberCount: 1,
    member: true,
    manageable: false,
    createdAt: '2026-08-21T00:00:00Z',
    updatedAt: '2026-08-21T00:00:00Z'
  };
}
