export type LearningTaskType = 'RESOURCE' | 'EXPERIMENT' | 'HOMEWORK';
export type LearningTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE';
export type LearningTaskSortBy = 'deadline' | 'createdAt';
export type SortOrder = 'asc' | 'desc';

export interface LearningTask {
  taskId: number;
  taskType: LearningTaskType;
  title: string;
  courseId: number;
  courseName: string;
  deadline: string | null;
  progress: number;
  status: LearningTaskStatus;
  actionUrl: string | null;
}

export interface LearningTaskPage {
  records: LearningTask[];
  total: number;
  page: number;
  size: number;
}

export interface LearningTaskQuery {
  taskType?: LearningTaskType[];
  status?: LearningTaskStatus;
  courseId?: number;
  sortBy?: LearningTaskSortBy;
  order?: SortOrder;
  page?: number;
  size?: number;
}
