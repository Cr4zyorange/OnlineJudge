import { readAuthStorage } from '../auth/storage';
import { request } from '../http';
import type {
  LearningRecordItem,
  LearningRecordRequest,
  LearningStatisticsOverview
} from '../../types/lrn';

const STATISTICS_CACHE_PREFIX = 'onlinejudge.learningStatisticsCache';
const RECORD_QUEUE_PREFIX = 'onlinejudge.learningRecordQueue';
const RECORD_QUEUE_COURSE_INDEX_PREFIX = 'onlinejudge.learningRecordQueueCourses';

export async function getLearningStatistics(courseId?: number): Promise<LearningStatisticsOverview> {
  const params = new URLSearchParams();
  if (courseId !== undefined) {
    params.append('courseId', String(courseId));
  }
  const queryString = params.toString();
  try {
    const data = await request<LearningStatisticsOverview>(
      queryString ? `/api/v1/learning/statistics?${queryString}` : '/api/v1/learning/statistics'
    );
    cacheLearningStatistics(data, courseId);
    return data;
  } catch (error) {
    const cached = shouldUseOfflineFallback() ? getCachedLearningStatistics(courseId) : null;
    if (cached) {
      return { ...cached, fromCache: true };
    }
    throw error;
  }
}

export async function reportLearningRecord(payload: LearningRecordRequest): Promise<LearningRecordItem> {
  try {
    return await postLearningRecord(payload);
  } catch (error) {
    if (shouldUseOfflineFallback()) {
      queueLearningRecord(payload);
      return syntheticRecord(payload);
    }
    throw error;
  }
}

export async function flushQueuedLearningRecords(courseId?: number): Promise<{ sent: number; remaining: number }> {
  if (!shouldUseOfflineFallback()) {
    return { sent: 0, remaining: countQueuedRecords(courseId) };
  }
  let sent = 0;
  for (const queuedCourseId of queuedCourseIds(courseId)) {
    const queued = getQueuedLearningRecords(queuedCourseId);
    const remaining: LearningRecordRequest[] = [];
    for (let index = 0; index < queued.length; index += 1) {
      const payload = queued[index];
      try {
        await postLearningRecord(payload);
        sent += 1;
      } catch {
        remaining.push(payload, ...queued.slice(index + 1));
        break;
      }
    }
    saveQueuedLearningRecords(queuedCourseId, remaining);
  }
  return { sent, remaining: countQueuedRecords(courseId) };
}

export function getCachedLearningStatistics(courseId?: number): LearningStatisticsOverview | null {
  try {
    const key = scopedStorageKey(STATISTICS_CACHE_PREFIX, courseId);
    if (!key) {
      return null;
    }
    const raw = safeGetItem(key);
    return raw ? JSON.parse(raw) as LearningStatisticsOverview : null;
  } catch {
    return null;
  }
}

export function getQueuedLearningRecords(courseId?: number): LearningRecordRequest[] {
  try {
    const key = scopedStorageKey(RECORD_QUEUE_PREFIX, courseId);
    if (!key) {
      return [];
    }
    const raw = safeGetItem(key);
    return raw ? JSON.parse(raw) as LearningRecordRequest[] : [];
  } catch {
    return [];
  }
}

function cacheLearningStatistics(data: LearningStatisticsOverview, courseId?: number) {
  const key = scopedStorageKey(STATISTICS_CACHE_PREFIX, courseId);
  if (!key) {
    return;
  }
  safeSetItem(key, JSON.stringify({
    ...data,
    fromCache: undefined
  }));
}

function queueLearningRecord(payload: LearningRecordRequest) {
  const key = scopedStorageKey(RECORD_QUEUE_PREFIX, payload.courseId);
  if (!key) {
    return;
  }
  rememberQueuedCourse(payload.courseId);
  const queued = getQueuedLearningRecords(payload.courseId);
  queued.push({
    ...payload,
    endedAt: payload.endedAt ?? new Date().toISOString()
  });
  safeSetItem(key, JSON.stringify(queued.slice(-50)));
}

