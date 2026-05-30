export type HomeworkStatus = 'DRAFT' | 'NOT_OPEN' | 'PUBLISHED' | 'CLOSED' | 'SCORE_PUBLISHED' | 'ARCHIVED';

export type HomeworkType = 'OBJECTIVE' | 'FILE' | 'CODE' | 'TEXT';

export interface HomeworkQuestion {
  id: number;
  homeworkId: number;
  questionType: string;
  stem: string;
  optionsJson: string | null;
  answerJson: string;
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
  expectedOutput: string;
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

export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}
