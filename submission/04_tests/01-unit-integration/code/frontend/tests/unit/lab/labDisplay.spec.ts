import { describe, expect, it } from 'vitest';
import type {
  LabEvaluationMode,
  LabExperimentStatus,
  LabSubmissionSummary
} from '../../../src/types/lab';
import {
  formatLabDateTime,
  formatLabEvaluationMode,
  formatLabEvaluationStatus,
  formatLabExperimentStatus,
  formatLabLanguage,
  formatLabScore,
  formatLabSubmitStatus,
  labEvaluationStatusTone,
  labExperimentStatusTone,
  labSubmitStatusTone,
  localizedLabError
} from '../../../src/views/lab/labDisplay';

type LabSubmitStatus = LabSubmissionSummary['submitStatus'];
type LabEvaluationStatus = LabSubmissionSummary['evaluationStatus'];

describe('LAB student-facing display helpers', () => {
  it.each<[LabExperimentStatus, string, ReturnType<typeof labExperimentStatusTone>]>([
    ['DRAFT', '草稿', 'neutral'],
    ['NOT_OPEN', '未开放', 'neutral'],
    ['PUBLISHED', '进行中', 'brand'],
    ['CLOSED', '已截止', 'warning'],
    ['SCORE_PUBLISHED', '成绩已发布', 'success'],
    ['ARCHIVED', '已归档', 'neutral']
  ])('formats experiment status %s as %s with a stable tone', (status, label, tone) => {
    expect(formatLabExperimentStatus(status)).toBe(label);
    expect(labExperimentStatusTone(status)).toBe(tone);
  });

  it.each<[LabEvaluationMode, string]>([
    ['DOCKER_IO', '自动评测'],
    ['MIXED', '自动评测 + 教师评分'],
    ['MANUAL' as unknown as LabEvaluationMode, '教师评分']
  ])('formats evaluation mode %s as %s', (mode, label) => {
    expect(formatLabEvaluationMode(mode)).toBe(label);
  });

  it.each<[LabSubmitStatus, string, ReturnType<typeof labSubmitStatusTone>]>([
    ['SUBMITTED', '已提交', 'success'],
    ['LATE', '逾期提交', 'warning'],
    ['WITHDRAWN', '已撤回', 'neutral']
  ])('formats submit status %s as %s with a stable tone', (status, label, tone) => {
    expect(formatLabSubmitStatus(status)).toBe(label);
    expect(labSubmitStatusTone(status)).toBe(tone);
  });

  it.each<[LabEvaluationStatus, string, ReturnType<typeof labEvaluationStatusTone>]>([
    ['NONE', '未评测', 'neutral'],
    ['PENDING', '等待评测', 'info'],
    ['RUNNING', '评测中', 'info'],
    ['ACCEPTED', '通过', 'success'],
    ['WRONG_ANSWER', '答案错误', 'danger'],
    ['COMPILE_ERROR', '编译失败', 'danger'],
    ['RUNTIME_ERROR', '运行失败', 'danger'],
    ['TIME_LIMIT_EXCEEDED', '运行超时', 'danger'],
    ['SYSTEM_ERROR', '评测异常', 'danger']
  ])('formats evaluation status %s as %s with a stable tone', (status, label, tone) => {
    expect(formatLabEvaluationStatus(status)).toBe(label);
    expect(labEvaluationStatusTone(status)).toBe(tone);
  });

  it('formats supported language codes without leaking implementation casing', () => {
    expect(formatLabLanguage('java')).toBe('Java');
    expect(formatLabLanguage('PYTHON')).toBe('Python');
    expect(formatLabLanguage('cpp')).toBe('C++');
    expect(formatLabLanguage('c')).toBe('C');
    expect(formatLabLanguage('javascript')).toBe('JavaScript');
    expect(formatLabLanguage('typescript')).toBe('TypeScript');
    expect(formatLabLanguage('')).toBe('未指定');
    expect(formatLabLanguage(null)).toBe('未指定');
  });

  it('formats shared dates and scores as student-facing text', () => {
    expect(formatLabDateTime('2026-08-18T09:30:45')).toBe('2026-08-18 09:30');
    expect(formatLabDateTime('')).toBe('时间待确认');
    expect(formatLabScore(95)).toBe('95 分');
    expect(formatLabScore(0)).toBe('0 分');
    expect(formatLabScore(null)).toBe('待发布');
    expect(formatLabScore(undefined)).toBe('待发布');
  });

  it('keeps actionable Chinese errors and replaces raw technical errors with the supplied fallback', () => {
    expect(localizedLabError(new Error('实验已截止'), '请稍后重试')).toBe('实验已截止');
    expect(localizedLabError(new Error('Failed to fetch'), '请稍后重试')).toBe('请稍后重试');
    expect(localizedLabError('ECONNRESET', '请稍后重试')).toBe('请稍后重试');
  });
});
