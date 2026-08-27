import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { writeAuthStorage, removeAuthStorage } from '../../../src/api/auth/storage';
import * as notificationsApi from '../../../src/api/lrn/notifications';
import {
  resetNotificationUnreadState,
  useNotificationUnread
} from '../../../src/lrn/notificationUnreadState';
import { currentUser, resetRuntimeContext } from '../../../src/app/runtimeContext';

vi.mock('../../../src/api/lrn/notifications');

const Consumer = defineComponent({
  template: '<output data-testid="unread-count">{{ unreadCount }}</output>',
  setup() {
    return useNotificationUnread({ pollIntervalMs: 50 });
  }
});

describe('notification unread state', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetAllMocks();
    resetNotificationUnreadState();
    resetRuntimeContext();
    writeAuthStorage('onlinejudge.authToken', 'student-session-a');
    currentUser.value = user(101, 'student-a');
  });

  afterEach(() => {
    mountedConsumers.splice(0).forEach((wrapper) => wrapper.unmount());
    resetNotificationUnreadState();
    resetRuntimeContext();
    removeAuthStorage('onlinejudge.authToken');
    vi.useRealTimers();
  });

  it('loads the unread count on mount and refreshes it without overlapping requests', async () => {
    let resolveFirst: ((value: Awaited<ReturnType<typeof notificationsApi.listNotifications>>) => void) | undefined;
    vi.mocked(notificationsApi.listNotifications)
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve;
      }))
      .mockResolvedValue(notificationPage(4));

    const wrapper = mount(Consumer);
    mountedConsumers.push(wrapper);
    await flushPromises();

    expect(notificationsApi.listNotifications).toHaveBeenCalledWith({ page: 1, size: 1 });
    await vi.advanceTimersByTimeAsync(200);
    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(1);

    resolveFirst?.(notificationPage(4));
    await flushPromises();
    expect(wrapper.get('[data-testid="unread-count"]').text()).toBe('4');

    await vi.advanceTimersByTimeAsync(50);
    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(2);
  });

  it('discards an in-flight response after the authenticated user changes', async () => {
    let resolveFirst: ((value: Awaited<ReturnType<typeof notificationsApi.listNotifications>>) => void) | undefined;
    vi.mocked(notificationsApi.listNotifications)
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve;
      }))
      .mockResolvedValueOnce(notificationPage(1));

    const wrapper = mount(Consumer);
    mountedConsumers.push(wrapper);
    await flushPromises();

    writeAuthStorage('onlinejudge.authToken', 'student-session-b');
    currentUser.value = user(202, 'student-b');
    await flushPromises();

    resolveFirst?.(notificationPage(99));
    await flushPromises();

    expect(wrapper.get('[data-testid="unread-count"]').text()).toBe('1');
  });

  it('stops polling after the consumer unmounts or the session is cleared', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(notificationPage(2));

    const wrapper = mount(Consumer);
    mountedConsumers.push(wrapper);
    await flushPromises();
    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(1);

    resetRuntimeContext();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(200);
    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(200);
    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(1);
  });
});

const mountedConsumers: Array<{ unmount: () => void }> = [];

function notificationPage(unreadCount: number) {
  return {
    records: [],
    total: unreadCount,
    page: 1,
    size: 1,
    unreadCount
  };
}

function user(id: number, username: string) {
  return {
    id,
    username,
    userType: 'STUDENT',
    displayName: username,
    roles: ['STUDENT'],
    permissions: []
  };
}