function saveQueuedLearningRecords(courseId: number, records: LearningRecordRequest[]) {
  const key = scopedStorageKey(RECORD_QUEUE_PREFIX, courseId);
  if (!key) {
    return;
  }
  if (records.length === 0) {
    safeRemoveItem(key);
    forgetQueuedCourse(courseId);
    return;
  }
  rememberQueuedCourse(courseId);
  safeSetItem(key, JSON.stringify(records.slice(-50)));
}

function countQueuedRecords(courseId?: number) {
  return queuedCourseIds(courseId)
    .map((queuedCourseId) => getQueuedLearningRecords(queuedCourseId).length)
    .reduce((total, count) => total + count, 0);
}

function queuedCourseIds(courseId?: number) {
  if (courseId !== undefined) {
    return [courseId];
  }
  const indexKey = scopedStorageKey(RECORD_QUEUE_COURSE_INDEX_PREFIX);
  if (!indexKey) {
    return [];
  }
  try {
    const raw = safeGetItem(indexKey);
    const parsed = raw ? JSON.parse(raw) as number[] : [];
    return parsed.filter((item) => Number.isFinite(item));
  } catch {
    return [];
  }
}

function rememberQueuedCourse(courseId: number) {
  const indexKey = scopedStorageKey(RECORD_QUEUE_COURSE_INDEX_PREFIX);
  if (!indexKey) {
    return;
  }
  const courseIds = new Set(queuedCourseIds());
  courseIds.add(courseId);
  safeSetItem(indexKey, JSON.stringify([...courseIds]));
}

function forgetQueuedCourse(courseId: number) {
  const indexKey = scopedStorageKey(RECORD_QUEUE_COURSE_INDEX_PREFIX);
  if (!indexKey) {
    return;
  }
  const courseIds = queuedCourseIds().filter((item) => item !== courseId);
  if (courseIds.length === 0) {
    safeRemoveItem(indexKey);
    return;
  }
  safeSetItem(indexKey, JSON.stringify(courseIds));
}

function postLearningRecord(payload: LearningRecordRequest) {
  return request<LearningRecordItem>('/api/v1/learning/records', {
    method: 'POST',
    body: payload
  });
}

function syntheticRecord(payload: LearningRecordRequest): LearningRecordItem {
  const endedAt = payload.endedAt ?? new Date().toISOString();
  return {
    id: 0,
    courseId: payload.courseId,
    courseName: '',
    sourceModule: payload.sourceModule,
    sourceId: payload.sourceId,
    actionType: payload.actionType,
    durationSeconds: payload.durationSeconds ?? 0,
    startedAt: payload.startedAt ?? endedAt,
    endedAt
  };
}

function scopedStorageKey(prefix: string, courseId?: number) {
  const userId = readAuthStorage('onlinejudge.userId');
  if (!userId) {
    return null;
  }
  const courseScope = courseId === undefined ? 'all' : String(courseId);
  return `${prefix}:user:${userId}:course:${courseScope}`;
}

function shouldUseOfflineFallback() {
  if (!readAuthStorage('onlinejudge.authToken') || !readAuthStorage('onlinejudge.userId')) {
    return false;
  }
  if (typeof window === 'undefined') {
    return true;
  }
  return !['/login', '/session-expired', '/account-disabled', '/403'].includes(window.location.pathname);
}

function safeGetItem(key: string) {
  const storage = safeLocalStorage();
  return storage ? storage.getItem(key) : null;
}

function safeSetItem(key: string, value: string) {
  const storage = safeLocalStorage();
  if (storage) {
    storage.setItem(key, value);
  }
}

function safeRemoveItem(key: string) {
  const storage = safeLocalStorage();
  if (storage) {
    storage.removeItem(key);
  }
}

function safeLocalStorage() {
  if (typeof window === 'undefined') {
    return null;
  }
  const storage = window.localStorage;
  if (
    typeof storage?.getItem === 'function'
    && typeof storage.setItem === 'function'
    && typeof storage.removeItem === 'function'
  ) {
    return storage;
  }
  return null;
}

if (typeof window !== 'undefined') {
  window.addEventListener('online', () => {
    void flushQueuedLearningRecords();
  });
}
