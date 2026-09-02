export type LabExperimentStatus =
  | 'DRAFT'
  | 'NOT_OPEN'
  | 'PUBLISHED'
  | 'CLOSED'
  | 'SCORE_PUBLISHED'
  | 'ARCHIVED';

export type LabEvaluationMode = 'DOCKER_IO' | 'MIXED' | 'MANUAL';

/**
 * Assessment persists LAB submissions under UUIDs. Numeric values are accepted
 * only for legacy local fixtures; live service values are opaque strings and
 * must travel unchanged through routes and API calls.
 */
export type LabSubmissionId = string | number;

export interface LabTestcase {
  id: number;
  labId: number;
  input: string;
  expectedOutput: string;
  scoreWeight: number;
  public: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  orderNum: number;
}

export interface LabTestcasePayload {
  input: string;
  expectedOutput: string;
  scoreWeight: number;
  public: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  orderNum: number;
}

export interface LabExperimentSummary {
  id: number;
  courseId: number;
  title: string;
  status: LabExperimentStatus;
  deadline: string;
  maxScore: number;
  evaluationMode: LabEvaluationMode;
  autoEvaluate: boolean;
  reportRequired: boolean;
  publishedAt?: string | null;
  deleted: boolean;
}

export interface LabExperimentDetail extends LabExperimentSummary {
  chapterId: number | null;
  description: string;
  attachmentIds: number[];
  allowedLanguages: string | null;
  timeLimitMs: number;
  memoryLimitKb: number;
  testcases: LabTestcase[];
}

export interface LabExperimentPayload {
  chapterId?: number | null;
  title: string;
  description: string;
  deadline: string;
  maxScore: number;
  attachmentIds: number[];
  allowedLanguages: string | null;
  evaluationMode: LabEvaluationMode;
  autoEvaluate: boolean;
  reportRequired: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  testcases: LabTestcasePayload[];
}

export interface LabSubmissionPayload {
  language: string;
  code?: string;
  file?: File;
}

export interface LabReportUploadPayload {
  submissionId?: LabSubmissionId;
  reportFile: File;
}

export interface LabReportSummary {
  reportId: number;
  submissionId: LabSubmissionId | null;
  fileName: string;
  fileType: 'PDF' | 'DOCX' | 'ZIP';
  fileSize: number;
  version: number;
  score: number | null;
  comment: string | null;
  submittedAt: string;
  downloadUrl: string;
}

export interface LabReportDetail extends LabReportSummary {}

export interface LabReportScorePayload {
  score: number;
  comment: string;
}

export interface LabScorePayload {
  manualScore: number;
  reportScore?: number | null;
  finalScore: number;
  comment?: string | null;
  changeReason?: string | null;
}

export interface LabScoreSummary {
  submissionId: LabSubmissionId;
  reportId: number | null;
  autoScore: number | null;
  reportScore: number | null;
  manualScore: number | null;
  finalScore: number;
  comment: string | null;
  hasChangeLogs: boolean;
  scoredAt: string;
  updatedAt: string;
}

export interface LabSubmissionSummary {
  submissionId: LabSubmissionId;
  labId: number;
  studentId: number;
  submitStatus: 'SUBMITTED' | 'LATE' | 'WITHDRAWN';
  evaluationStatus:
    | 'NONE'
    | 'PENDING'
    | 'RUNNING'
    | 'ACCEPTED'
    | 'WRONG_ANSWER'
    | 'COMPILE_ERROR'
    | 'RUNTIME_ERROR'
    | 'TIME_LIMIT_EXCEEDED'
    | 'SYSTEM_ERROR';
  autoScore?: number | null;
  version: number;
  submittedAt: string;
}

export interface LabSubmissionHistoryItem {
  submissionId: LabSubmissionId;
  labId: number;
  studentId: number;
  language: string;
  submitStatus: LabSubmissionSummary['submitStatus'];
  evaluationStatus: LabSubmissionSummary['evaluationStatus'];
  autoScore: number | null;
  finalScore: number | null;
  version: number;
  submittedAt: string;
  isLatest: boolean;
  isFinal: boolean;
  isScoringBasis: boolean;
  hasFile: boolean;
}

export interface LabSubmissionSourceFile {
  originalFilename: string;
  contentType: string;
  fileSize: number;
  downloadAvailable: boolean;
}

export interface LabSubmissionDetail extends LabSubmissionHistoryItem {
  code: string | null;
  sourceFile: LabSubmissionSourceFile | null;
  latestReport: LabReportSummary | null;
  latestScore?: LabScoreSummary | null;
}

export interface LabEvaluationCaseResult {
  testcaseId: number;
  orderNum: number;
  passed: boolean;
  score: number;
  input: string;
  expectedOutput: string;
  actualOutput: string;
  message: string;
}

export interface LabSubmissionResult {
  submissionId: LabSubmissionId;
  evaluationStatus: LabSubmissionSummary['evaluationStatus'];
  score: number;
  passedCases: number;
  totalCases: number;
  message: string;
  caseResults: LabEvaluationCaseResult[];
  submittedAt: string;
  finishedAt: string;
}

export interface LabSubmissionListFilters {
  studentId?: number;
  submitStatus?: LabSubmissionSummary['submitStatus'];
  evaluationStatus?: LabSubmissionSummary['evaluationStatus'];
  overdue?: boolean;
}

export interface LabResult {
  labId: number;
  studentId: number;
  status: LabExperimentStatus;
  submission: LabSubmissionDetail;
  evaluationResult: LabSubmissionResult;
  latestReport: LabReportSummary | null;
  latestScore?: LabScoreSummary | null;
  publishedAt?: string | null;
}

export interface LabStatistics {
  labId: number;
  courseId: number;
  totalStudentCount: number;
  submittedCount: number;
  unsubmittedCount: number;
  evaluatedCount: number;
  submissionRate: number;
  evaluationCompletionRate: number;
  averageScore: number | null;
  lateSubmissionCount: number;
  unsubmittedStudentIds: number[];
  scoreDistribution: Record<string, number>;
  generatedAt: string;
}
