import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestBlob } from '../../../src/api/http';

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
});
