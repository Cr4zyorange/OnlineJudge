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

export type LearningProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
export type LearningProgressSourceModule = 'CRS' | 'LAB' | 'HWK';

export interface LearningProgressItem {
  progressId: number;
  courseId: number;
  courseName: string;
  chapterId: number | null;
  chapterName: string | null;
  sourceModule: LearningProgressSourceModule;
  sourceId: number;
  progressPercent: number;
  lastPosition: string | null;
  status: LearningProgressStatus;
  continueUrl: string;
  updatedAt: string;
}

export interface LearningChapterProgress {
  chapterId: number;
  chapterName: string;
  progressPercent: number;
  status: LearningProgressStatus;
  lastPosition: string | null;
  continueUrl: string | null;
  updatedAt: string | null;
  records: LearningProgressItem[];
}

export interface LearningCourseProgress {
  courseId: number;
  courseName: string;
  progressPercent: number;
  status: LearningProgressStatus;
  lastPosition: string | null;
  continueUrl: string | null;
  updatedAt: string | null;
  continueLearning: LearningProgressItem | null;
  chapters: LearningChapterProgress[];
}

export interface LearningProgressOverview {
  courses: LearningCourseProgress[];
  total: number;
}

export interface LearningProgressSaveRequest {
  courseId: number;
  chapterId?: number | null;
  sourceModule: LearningProgressSourceModule;
  sourceId: number;
  progressPercent: number;
  lastPosition?: string | null;
}
