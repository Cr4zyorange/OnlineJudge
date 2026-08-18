import type { StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import type {
  LabEvaluationMode,
  LabExperimentStatus,
  LabSubmissionSummary
} from '../../types/lab';

type LabSubmitStatus = LabSubmissionSummary['submitStatus'];
type LabEvaluationStatus = LabSubmissionSummary['evaluationStatus'];

const experimentStatusLabels: Record<LabExperimentStatus, string> = {
  DRAFT: '草稿',
  NOT_OPEN: '未开放',
  PUBLISHED: '进行中',
  CLOSED: '已截止',
  SCORE_PUBLISHED: '成绩已发布',
  ARCHIVED: '已归档'
};

const experimentStatusTones: Record<LabExperimentStatus, StatusBadgeTone> = {
  DRAFT: 'neutral',
  NOT_OPEN: 'neutral',
  PUBLISHED: 'brand',
  CLOSED: 'warning',
  SCORE_PUBLISHED: 'success',
  ARCHIVED: 'neutral'
};

const evaluationModeLabels: Record<LabEvaluationMode, string> = {
  DOCKER_IO: '自动评测',
  MIXED: '自动评测 + 教师评分',
  MANUAL: '教师评分'
};

const submitStatusLabels: Record<LabSubmitStatus, string> = {
  SUBMITTED: '已提交',
  LATE: '逾期提交',
  WITHDRAWN: '已撤回'
};

const submitStatusTones: Record<LabSubmitStatus, StatusBadgeTone> = {
  SUBMITTED: 'success',
  LATE: 'warning',
  WITHDRAWN: 'neutral'
};

const evaluationStatusLabels: Record<LabEvaluationStatus, string> = {
  NONE: '未评测',
  PENDING: '等待评测',
  RUNNING: '评测中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  COMPILE_ERROR: '编译失败',
  RUNTIME_ERROR: '运行失败',
  TIME_LIMIT_EXCEEDED: '运行超时',
  SYSTEM_ERROR: '评测异常'
};

const evaluationStatusTones: Record<LabEvaluationStatus, StatusBadgeTone> = {
  NONE: 'neutral',
  PENDING: 'info',
  RUNNING: 'info',
  ACCEPTED: 'success',
  WRONG_ANSWER: 'danger',
  COMPILE_ERROR: 'danger',
  RUNTIME_ERROR: 'danger',
  TIME_LIMIT_EXCEEDED: 'danger',
  SYSTEM_ERROR: 'danger'
};

const languageLabels: Readonly<Record<string, string>> = {
  cpp: 'C++',
  c: 'C',
  java: 'Java',
  python: 'Python',
  javascript: 'JavaScript',
  typescript: 'TypeScript'
};

export function formatLabExperimentStatus(status: LabExperimentStatus): string {
  return experimentStatusLabels[status];
}

export function labExperimentStatusTone(status: LabExperimentStatus): StatusBadgeTone {
  return experimentStatusTones[status];
}

export function formatLabEvaluationMode(mode: LabEvaluationMode): string {
  return evaluationModeLabels[mode];
}

export function formatLabSubmitStatus(status: LabSubmitStatus): string {
  return submitStatusLabels[status];
}

export function labSubmitStatusTone(status: LabSubmitStatus): StatusBadgeTone {
  return submitStatusTones[status];
}

export function formatLabEvaluationStatus(status: LabEvaluationStatus): string {
  return evaluationStatusLabels[status];
}

export function labEvaluationStatusTone(status: LabEvaluationStatus): StatusBadgeTone {
  return evaluationStatusTones[status];
}

export function formatLabLanguage(language: string | null | undefined): string {
  const normalized = language?.trim().toLowerCase();
  if (!normalized) {
    return '未指定';
  }
  return languageLabels[normalized] ?? language!.trim();
}

export function formatLabDateTime(value: string | null | undefined): string {
  if (!value?.trim()) {
    return '时间待确认';
  }
  return value.trim().replace('T', ' ').slice(0, 16);
}

export function formatLabScore(score: number | null | undefined): string {
  return score === null || score === undefined ? '待发布' : `${score} 分`;
}

export function localizedLabError(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message.trim() : '';
  return /[\u3400-\u9fff]/u.test(message) ? message : fallback;
}
