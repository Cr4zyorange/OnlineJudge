import { beforeEach, describe, expect, it, vi } from 'vitest';
import { request, requestBlob } from '../../../src/api/http';

vi.mock('../../../src/api/http', () => ({
  configureAuthContext: vi.fn(),
  request: vi.fn(),
  requestBlob: vi.fn()
}));

describe('lab api report download', () => {
  beforeEach(() => {
    vi.resetAllMocks();
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
});
