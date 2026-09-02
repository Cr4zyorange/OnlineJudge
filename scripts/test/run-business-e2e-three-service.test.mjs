import assert from 'node:assert/strict';
import test from 'node:test';

import {
  isSuccessfulSummary,
  parsePositiveIdentifier,
  redact,
  validateCleanup,
  validateContext,
  validateEvidenceManifest
} from './run-business-e2e-three-service.mjs';

test('accepts only a positive numeric identifier from a bootstrap API envelope', () => {
  assert.equal(parsePositiveIdentifier({ data: { id: '42' } }, 'course'), 42);
  assert.throws(() => parsePositiveIdentifier({ data: { id: 0 } }, 'course'), /course.*positive/i);
});

test('rejects a context that is not a nine-workload loopback platform', () => {
  assert.throws(
    () => validateContext({ workloads: 8, baseUrl: 'http://example.test' }),
    /nine workloads.*loopback/i
  );
});

test('requires exactly 24 passed with no failed or skipped tests', () => {
  assert.equal(isSuccessfulSummary({ total: 24, passed: 24, failed: 0, skipped: 0 }), true);
  assert.equal(isSuccessfulSummary({ total: 24, passed: 23, failed: 0, skipped: 1 }), false);
});

test('redacts runtime secrets from evidence', () => {
  assert.equal(
    redact('MYSQL_ROOT_PASSWORD=abc Bearer xyz', ['abc', 'xyz']),
    'MYSQL_ROOT_PASSWORD=[REDACTED] Bearer [REDACTED]'
  );
});

test('requires the three representative evidence groups', () => {
  assert.throws(
    () => validateEvidenceManifest({ representative: [] }),
    /AUTH.*Worker.*GRD/i
  );
});

test('rejects cleanup records that leave project resources behind', () => {
  assert.throws(() => validateCleanup({ containers: ['oj318-x'] }), /resources remain/i);
});
