import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';

import {
  buildRepresentativeEvidence,
  createGrdSummaryFixtureLabPayload,
  createBootstrapRequestId,
  isSuccessfulSummary,
  normalizeBareLabCreateResponse,
  normalizeBareLabScorePublicationResponse,
  normalizeBareLabScoreResponse,
  normalizeBareLabSubmissionResponse,
  parseStudentGradeSummaryIdentifier,
  parsePositiveIdentifier,
  redact,
  waitForAssessmentLabMembership,
  validateCleanup,
  validateContext,
  validateEvidenceManifest
} from './run-business-e2e-three-service.mjs';

test('accepts only a positive numeric identifier from a bootstrap API envelope', () => {
  assert.equal(parsePositiveIdentifier({ data: { id: '42' } }, 'course'), 42);
  assert.throws(() => parsePositiveIdentifier({ data: { id: 0 } }, 'course'), /course.*positive/i);
});

test('generates UUID correlation ids accepted by the Grade source-event consumer', () => {
  assert.match(
    createBootstrapRequestId(),
    /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i
  );
});

test('waits read-only for the Assessment LAB membership projection before one submission', async () => {
  const requests = [];
  const delays = [];
  const statuses = [403, 200];

  await waitForAssessmentLabMembership({
    baseUrl: 'http://127.0.0.1:18080',
    labId: 418,
    token: 'student-token',
    fetchImpl: async (url, options) => {
      requests.push({ url: String(url), method: options.method, headers: options.headers });
      return { status: statuses.shift() };
    },
    sleep: async (delay) => { delays.push(delay); }
  });

  assert.deepEqual(delays, [100]);
  assert.equal(requests.length, 2);
  assert.ok(requests.every((request) => request.method === 'GET'));
  assert.ok(requests.every((request) => request.url.endsWith('/api/v1/labs/418')));
  assert.ok(requests.every((request) => !request.url.includes('/submissions')));
  assert.ok(requests.every((request) => /^Bearer student-token$/.test(request.headers.authorization)));
  assert.ok(requests.every((request) => /^[0-9a-f-]{36}$/i.test(request.headers['x-request-id'])));
});

test('selects the bootstrapped student course-grade summary rather than a legacy fixture id', () => {
  assert.equal(parseStudentGradeSummaryIdentifier({
    data: {
      records: [
        { studentId: 7, summary: { id: 101 } },
        { studentId: 8, summary: { id: '102' } }
      ]
    }
  }, 8), 102);
  assert.throws(
    () => parseStudentGradeSummaryIdentifier({ data: { records: [] } }, 8),
    /student.*summary.*positive/i
  );
});

test('builds the GRD source LAB fixture with an RFC 3339 deadline required by Assessment', () => {
  const payload = createGrdSummaryFixtureLabPayload();
  assert.match(payload.deadline, /Z$/);
  assert.ok(Number.isFinite(Date.parse(payload.deadline)));
  assert.deepEqual(payload.allowedLanguages, ['python']);
  assert.equal(payload.evaluationMode, 'MANUAL');
  assert.equal(payload.autoEvaluate, false);
  assert.deepEqual(payload.testcases, []);
});

test('normalizes Assessment bare 201 LAB creation responses into the bootstrap envelope', () => {
  const normalized = normalizeBareLabCreateResponse({ labId: 314, id: 314, status: 'DRAFT' });
  assert.deepEqual(normalized, { data: { id: 314 } });
  assert.throws(() => normalizeBareLabCreateResponse({ labId: 0 }), /LAB.*positive/i);
});

test('uses the strict bare LAB response normalizer when publishing the GRD fixture source', () => {
  const runner = readFileSync(
    resolve(import.meta.dirname, 'run-business-e2e-three-service.mjs'),
    'utf8'
  );
  assert.match(
    runner,
    /\/api\/v1\/labs\/\$\{fixtureLabId\}\/publish[\s\S]{0,240}normalizeBareLabCreateResponse/
  );
});

test('normalizes only UUID-backed Assessment bare LAB submission responses', () => {
  const submissionId = '571b37b3-1b3c-47d5-a692-04a21ef2db23';
  assert.deepEqual(
    normalizeBareLabSubmissionResponse({ submissionId, status: 'SUBMITTED' }),
    { data: { submissionId } }
  );
  assert.throws(
    () => normalizeBareLabSubmissionResponse({ submissionId: 'not-a-uuid' }),
    /UUID submission id/i
  );
});

