export type HomeworkType = 'OBJECTIVE' | 'FILE' | 'CODE';

export type HomeworkStatus =
  | 'DRAFT'
  | 'NOT_OPEN'
  | 'PUBLISHED'
  | 'CLOSED'
  | 'SCORE_PUBLISHED'
  | 'ARCHIVED';

export type HomeworkQuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';

export type HomeworkSubmitType = 'TEXT' | 'FILE' | 'CODE' | 'OBJECTIVE';

export type HomeworkSubmitStatus = 'SUBMITTED' | 'LATE' | 'REJECTED';

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

export type HomeworkReviewStatus = 'UNREVIEWED' | 'REVIEWED' | 'NEED_REVIEW';

export interface HomeworkQuestionPayload {
  questionType: HomeworkQuestionType;
  stem: string;
  optionsJson: string;
  answerJson: string;
  score: string;
  sortOrder: number;
}

export interface HomeworkTestCasePayload {
  inputData: string;
  expectedOutput: string;
  scoreWeight: string;
  hidden: boolean;
  timeLimitMs: number;
  memoryLimitKb: number;
  sortOrder: number;
}

export interface HomeworkPayload {
  courseId: number;
  chapterId: number | null;
  title: string;
  description: string;
  type: HomeworkType;
  totalScore: string;
  deadline: string;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  questions: HomeworkQuestionPayload[];
  testCases: HomeworkTestCasePayload[];
}

export interface HomeworkQuestion extends Omit<HomeworkQuestionPayload, 'answerJson'> {
  id: number;
  homeworkId: number;
  answerJson?: string;
}

export interface HomeworkTestCase extends HomeworkTestCasePayload {
  id: number;
  homeworkId: number;
}

export interface HomeworkSummary {
  id: number;
  courseId: number;
  chapterId: number | null;
  title: string;
  description: string;
  type: HomeworkType;
  status: HomeworkStatus;
  totalScore: string;
  deadline: string;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  createdBy: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface HomeworkDetail extends HomeworkSummary {
  questions: HomeworkQuestion[];
  testCases: HomeworkTestCase[];
}

export interface HomeworkSubmissionPayload {
  answerText?: string;
  answerJson?: string;
  fileUrl?: string;
  codeText?: string;
  language?: string;
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
  autoScore: string | null;
  manualScore: string | null;
  finalScore: string | null;
  comment: string | null;
  isLatest: boolean;
  isFinal: boolean;
  submittedAt: string;
  reviewedBy: number | null;
  reviewedAt: string | null;
}

export interface HomeworkEvaluation {
  id: number;
  homeworkId: number;
  submissionId: number;
  evaluatorType: HomeworkType;
  status: HomeworkEvaluationStatus;
  score: string | null;
  totalScore: string;
  passedCount: number;
  totalCount: number;
  caseResultsJson: string | null;
  message: string | null;
  startedAt: string;
  finishedAt: string | null;
}
