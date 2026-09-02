import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getLabDetail } from '../../../src/api/lab/labs';
import { request, requestBlob } from '../../../src/api/http';
import type { LabExperimentDetail } from '../../../src/types/lab';

vi.mock('../../../src/api/http', () => ({
  configureAuthContext: vi.fn(),
  request: vi.fn(),
  requestBlob: vi.fn()
}));

describe('LAB API adapters', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('normalizes an Assessment service course ID before exposing experiment detail to views', async () => {
    vi.mocked(request).mockResolvedValue({
      id: 7,
      courseId: '101',
      title: '容器评测实验'
    } as unknown as LabExperimentDetail);

    await expect(getLabDetail(7)).resolves.toEqual(expect.objectContaining({
      id: 7,
      courseId: 101
    }));
  });

  it('downloads lab reports through the authenticated blob request helper', async () => {
    const response = {
      blob: new Blob(['report'], { type: 'application/pdf' }),
      filename: 'report-v1.pdf'
    };
    vi.mocked(requestBlob).mockResolvedValueOnce(response);

    const labApiModule = await import('../../../src/api/lab/labs');
    const result = await (labApiModule as {
      downloadLabReport: (labId: number, reportId: number) => Promise<typeof response>;
    }).downloadLabReport(13, 801);

    expect(requestBlob).toHaveBeenCalledWith('/api/v1/labs/13/reports/801/download');
    expect(result).toBe(response);
  });

  it('preserves the Assessment UUID submission identifier for controlled source downloads', async () => {
    const response = {
      blob: new Blob(['print("source")'], { type: 'text/x-python' }),
      filename: 'source-v3.py'
    };
    vi.mocked(requestBlob).mockResolvedValueOnce(response);

    const labApiModule = await import('../../../src/api/lab/labs');
    const result = await (labApiModule as {
      downloadLabSubmissionSource: (
        labId: number,
        submissionId: string
      ) => Promise<typeof response>;
    }).downloadLabSubmissionSource(13, '0d25ce84-3a65-4dc8-8a82-7333f55c9143');

    expect(requestBlob).toHaveBeenCalledWith(
      '/api/v1/labs/13/submissions/0d25ce84-3a65-4dc8-8a82-7333f55c9143/source/download'
    );
    expect(result).toBe(response);
  });

  it('scores lab reports through the teacher report scoring endpoint', async () => {
    const response = {
      reportId: 901,
      submissionId: 301,
      fileName: 'report-v2.pdf',
      fileType: 'PDF',
      fileSize: 4096,
      version: 2,
      score: 95,
      comment: '报告完整',
      submittedAt: '2026-06-26T00:20:00',
      downloadUrl: '/api/v1/labs/12/reports/901/download'
    };
    vi.mocked(request).mockResolvedValueOnce(response);

    const labApiModule = await import('../../../src/api/lab/labs');
    const result = await (labApiModule as {
      scoreLabReport: (
        labId: number,
        reportId: number,
        payload: { score: number; comment: string }
      ) => Promise<typeof response>;
    }).scoreLabReport(12, 901, { score: 95, comment: '报告完整' });

    expect(request).toHaveBeenCalledWith('/api/v1/labs/12/reports/901/score', {
      method: 'PUT',
      body: JSON.stringify({ score: 95, comment: '报告完整' })
    });
    expect(result).toBe(response);
  });

  it('loads lab statistics through the documented teacher statistics endpoint', async () => {
    const response = {
      labId: 12,
      courseId: 101,
      totalStudentCount: 3,
      submittedCount: 2,
      unsubmittedCount: 1,
      evaluatedCount: 1,
      submissionRate: 66.67,
      evaluationCompletionRate: 33.33,
      averageScore: 81.5,
      lateSubmissionCount: 1,
      unsubmittedStudentIds: [703],
      scoreDistribution: {
        '0-59': 0,
        '60-69': 1,
        '70-79': 0,
        '80-89': 0,
        '90-100': 1
      },
      generatedAt: '2026-06-06T23:00:00'
    };
    vi.mocked(request).mockResolvedValueOnce(response);

    const { getLabStatistics } = await import('../../../src/api/lab/labs');
    const result = await getLabStatistics(12);

    expect(request).toHaveBeenCalledWith('/api/v1/labs/12/statistics');
    expect(result).toBe(response);
  });
});