test('normalizes a bare LAB score only when it confirms the submitted UUID and numeric final score', () => {
  const submissionId = '571b37b3-1b3c-47d5-a692-04a21ef2db23';
  assert.deepEqual(
    normalizeBareLabScoreResponse({ submissionId, finalScore: 90 }, submissionId),
    { data: { submissionId, finalScore: 90 } }
  );
  assert.throws(
    () => normalizeBareLabScoreResponse({ submissionId, finalScore: '90' }, submissionId),
    /numeric finalScore/i
  );
  assert.throws(
    () => normalizeBareLabScoreResponse({ submissionId, finalScore: 90 }, 'd9e4c95b-95cf-4fc3-a62f-12e96fd4c7b1'),
    /does not match/i
  );
});

test('normalizes a bare LAB score publication only for the requested LAB and terminal status', () => {
  assert.deepEqual(
    normalizeBareLabScorePublicationResponse({ labId: 314, status: 'SCORE_PUBLISHED' }, 314),
    { data: { id: 314, status: 'SCORE_PUBLISHED' } }
  );
  assert.throws(
    () => normalizeBareLabScorePublicationResponse({ labId: 314, status: 'PUBLISHED' }, 314),
    /SCORE_PUBLISHED/i
  );
  assert.throws(
    () => normalizeBareLabScorePublicationResponse({ labId: 315, status: 'SCORE_PUBLISHED' }, 314),
    /does not match/i
  );
});

test('rejects a context that is not a nine-workload loopback platform', () => {
  assert.throws(
    () => validateContext({ workloads: 8, baseUrl: 'http://example.test' }),
    /nine workloads.*loopback/i
  );
});

