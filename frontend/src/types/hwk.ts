export type HomeworkStatus = 'DRAFT' | 'NOT_OPEN' | 'PUBLISHED' | 'CLOSED' | 'SCORE_PUBLISHED' | 'ARCHIVED';

export type HomeworkType = 'OBJECTIVE' | 'FILE' | 'CODE' | 'TEXT';

export interface HomeworkQuestion {
  id: number;
  homeworkId: number;
  questionType: string;
  stem: string;
  optionsJson: string | null;
  answerJson?: string;
  score: number;
  sortOrder: number;
}

export interface HomeworkQuestionPayload {
  questionType: string;
  stem: string;
  optionsJson: string | null;
  answerJson: string;
  score: number;
  sortOrder: number;
}

export interface HomeworkTestCase {
  id: number;
  homeworkId: number;
  inputData: string;
  expectedOutput?: string;
  scoreWeight: number;
  hidden: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  sortOrder: number;
}

export interface HomeworkTestCasePayload {
  inputData: string;
  expectedOutput: string;
  scoreWeight: number;
  hidden: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  sortOrder: number;
}

export interface HomeworkSummary {
  id: number;
  courseId: number;
  title: string;
  description: string;
  type: HomeworkType;
  status: HomeworkStatus;
  totalScore: number;
  deadline: string;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  deleted: boolean;
}

export interface HomeworkDetail extends HomeworkSummary {
  chapterId: number | null;
  judgeConfigId: number | null;
  createdBy: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  languageLimitJson?: string | null;
  timeLimitMs?: number | null;
  memoryLimitKb?: number | null;
  outputCompareMode?: string | null;
  questions: HomeworkQuestion[];
  testCases: HomeworkTestCase[];
}

export interface HomeworkPayload {
  courseId: number;
  chapterId?: number | null;
  title: string;
  description: string;
  type: HomeworkType;
  deadline: string;
  totalScore: number;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  questions: HomeworkQuestionPayload[];
  testCases: HomeworkTestCasePayload[];
  languageLimitJson: string | null;
  timeLimitMs: number;
  memoryLimitKb: number;
  outputCompareMode: string;
}

export type HomeworkSubmitStatus = 'SUBMITTED' | 'LATE' | 'REJECTED';

export type HomeworkReviewStatus = 'UNREVIEWED' | 'REVIEWED' | 'NEED_REVIEW';

export type HomeworkEvaluationStatus =
  | 'NONE'
  | 'PENDING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'SYSTEM_ERROR';

export interface HomeworkSubmissionPayload {
  answerText?: string;
  answerJson?: string;
  fileIds?: string[];
  codeText?: string;
  language?: string;
}

export interface HomeworkReviewPayload {
  manualScore: number;
  finalScore: number;
  comment?: string | null;
}

export interface HomeworkSubmissionSummary {
  submissionId: number;
  homeworkId: number;
  studentId: number;
  submitType?: HomeworkType;
  answerText?: string | null;
  answerJson?: string | null;
  fileUrl?: string | null;
  language?: string | null;
  submitStatus: HomeworkSubmitStatus;
  evaluationStatus: HomeworkEvaluationStatus;
  reviewStatus: HomeworkReviewStatus;
  autoScore?: number | null;
  manualScore?: number | null;
  finalScore?: number | null;
  comment?: string | null;
  version: number;
  final: boolean;
  submittedAt: string;
}

export type HomeworkSubmissionDetail = HomeworkSubmissionSummary;

export interface HomeworkEvaluationResult {
  evaluationId: number;
  submissionId: number;
  evaluationStatus: HomeworkEvaluationStatus;
  score: number;
  passedCases: number;
  totalCases: number;
  durationMs?: number | null;
  errorMessage?: string | null;
  feedback?: string | null;
  compileLog?: string | null;
  runLog?: string | null;
  reevaluation: boolean;
  triggeredBy?: number | null;
  startedAt: string;
  finishedAt?: string | null;
}

export interface HomeworkStatistics {
  homeworkId: number;
  courseId: number;
  totalStudentCount: number;
  submittedCount: number;
  unsubmittedCount: number;
  evaluatedCount: number;
  reviewedCount: number;
  averageScore: number | null;
  maxScore: number | null;
  minScore: number | null;
  unsubmittedStudentIds: number[];
}

export type HomeworkReviewOperationType = 'REVIEW' | 'REJUDGE' | 'PUBLISH';

export interface HomeworkReviewLog {
  id: number;
  submissionId: number;
  homeworkId: number;
  studentId: number;
  operationType: HomeworkReviewOperationType;
  oldScore?: number | null;
  newScore?: number | null;
  comment?: string | null;
  operatorId: number;
  reason?: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}
