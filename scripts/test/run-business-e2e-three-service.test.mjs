import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

test('renders the Course authorization endpoint used by Assessment', () => {
  const renderer = readFileSync(
    resolve(import.meta.dirname, '../platform/render_disposable_environment.py'),
    'utf8'
  );

  assert.match(
    renderer,
    /ASSESSMENT_COURSE_AUTHORIZATION_URI.*\/internal\/v2\/courses\/\{courseId\}\/authorizations\/\{userId\}/
  );
});

test('mints a scoped Course service JWT for Assessment in the disposable runtime', () => {
  const runner = readFileSync(
    resolve(import.meta.dirname, '../platform/run_disposable_environment.sh'),
    'utf8'
  );

  assert.match(runner, /mint_service_token\(\)/);
  assert.match(
    runner,
    /assessment_course_identity="\$\(mint_service_token assessment-api course course\.authorizations\.read\)"/
  );
  assert.match(runner, /ASSESSMENT_SERVICE_IDENTITY=%s\\n' "\$assessment_course_identity"/);
  assert.match(runner, /tr '\+' '-' \| tr '\/' '_'/);
});

test('uses audience-specific Grade service identities in the disposable environment', () => {
  const renderer = readFileSync(
    resolve(import.meta.dirname, '../platform/render_disposable_environment.py'),
    'utf8'
  );
  const runner = readFileSync(
    resolve(import.meta.dirname, '../platform/run_disposable_environment.sh'),
    'utf8'
  );

  assert.match(renderer, /GRADE_COURSE_SERVICE_AUTHORIZATION.*GRADE_COURSE_SERVICE_IDENTITY/);
  assert.match(renderer, /GRADE_ASSESSMENT_SERVICE_AUTHORIZATION.*GRADE_ASSESSMENT_SERVICE_IDENTITY/);
  assert.match(runner, /mint_service_token grade-service course course\.authorizations\.read course\.members\.read/);
  assert.match(runner, /mint_service_token grade-service assessment grades:read/);
});
