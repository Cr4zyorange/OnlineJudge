import type {
  HomeworkEvaluationStatus,
  HomeworkReviewOperationType,
  HomeworkReviewStatus,
  HomeworkStatus,
  HomeworkSubmitStatus,
  HomeworkType
} from '../../types/hwk';

const homeworkTypeLabels: Record<HomeworkType, string> = {
  OBJECTIVE: '客观题作业',
  FILE: '附件作业',
  CODE: '代码作业',
  TEXT: '文本作业'
};

const homeworkStatusLabels: Record<HomeworkStatus, string> = {
  DRAFT: '草稿',
  NOT_OPEN: '未开放',
  PUBLISHED: '已发布',
  CLOSED: '已关闭',
  SCORE_PUBLISHED: '成绩已发布',
  ARCHIVED: '已归档'
};

const submitStatusLabels: Record<HomeworkSubmitStatus, string> = {
  SUBMITTED: '已提交',
  LATE: '逾期提交',
  REJECTED: '已拒绝'
};

const evaluationStatusLabels: Record<HomeworkEvaluationStatus, string> = {
  NONE: '未评测',
  PENDING: '等待评测',
  RUNNING: '评测中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  COMPILE_ERROR: '编译错误',
  RUNTIME_ERROR: '运行错误',
  TIME_LIMIT_EXCEEDED: '运行超时',
  SYSTEM_ERROR: '系统错误'
};

const reviewStatusLabels: Record<HomeworkReviewStatus, string> = {
  UNREVIEWED: '待批阅',
  REVIEWED: '已批阅',
  NEED_REVIEW: '需批阅'
};

const reviewOperationLabels: Record<HomeworkReviewOperationType, string> = {
  REVIEW: '批阅',
  REJUDGE: '重评',
  PUBLISH: '发布成绩'
};

const questionTypeLabels: Record<string, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  JUDGE: '判断题'
};

const outputCompareModeLabels: Record<string, string> = {
  EXACT: '严格匹配',
  TRIM: '忽略首尾空白'
};

function labelOf<T extends string>(labels: Record<T, string>, value: T | null | undefined) {
  return value ? labels[value] ?? value : '未知';
}

export function formatHomeworkType(value: HomeworkType | null | undefined) {
  return labelOf(homeworkTypeLabels, value);
}

export function formatHomeworkStatus(value: HomeworkStatus | null | undefined) {
  return labelOf(homeworkStatusLabels, value);
}

export function formatSubmitStatus(value: HomeworkSubmitStatus | null | undefined) {
  return labelOf(submitStatusLabels, value);
}

export function formatEvaluationStatus(value: HomeworkEvaluationStatus | null | undefined) {
  return labelOf(evaluationStatusLabels, value);
}

export function formatReviewStatus(value: HomeworkReviewStatus | null | undefined) {
  return labelOf(reviewStatusLabels, value);
}

export function formatReviewOperation(value: HomeworkReviewOperationType | null | undefined) {
  return labelOf(reviewOperationLabels, value);
}

export function formatQuestionType(value: string | null | undefined) {
  return value ? questionTypeLabels[value] ?? value : '未知';
}

export function formatOutputCompareMode(value: string | null | undefined) {
  return value ? outputCompareModeLabels[value] ?? value : '未知';
}