test('requires the disposable Compose environment file for post-E2E runtime evidence', () => {
  assert.throws(
    () => validateContext({
      workloads: 9,
      baseUrl: 'http://127.0.0.1:18080',
      projectName: 'oj318-deadbeef-1',
      evidenceDir: '/private/tmp/evidence',
      composeFile: '/private/tmp/compose.yml'
    }),
    /Compose environment file/i
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

test('captures post-E2E Compose logs with RFC3339 timestamps for cross-service evidence ordering', () => {
  const runner = readFileSync(resolve('scripts/test/run-business-e2e-three-service.mjs'), 'utf8');
  assert.match(runner, /'logs', '--no-color', '--timestamps'/);
});

test('requires three independent browser-to-runtime representative evidence chains', () => {
  const evidenceDir = mkdtempSync(join(tmpdir(), 'issue320-representative-'));
  const screenshot = (name) => {
    const path = join(evidenceDir, `${name}.png`);
    writeFileSync(path, 'screenshot');
    return path;
  };
  const authCase = '教师建课并展示章节/资源/公告管理入口，学生公开加入';
  const workerCase = '多类型提交验证代码后台评测并覆盖附件异常、过期、越权与重评';
  const gradeCase = '@grd-main @grd-alternative @grd-exception runs LAB/HWK -> GRD -> LRN with real APIs';
  const records = [
    {
      group: 'AUTH-CRS', proofId: 'course-418',
      chain: {
        login: { method: 'POST', path: '/api/v1/auth/login', status: 200, response: { userId: 901 } },
        courseDetail: { method: 'GET', path: '/api/v1/courses/418', status: 200, response: { courseId: 418, member: true } }
      },
      uiAssertion: { route: '/courses/418', selector: 'button[name="管理章节"]', expected: 'visible', screenshot: screenshot('auth'), junitCase: authCase }
    },
    {
      group: 'ASSESSMENT-WORKER', proofId: 'task-e640c4ad-6bd5-4ad4-b5f4-48c740f7df55',
      chain: {
        submit: { method: 'POST', path: '/api/v1/homeworks/418/submissions', status: 201, response: { submissionId: 419, evaluationStatus: 'PENDING' } },
        finalRead: { method: 'GET', path: '/api/v1/submissions/419/evaluation', status: 200, response: { submissionId: 419, taskId: 'e640c4ad-6bd5-4ad4-b5f4-48c740f7df55', taskState: 'SUCCEEDED', evaluationStatus: 'ACCEPTED', score: 100 } }
      },
      uiAssertion: { route: '/courses/418/homeworks/1/submissions/419/result', selector: '[data-testid="evaluation-score"]', expected: '100', screenshot: screenshot('worker'), junitCase: workerCase }
    },
    {
      group: 'GRD-LRN', proofId: 'notification-620',
      chain: {
        publish: { method: 'POST', path: '/api/v1/courses/418/grades/publish', status: 200, response: { publishId: 617, publishedCount: 1 } },
        notification: { method: 'GET', path: '/api/v1/notifications?type=GRADE&size=100', status: 200, response: { notificationId: 620, sourceId: 617, sourceModule: 'GRD', title: '成绩已发布' } }
      },
      uiAssertion: { route: '/notifications', selector: '[data-testid="notification-card-620"]', expected: '成绩已发布', screenshot: screenshot('grade'), junitCase: gradeCase }
    }
  ];
  // Compose groups lines by service, not timestamp.  These three CI-shaped
  // assessment lines are intentionally physically ordered queued, final GET,
  // worker completion while their RFC3339 timestamps prove the actual chain.
  const logs = [
    'gateway-1 | 2026-09-03T02:29:40.000Z "GET /api/v1/courses/418 HTTP/1.1" 200',
    'gateway-1 | 2026-09-03T02:29:43.000Z "GET /api/v1/submissions/419/evaluation HTTP/1.1" 200 early-poll',
    'assessment-api-1 | 2026-09-03T02:29:44.870Z homework_submission_queued publicSubmissionId=419 submissionId=internal-419 taskId=e640c4ad-6bd5-4ad4-b5f4-48c740f7df55',
    'gateway-1 | 2026-09-03T02:29:48.000Z "GET /api/v1/submissions/419/evaluation HTTP/1.1" 200',
    'gateway-1 | 2026-09-03T02:29:49.000Z "GET /api/v1/notifications?type=GRADE&size=100 HTTP/1.1" 200 early-poll',
    'course-service-1 | 2026-09-03T02:29:50.000Z lrn_notification_projected eventId=event-617 correlationId=correlation-617 sourceModule=GRD sourceId=617 notificationIds=[620]',
    'gateway-1 | 2026-09-03T02:29:51.000Z "GET /api/v1/notifications?type=GRADE&size=100 HTTP/1.1" 200',
    'assessment-worker-1 | 2026-09-03T02:29:47.320Z assessment_worker_terminal taskId=e640c4ad-6bd5-4ad4-b5f4-48c740f7df55 submissionId=internal-419 sourceType=HWK taskState=SUCCEEDED evaluationStatus=ACCEPTED score=100'
  ].join('\n');
  const junit = [authCase, workerCase, gradeCase].map((name) => (
    `<testcase name="${name.replaceAll('>', '&gt;')}"/>`
  )).join('\n');

  const manifest = buildRepresentativeEvidence({ baseUrl: 'http://127.0.0.1:18080', records, logs, junit });

  assert.deepEqual(manifest.representative.map((entry) => entry.group), [
    'AUTH-CRS', 'ASSESSMENT-WORKER', 'GRD-LRN'
  ]);
  assert.equal(new Set(manifest.representative.map((entry) => entry.proofId)).size, 3);
  assert.match(manifest.representative[1].requestResponse, /internal-419/);
  assert.match(manifest.representative[2].logExcerpt, /notifications\?type=GRADE/);
  assert.throws(() => buildRepresentativeEvidence({
    baseUrl: 'http://127.0.0.1:18080', records, junit,
    logs: logs.replace('2026-09-03T02:29:47.320Z assessment_worker_terminal', '2026-09-03T02:29:49.000Z assessment_worker_terminal')
  }), /submit then worker completion then passive GET/i);
  assert.throws(() => buildRepresentativeEvidence({
    baseUrl: 'http://127.0.0.1:18080', records, junit,
    logs: logs.replace('gateway-1 | 2026-09-03T02:29:48.000Z "GET /api/v1/submissions/419/evaluation HTTP/1.1" 200', 'gateway-1 | "GET /api/v1/submissions/419/evaluation HTTP/1.1" 200')
  }), /timestamp/i);
  assert.throws(() => buildRepresentativeEvidence({
    baseUrl: 'http://127.0.0.1:18080', logs, junit,
    records: [...records.slice(0, 2), { ...records[2], proofId: 'task-e640c4ad-6bd5-4ad4-b5f4-48c740f7df55' }]
  }), /distinct/i);
  assert.throws(() => buildRepresentativeEvidence({
    baseUrl: 'http://127.0.0.1:18080', logs, junit,
    records: records.map((record) => record.group === 'ASSESSMENT-WORKER'
      ? { ...record, chain: { submit: record.chain.submit } }
      : record)
  }), /finalRead/i);
  assert.throws(() => buildRepresentativeEvidence({
    baseUrl: 'http://127.0.0.1:18080', logs, junit,
    records: records.map((record) => record.group === 'GRD-LRN'
      ? { ...record, chain: { publish: record.chain.publish } }
      : record)
  }), /notification/i);
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

test('mints disposable service JWTs after the optional image build completes', () => {
  const runner = readFileSync(
    resolve(import.meta.dirname, '../platform/run_disposable_environment.sh'),
    'utf8'
  );

  const build = runner.indexOf('if (( ! skip_build ));');
  const identities = runner.indexOf('assessment_course_identity="$(mint_service_token');
  assert.ok(build >= 0, 'the disposable runner must retain its optional image build');
  assert.ok(identities > build,
    'short-lived Assessment-to-Course authorization credentials must be minted after image builds');
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
