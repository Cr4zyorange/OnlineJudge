import type { ReminderRuleOverview } from '../../types/lrn';
import { request } from '../http';

export async function getReminderRules(): Promise<ReminderRuleOverview> {
  return request<ReminderRuleOverview>('/api/v1/reminder-rules');
}

export async function saveReminderRules(payload: ReminderRuleOverview): Promise<ReminderRuleOverview> {
  return request<ReminderRuleOverview>('/api/v1/reminder-rules', {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}
