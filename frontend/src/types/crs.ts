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
