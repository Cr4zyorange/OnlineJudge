import { appendFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Page } from '@playwright/test';

type RepresentativeGroup = 'AUTH-CRS' | 'ASSESSMENT-WORKER' | 'GRD-LRN';

type ApiOperationEvidence = {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  status: number;
  response: Record<string, unknown>;
};

export type RepresentativeEvidenceRecord = {
  group: RepresentativeGroup;
  proofId: string;
  chain: Record<string, ApiOperationEvidence>;
  uiAssertion: {
    route: string;
    selector: string;
    expected: string;
    screenshot: string;
    junitCase: string;
  };
};

export async function captureIssue320RepresentativeScreenshot(page: Page, name: string): Promise<string> {
  const artifactDir = process.env.E2E_THREE_SERVICE_ARTIFACT_DIR?.trim();
  if (!artifactDir) return '';
  const screenshot = join(artifactDir, `representative-${name}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  return screenshot;
}

/**
 * The disposable runner opts into this append-only, non-secret record. It
 * links a passing browser assertion to the exact API entity that the scenario
 * created or observed; the runner later joins it to the raw gateway log.
 */
export function recordIssue320Representative(record: RepresentativeEvidenceRecord): void {
  const output = process.env.E2E_THREE_SERVICE_REPRESENTATIVE_EVIDENCE_FILE?.trim();
  if (!output) return;
  const operations = Object.values(record.chain ?? {});
  if (!record.proofId || !record.uiAssertion?.route.startsWith('/') || !record.uiAssertion.selector
    || !record.uiAssertion.screenshot || !record.uiAssertion.junitCase
    || operations.length === 0 || operations.some((operation) => (
      !operation.path.startsWith('/api/') || !Number.isInteger(operation.status)
    ))) {
    throw new Error('Issue #320 representative evidence must contain safe API chain records and a browser assertion');
  }
  appendFileSync(output, `${JSON.stringify(record)}\n`, { encoding: 'utf8', mode: 0o600 });
}
