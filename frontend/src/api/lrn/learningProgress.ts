import type {
  LearningProgressItem,
  LearningProgressOverview,
  LearningProgressSaveRequest
} from '../../types/lrn';
import { request } from '../http';

export async function getLearningProgress(courseId?: number): Promise<LearningProgressOverview> {
  const params = new URLSearchParams();
  if (courseId !== undefined) {
    params.append('courseId', String(courseId));
  }
  const queryString = params.toString();
  return request<LearningProgressOverview>(
    queryString ? `/api/v1/learning/progress?${queryString}` : '/api/v1/learning/progress'
  );
}

export async function saveLearningProgress(
  payload: LearningProgressSaveRequest
): Promise<LearningProgressItem> {
  return request<LearningProgressItem>('/api/v1/learning/progress', {
    method: 'POST',
    body: payload
  });
}
