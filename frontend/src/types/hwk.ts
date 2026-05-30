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

export type HomeworkSubmitType = 'OBJECTIVE' | 'FILE' | 'CODE' | 'TEXT';

export type HomeworkSubmitStatus = 'SUBMITTED' | 'LATE';

export type HomeworkEvaluationStatus = 'PENDING' | 'NOT_EVALUATED' | 'NOT_REQUIRED';

export type HomeworkReviewStatus = 'UNREVIEWED' | 'REVIEWED';

export interface HomeworkSubmissionPayload {
  answerText: string | null;
  answerJson: string | null;
  fileUrl: string | null;
  codeText: string | null;
  language: string | null;
}

export interface HomeworkSubmission {
  id: number;
  homeworkId: number;
  studentId: number;
  submitType: HomeworkSubmitType;
  answerText: string | null;
  answerJson: string | null;
  fileUrl: string | null;
  language: string | null;
  submitStatus: HomeworkSubmitStatus;
  evaluationStatus: HomeworkEvaluationStatus;
  reviewStatus: HomeworkReviewStatus;
  autoScore: number | null;
  manualScore: number | null;
  finalScore: number | null;
  comment: string | null;
  final: boolean;
  submittedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}
