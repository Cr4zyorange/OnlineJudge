import type { NotificationMutationResult, NotificationPage, NotificationQuery, NotificationReadRequest } from '../../types/lrn';
import { request } from '../http';

export async function listNotifications(query: NotificationQuery = {}): Promise<NotificationPage> {
  const params = new URLSearchParams();
  appendParam(params, 'type', query.type);
  if (query.isRead !== undefined) {
    appendParam(params, 'isRead', String(query.isRead));
  }
  appendParam(params, 'startTime', query.startTime);
  appendParam(params, 'endTime', query.endTime);
  appendParam(params, 'page', query.page);
  appendParam(params, 'size', query.size);
  const queryString = params.toString();
  return request<NotificationPage>(queryString ? `/api/v1/notifications?${queryString}` : '/api/v1/notifications');
}

export async function markNotificationsRead(payload: NotificationReadRequest): Promise<NotificationMutationResult> {
  return request<NotificationMutationResult>('/api/v1/notifications/read', {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function deleteNotification(notificationId: number): Promise<NotificationMutationResult> {
  return request<NotificationMutationResult>(`/api/v1/notifications/${notificationId}`, {
    method: 'DELETE'
  });
}

function appendParam(params: URLSearchParams, name: string, value: string | number | undefined) {
  if (value === undefined || value === '') {
    return;
  }
  params.append(name, String(value));
}
