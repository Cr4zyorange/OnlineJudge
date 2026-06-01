import type { LearningTaskPage, LearningTaskQuery } from '../../types/lrn';
import { configureAuthContext, request } from '../http';

export interface LearningTaskAuthContext {
  userId: number | string;
  userRole: 'STUDENT';
  courseIds: Array<number | string> | '*';
}

type LearningTaskAuthContextProvider = () => LearningTaskAuthContext | null;

let authContextProvider: LearningTaskAuthContextProvider | null = null;

export function configureLearningTaskAuthContext(provider: LearningTaskAuthContextProvider | null) {
  authContextProvider = provider;
  configureAuthContext(() => {
    const context = authContextProvider?.();
    if (!context) {
      return null;
    }
    return {
      userId: context.userId,
      role: context.userRole,
      courseIds: context.courseIds
    };
  });
}

export async function listLearningTasks(query: LearningTaskQuery = {}): Promise<LearningTaskPage> {
  const params = new URLSearchParams();
  appendParam(params, 'taskType', query.taskType?.join(','));
  appendParam(params, 'status', query.status);
  appendParam(params, 'courseId', query.courseId);
  appendParam(params, 'sortBy', query.sortBy);
  appendParam(params, 'order', query.order);
  appendParam(params, 'page', query.page);
  appendParam(params, 'size', query.size);
  const queryString = params.toString();
  return request<LearningTaskPage>(queryString ? `/api/v1/learning/tasks?${queryString}` : '/api/v1/learning/tasks');
}

function appendParam(params: URLSearchParams, name: string, value: string | number | undefined) {
  if (value === undefined || value === '') {
    return;
  }
  params.append(name, String(value));
}
