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
}

export interface CourseGradeSummary {
  id: number;
  courseId: number;
  studentId: number;
  finalScore: string | null;
  finalStatus: FinalStatus;
  publishStatus: PublishStatus;
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
