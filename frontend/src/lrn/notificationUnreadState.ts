import { computed, onBeforeUnmount, onMounted, readonly, ref, watch } from 'vue';
import { readAuthStorage } from '../api/auth/storage';
import { listNotifications } from '../api/lrn/notifications';
import { currentUser } from '../app/runtimeContext';

export const DEFAULT_NOTIFICATION_POLL_INTERVAL_MS = 1_000;

export interface NotificationUnreadOptions {
  pollIntervalMs?: number;
}

const unreadCount = ref(0);
export const notificationUnreadCount = readonly(unreadCount);
const activeSessionKey = ref<string | null>(null);
let activeRequest: Promise<void> | null = null;
let activeRequestSessionKey: string | null = null;
let scheduledPoll: ReturnType<typeof setTimeout> | null = null;
let pollIntervalMs = DEFAULT_NOTIFICATION_POLL_INTERVAL_MS;
let consumerCount = 0;
let stateGeneration = 0;

export function useNotificationUnread(options: NotificationUnreadOptions = {}) {
  const interval = normalizePollInterval(options.pollIntervalMs);
  const sessionKey = computed(currentSessionKey);

  const stopWatchingSession = watch(sessionKey, () => {
    reconcileSession();
    void refreshNotificationUnreadCount();
  });

  onMounted(() => {
    consumerCount += 1;
    pollIntervalMs = Math.min(pollIntervalMs, interval);
    reconcileSession();
    void refreshNotificationUnreadCount();
    schedulePoll();
  });

  onBeforeUnmount(() => {
    stopWatchingSession();
    consumerCount = Math.max(0, consumerCount - 1);
    if (consumerCount === 0) {
      clearScheduledPoll();
    }
  });

  return {
    unreadCount: readonly(unreadCount),
    refreshUnreadCount: refreshNotificationUnreadCount,
    syncUnreadCount: syncNotificationUnreadCount
  };
}

export async function refreshNotificationUnreadCount(): Promise<void> {
  const sessionKey = reconcileSession();
  if (!sessionKey) {
    return;
  }
  if (activeRequest) {
    if (activeRequestSessionKey === sessionKey) {
      return activeRequest;
    }
    await activeRequest;
    return refreshNotificationUnreadCount();
  }

  const requestGeneration = stateGeneration;
  activeRequestSessionKey = sessionKey;
  activeRequest = (async () => {
    try {
      const page = await listNotifications({ page: 1, size: 1 });
      if (requestGeneration === stateGeneration && currentSessionKey() === sessionKey) {
        unreadCount.value = page.unreadCount;
      }
    } finally {
      activeRequest = null;
      activeRequestSessionKey = null;
    }
  })();
  return activeRequest;
}

export function syncNotificationUnreadCount(nextUnreadCount: number) {
  unreadCount.value = Number.isFinite(nextUnreadCount) ? Math.max(0, Math.trunc(nextUnreadCount)) : 0;
}

export function resetNotificationUnreadState() {
  stateGeneration += 1;
  unreadCount.value = 0;
  activeSessionKey.value = null;
  activeRequest = null;
  activeRequestSessionKey = null;
  consumerCount = 0;
  pollIntervalMs = DEFAULT_NOTIFICATION_POLL_INTERVAL_MS;
  clearScheduledPoll();
}

function schedulePoll() {
  if (scheduledPoll || consumerCount === 0 || !reconcileSession()) {
    return;
  }
  scheduledPoll = setTimeout(async () => {
    scheduledPoll = null;
    await refreshNotificationUnreadCount();
    schedulePoll();
  }, pollIntervalMs);
}

function clearScheduledPoll() {
  if (scheduledPoll) {
    clearTimeout(scheduledPoll);
    scheduledPoll = null;
  }
}

function reconcileSession(): string | null {
  const key = currentSessionKey();
  if (activeSessionKey.value === key) {
    return key;
  }
  stateGeneration += 1;
  activeSessionKey.value = key;
  unreadCount.value = 0;
  if (!key) {
    clearScheduledPoll();
  }
  return key;
}

function currentSessionKey(): string | null {
  const token = readAuthStorage('onlinejudge.authToken');
  const userId = currentUser.value?.id ?? readAuthStorage('onlinejudge.userId');
  if (!token || userId === null || userId === undefined || String(userId).trim() === '') {
    return null;
  }
  return `${userId}:${token}`;
}

function normalizePollInterval(value: number | undefined) {
  if (!Number.isFinite(value) || value === undefined) {
    return DEFAULT_NOTIFICATION_POLL_INTERVAL_MS;
  }
  return Math.max(50, Math.trunc(value));
}
