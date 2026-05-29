export type GradeItemSourceType = 'LAB' | 'HWK' | 'OTHER_COURSE_ITEM';

export interface GradeItem {
  id: number;
  courseId: number;
  name: string;
  sourceType: GradeItemSourceType;
  sourceId: number | null;
  fullScore: string;
  weight: string;
  includedInFinal: boolean;
  enabled: boolean;
  sortOrder: number;
}

export interface CreateGradeItemPayload {
  name: string;
  sourceType: GradeItemSourceType;
  sourceId: number | null;
  fullScore: string;
  weight: string;
  includedInFinal: boolean;
  sortOrder: number;
}

export interface UpdateGradeItemPayload extends CreateGradeItemPayload {
  enabled?: boolean;
}

export interface GradeRuleValidationResult {
  valid: boolean;
  totalIncludedWeight: string;
  errors: string[];
}

export type GradeStatus = 'SCORED' | 'UNSUBMITTED' | 'UNGRADED' | 'MISSING' | 'ADJUSTED';
export type PublishStatus = 'UNPUBLISHED' | 'PUBLISHED';
export type FinalStatus = 'CALCULATED' | 'INCOMPLETE' | 'ADJUSTED';

export interface GradeRecord {
  id: number;
  courseId: number;
  studentId: number;
  gradeItemId: number;
  sourceType: GradeItemSourceType;
  sourceId: number | null;
  rawScore: string | null;
  weightedScore: string | null;
  gradeStatus: GradeStatus;
  publishStatus: PublishStatus;
  publishedAt?: string | null;
}

export interface GradeAdjustmentPayload {
  newScore: string;
  reason: string;
}

export interface GradeAdjustmentResult {
  recordId: number;
  studentId: number;
  gradeItemId: number | null;
  oldScore: string | null;
  newScore: string;
  reason: string;
  updatedAt: string;
}

export interface FinalScoreAdjustmentResult {
  summaryId: number;
  studentId: number;
  oldScore: string | null;
  newScore: string;
  reason: string;
  updatedAt: string;
}

export interface GradeChangeLog {
  id: number;
  courseId: number;
  studentId: number;
  gradeItemId: number | null;
  changeType: 'RECORD_ADJUST' | 'FINAL_ADJUST';
  oldValue: string | null;
  newValue: string | null;
  reason: string;
  operatorId: number;
  createdAt: string;
}

export interface GradeChangeLogPage {
  records: GradeChangeLog[];
  total: number;
  page: number;
  size: number;
}

export interface CourseGradeSummary {
  id: number;
  courseId: number;
  studentId: number;
  finalScore: string | null;
  finalStatus: FinalStatus;
  publishStatus: PublishStatus;
  publishedAt?: string | null;
}

export interface CourseGradeRow {
  studentId: number;
  summary: CourseGradeSummary | null;
  records: GradeRecord[];
}

export interface CourseGradeTablePage {
  records: CourseGradeRow[];
  total: number;
  page: number;
  size: number;
}

export interface GradeSyncResult {
  calculationBatchId: number;
  affectedItemCount: number;
  affectedStudentCount: number;
  syncedCount: number;
  missingCount: number;
  ungradedCount: number;
}

export interface GradeRecalculationResult {
  calculationBatchId: number;
  affectedCount: number;
}

export type GradePublishScope = 'COURSE' | 'PARTIAL_STUDENTS' | 'PARTIAL_ITEMS';

export interface GradePublishPayload {
  publishScope: GradePublishScope;
  studentIds: number[];
  gradeItemIds: number[];
}

export interface GradePublishResult {
  publishId: number;
  publishedCount: number;
  publishedAt: string;
  notificationStatus: 'SENT' | 'FAILED';
}

export interface GradePublishRecord {
  id: number;
  courseId: number;
  publishScope: GradePublishScope;
  publishedCount: number;
  publishedBy: number;
  publishedAt: string;
  notificationStatus: 'SENT' | 'FAILED';
  remark: string | null;
}

export interface GradePublishRecordPage {
  records: GradePublishRecord[];
  total: number;
  page: number;
  size: number;
}
