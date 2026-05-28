import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearAuthSession, getCurrentUser, login, logout, register } from '../../../src/api/auth/auth';

describe('AUTH API client', () => {
  afterEach(() => {
    clearAuthSession();
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('registers users through the documented AUTH endpoint without requiring existing login state', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
      id: 45,
      username: 'student45',
      userType: 'STUDENT',
      displayName: '学生45',
      roles: ['STUDENT'],
      permissions: ['course:view']
    }));

    const result = await register({
      username: 'student45',
      password: 'Student45@pass',
      userType: 'STUDENT',
      displayName: '学生45'
    });

    expect(result.username).toBe('student45');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        username: 'student45',
        password: 'Student45@pass',
        userType: 'STUDENT',
        displayName: '学生45'
      })
    }));
  });

  it('stores token and current user context after login then uses bearer auth for me and logout', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        token: 'token-45',
        expiresAt: '2026-05-28T18:00:00',
        user: {
          id: 45,
          username: 'student45',
          userType: 'STUDENT',
          displayName: '学生45',
          roles: ['STUDENT'],
          permissions: ['course:view']
        }
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 45,
        username: 'student45',
        userType: 'STUDENT',
        displayName: '学生45',
        roles: ['STUDENT'],
        permissions: ['course:view']
      }))
      .mockResolvedValueOnce(jsonResponse(null));

    const result = await login({ account: 'student45', password: 'Student45@pass' });
    expect(result.token).toBe('token-45');
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBe('token-45');
    expect(window.localStorage.getItem('onlinejudge.userId')).toBe('45');
    expect(window.localStorage.getItem('onlinejudge.userRole')).toBe('STUDENT');
    expect(window.localStorage.getItem('onlinejudge.permissions')).toBe('course:view');

    await getCurrentUser();
    await logout();

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer token-45' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/logout', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer token-45' })
    }));
    expect(window.localStorage.getItem('onlinejudge.authToken')).toBeNull();
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
