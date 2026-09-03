export type CourseStatus = 'DRAFT' | 'NOT_STARTED' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED';
export type EnrollmentMode = 'PUBLIC' | 'INVITE' | 'REVIEW';

export interface Course {
  id: number;
  name: string;
  description?: string;
  teacherId: number;
  teacherName: string;
  semester?: string;
  category?: string;
  coverUrl?: string;
  enrollmentMode: EnrollmentMode;
  inviteCode?: string;
  maxStudents?: number;
  startDate?: string;
  endDate?: string;
  status: CourseStatus;
  memberCount: number;
  member: boolean;
  manageable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CoursePayload {
  name: string;
  description?: string;
  semester?: string;
  category?: string;
  coverUrl?: string;
  enrollmentMode: EnrollmentMode;
  inviteCode?: string;
  maxStudents?: number;
  startDate?: string;
  endDate?: string;
  status: CourseStatus;
}

export interface CourseJoinPayload {
  inviteCode?: string;
  applyReason?: string;
}

export interface CoursePermission {
  courseId: number;
  userId: number;
  member: boolean;
  teacher: boolean;
  role?: 'TEACHER' | 'ASSISTANT' | 'STUDENT';
  status?: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REMOVED';
}

export interface CourseMember {
  courseId: number;
  userId: number;
  role: 'TEACHER' | 'ASSISTANT' | 'STUDENT';
  status: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REMOVED';
  joinMethod: EnrollmentMode | 'CREATED';
  approvedBy?: number | null;
  joinedAt?: string | null;
}

export interface Chapter {
  id: number;
  courseId: number;
  parentId?: number | null;
  chapterName: string;
  sortOrder: number;
  objective?: string;
  visibleStatus: 0 | 1;
  chapterType: 1 | 2 | 3;
  children: Chapter[];
  createdAt: string;
  updatedAt: string;
}

export interface ChapterPayload {
  parentId?: number | null;
  chapterName: string;
  sortOrder?: number;
  objective?: string;
  visibleStatus?: 0 | 1;
  chapterType?: 1 | 2 | 3;
}

export type ResourceType = 'DOCUMENT' | 'COURSEWARE' | 'VIDEO' | 'IMAGE' | 'ARCHIVE' | 'LINK' | 'OTHER';
export type ResourceVisibility = 'STUDENT' | 'TEACHER';

export interface CourseResource {
  id: number;
  courseId: number;
  chapterId?: number | null;
  name: string;
  resourceType: ResourceType;
  visibility: ResourceVisibility;
  publishAt?: string | null;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadUserId: number;
  downloadUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface CourseAnnouncement {
  id: number;
  courseId: number;
  title: string;
  content: string;
  top: boolean;
  publisherId: number;
  publisherName: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnnouncementPayload {
  title: string;
  content: string;
  isTop?: boolean;
}

export interface CourseRecentTask {
  taskId: number;
  taskType: 'RESOURCE' | 'EXPERIMENT' | 'HOMEWORK';
  title: string;
  courseId: number;
  courseName: string;
  deadline?: string | null;
  progress: number;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE';
  actionUrl?: string | null;
}

export interface CourseHomeSummary {
  course: Course;
  announcements: CourseAnnouncement[];
  recentTasks: CourseRecentTask[];
}

/** API-CRS-15 is paged even where the current teacher detail only renders its first page. */
export interface CourseMemberPage {
  items: CourseMember[];
  page: number;
  size: number;
  total: number;
}

export interface ResourcePayload {
  chapterId?: number | null;
  name: string;
  resourceType: ResourceType;
  visibility: ResourceVisibility;
  publishAt?: string | null;
}

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}
