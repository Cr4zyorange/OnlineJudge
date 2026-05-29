export type LabExperimentStatus =
  | 'DRAFT'
  | 'NOT_OPEN'
  | 'PUBLISHED'
  | 'CLOSED'
  | 'SCORE_PUBLISHED'
  | 'ARCHIVED';

export type LabEvaluationMode = 'DOCKER_IO' | 'MIXED';

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

export interface LabSubmissionSummary {
  submissionId: number;
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
  version: number;
  submittedAt: string;
}
