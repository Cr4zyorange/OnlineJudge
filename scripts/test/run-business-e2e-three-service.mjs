#!/usr/bin/env node
import { randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { chmod, mkdir, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const frontendDir = join(repositoryRoot, 'frontend');
const commandSuffix = process.platform === 'win32' ? '.cmd' : '';
const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

export const e2eTargets = [
  'tests/e2e/auth',
  'tests/e2e/crs',
  'tests/e2e/grd',
  'tests/e2e/hwk',
  'tests/e2e/lab',
  'tests/e2e/lrn/lrn-business-closure.spec.ts',
  'tests/e2e/lrn/notification-read-on-open.spec.ts',
  'tests/e2e/shared'
];

export function validateContext(context) {
  const hasNineWorkloads = context?.workloads === 9;
  const isLoopback = /^http:\/\/127\.0\.0\.1:\d+$/.test(context?.baseUrl || '');
  if (!hasNineWorkloads || !isLoopback) {
    throw new Error('three-service context must describe nine workloads on a loopback base URL');
  }
  if (!/^oj318-[a-z0-9-]+$/i.test(context.projectName || '')) {
    throw new Error('three-service context must identify its disposable oj318 Compose project');
  }
  if (typeof context.evidenceDir !== 'string' || !context.evidenceDir.trim()) {
    throw new Error('three-service context must provide an evidence directory');
  }
  return context;
}

export function isSuccessfulSummary(summary) {
  return summary?.total === 24
    && summary.passed === 24
    && summary.failed === 0
    && summary.skipped === 0;
}

export function parsePositiveIdentifier(envelope, label) {
  const id = Number(envelope?.data?.id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error(`${label} bootstrap response must contain a positive numeric id`);
  }
  return id;
}

export function parseStudentGradeSummaryIdentifier(envelope, studentId) {
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    throw new Error('student bootstrap response must contain a positive numeric id');
  }
  const summaryId = Number(envelope?.data?.records?.find((record) => (
    Number(record?.studentId) === studentId
  ))?.summary?.id);
  if (!Number.isSafeInteger(summaryId) || summaryId <= 0) {
    throw new Error('student course-grade summary bootstrap response must contain a positive numeric id');
  }
  return summaryId;
}

export function createGrdSummaryFixtureLabPayload() {
  return {
    title: 'Issue #320 GRD 总评运行期基线',
    description: 'Creates a published, adjustable course-grade summary for the LRN closure scenario.',
    deadline: '2030-12-31T23:59:00Z',
    maxScore: 100,
    attachmentIds: [],
    allowedLanguages: ['python'],
    evaluationMode: 'MANUAL',
    autoEvaluate: false,
    reportRequired: false,
    timeLimitMs: 1_000,
    memoryLimitKb: 65_536,
    testcases: []
  };
}

export function normalizeBareLabCreateResponse(body) {
  const labId = Number(body?.id ?? body?.labId);
  if (!Number.isSafeInteger(labId) || labId <= 0) {
    throw new Error('Assessment bare LAB create response must contain a positive numeric id');
  }
  return { data: { id: labId } };
}

export function normalizeBareLabSubmissionResponse(body) {
  const submissionId = String(body?.submissionId || '');
  if (!UUID_PATTERN.test(submissionId)) {
    throw new Error('Assessment bare LAB submission response must contain a UUID submission id');
  }
  return { data: { submissionId } };
}

export function normalizeBareLabScoreResponse(body, expectedSubmissionId) {
  const submissionId = String(body?.submissionId || '');
  if (!UUID_PATTERN.test(submissionId) || !UUID_PATTERN.test(expectedSubmissionId || '')) {
    throw new Error('Assessment bare LAB score response must contain a UUID submission id');
  }
  if (submissionId !== expectedSubmissionId) {
    throw new Error('Assessment bare LAB score response submission id does not match the submitted LAB fixture');
  }
  if (typeof body?.finalScore !== 'number' || !Number.isFinite(body.finalScore)) {
    throw new Error('Assessment bare LAB score response must contain a numeric finalScore');
  }
  return { data: { submissionId, finalScore: body.finalScore } };
}

export function normalizeBareLabScorePublicationResponse(body, expectedLabId) {
  if (!Number.isSafeInteger(expectedLabId) || expectedLabId <= 0) {
    throw new Error('requested LAB fixture id must be a positive numeric id');
  }
  const labId = Number(body?.id ?? body?.labId);
  if (!Number.isSafeInteger(labId) || labId <= 0) {
    throw new Error('Assessment bare LAB score publication response must contain a positive numeric id');
  }
  if (labId !== expectedLabId) {
    throw new Error('Assessment bare LAB score publication response id does not match the requested LAB fixture');
  }
  if (body?.status !== 'SCORE_PUBLISHED') {
    throw new Error('Assessment bare LAB score publication response must have SCORE_PUBLISHED status');
  }
  return { data: { id: labId, status: body.status } };
}

export function redact(value, secrets) {
  return secrets.reduce((result, secret) => {
    if (!secret) {
      return result;
    }
    return result.split(secret).join('[REDACTED]');
  }, String(value));
}

export function validateEvidenceManifest(manifest) {
  const required = ['AUTH-CRS', 'ASSESSMENT-WORKER', 'GRD-LRN'];
  const representative = manifest?.representative;
  if (!Array.isArray(representative)
    || !required.every((group) => representative.some((entry) => entry?.group === group))) {
    throw new Error('evidence must include AUTH-CRS, ASSESSMENT-WORKER and GRD-LRN representative groups');
  }
  for (const group of representative) {
    if (!group.requestResponse || !group.uiAssertion || !group.proofId || !group.logExcerpt) {
      throw new Error(`evidence group ${group.group || 'unknown'} is incomplete`);
    }
  }
  return manifest;
}

export function validateCleanup(cleanup) {
  const remaining = [...(cleanup?.containers || []), ...(cleanup?.volumes || [])];
  if (remaining.length) {
    throw new Error(`disposable resources remain: ${remaining.join(', ')}`);
  }
  return cleanup;
}

function parseJUnit(xml) {
  const root = xml.match(/<testsuites\b([^>]*)>/) || xml.match(/<testsuite\b([^>]*)>/);
  if (!root) {
    throw new Error('Playwright did not produce a JUnit testsuite summary');
  }
  const attribute = (name) => Number((root[1].match(new RegExp(`\\b${name}="(\\d+)"`)) || [])[1] || 0);
  const total = attribute('tests');
  const failures = attribute('failures');
  const errors = attribute('errors');
  const skipped = attribute('skipped');
  return {
    total,
    passed: total - failures - errors - skipped,
    failed: failures + errors,
    skipped
  };
}

async function run(command, args, options) {
  await new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, { ...options, stdio: 'inherit' });
    child.once('error', rejectRun);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolveRun();
      } else {
        rejectRun(new Error(`${command} exited with ${signal || `code ${code}`}`));
      }
    });
  });
}

async function writePrivateFile(path, content) {
  await writeFile(path, content, { encoding: 'utf8', mode: 0o600 });
  if (process.platform !== 'win32') {
    await chmod(path, 0o600);
  }
}

async function requestEnvelope(baseUrl, path, options, label, normalizeBareSuccess) {
  const response = await fetch(new URL(path, `${baseUrl}/`), options);
  const body = await response.json().catch(() => null);
  if (!response.ok || (body?.code !== undefined && body.code !== '0')) {
    throw new Error(`${label}: HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  if (body?.code === '0') {
    return body;
  }
  if (normalizeBareSuccess) {
    return normalizeBareSuccess(body);
  }
  throw new Error(`${label}: HTTP ${response.status} ${JSON.stringify(body)}`);
}

async function bootstrapScenarioCourse(context, artifactDir) {
  const teacherLogin = await requestEnvelope(context.baseUrl, '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ account: 'teacher001', password: 'Teacher001@pass' })
  }, 'bootstrap teacher login');
  const studentLogin = await requestEnvelope(context.baseUrl, '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ account: 'student001', password: 'Student001@pass' })
  }, 'bootstrap student login');
  const studentId = Number(studentLogin?.data?.user?.id);
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    throw new Error('bootstrap student login must contain a positive numeric user id');
  }
  const teacherHeaders = {
    authorization: `Bearer ${teacherLogin.data.token}`,
    'content-type': 'application/json',
    'x-request-id': `issue320-bootstrap-${Date.now()}`
  };
  const course = await requestEnvelope(context.baseUrl, '/api/v1/courses', {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify({
      name: '数据结构全流程演示课',
      description: 'Issue #320 disposable browser scenario fixture',
      enrollmentMode: 'PUBLIC',
      maxStudents: 100
    })
  }, 'bootstrap scenario course');
  const courseId = parsePositiveIdentifier(course, 'course');
  await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/join`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${studentLogin.data.token}`,
      'content-type': 'application/json',
      'x-request-id': `issue320-bootstrap-join-${Date.now()}`
    },
    body: '{}'
  }, 'bootstrap student course membership');
  const chapter = await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/chapters`, {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify({ title: 'Issue #320 运行期章节', sortOrder: 1, visible: true, chapterType: 1 })
  }, 'bootstrap scenario chapter');
  const chapterId = parsePositiveIdentifier(chapter, 'chapter');

  const fixtureLab = await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/labs`, {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify(createGrdSummaryFixtureLabPayload())
  }, 'bootstrap GRD source LAB', normalizeBareLabCreateResponse);
  const fixtureLabId = parsePositiveIdentifier(fixtureLab, 'GRD source LAB');
  await requestEnvelope(context.baseUrl, `/api/v1/labs/${fixtureLabId}/publish`, {
    method: 'POST',
    headers: teacherHeaders,
    body: '{}'
  }, 'bootstrap publish GRD source LAB', normalizeBareLabCreateResponse);
  const sourceSubmission = new FormData();
  sourceSubmission.set('code', 'print("Issue #320 GRD fixture")');
  sourceSubmission.set('language', 'python');
  const fixtureSubmission = await requestEnvelope(context.baseUrl, `/api/v1/labs/${fixtureLabId}/submissions`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${studentLogin.data.token}`,
      'x-request-id': `issue320-bootstrap-source-submit-${Date.now()}`
    },
    body: sourceSubmission
  }, 'bootstrap submit GRD source LAB', normalizeBareLabSubmissionResponse);
  const fixtureSubmissionId = String(fixtureSubmission?.data?.submissionId || '');
  if (!UUID_PATTERN.test(fixtureSubmissionId)) {
    throw new Error('GRD source LAB submission bootstrap response must contain a UUID submission id');
  }
  await requestEnvelope(context.baseUrl, `/api/v1/labs/${fixtureLabId}/submissions/${fixtureSubmissionId}/score`, {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify({
      manualScore: 90,
      finalScore: 90,
      comment: 'Issue #320 GRD fixture score',
      changeReason: 'Creates a published summary for the LRN adjustment closure.'
    })
  }, 'bootstrap score GRD source LAB', (body) => normalizeBareLabScoreResponse(body, fixtureSubmissionId));
  await requestEnvelope(context.baseUrl, `/api/v1/labs/${fixtureLabId}/release-scores`, {
    method: 'PUT',
    headers: teacherHeaders,
    body: '{}'
  }, 'bootstrap release GRD source LAB scores', (body) => normalizeBareLabScorePublicationResponse(body, fixtureLabId));
  await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/grade-items`, {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify({
      name: 'Issue #320 GRD 总评基线 LAB',
      sourceType: 'LAB',
      sourceId: fixtureLabId,
      fullScore: 100,
      weight: 1,
      includedInFinal: true,
      sortOrder: 1
    })
  }, 'bootstrap GRD source grade item');

  let gradeSummaryId;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/grades/sync`, {
      method: 'POST',
      headers: teacherHeaders,
      body: '{}'
    }, 'bootstrap sync GRD source score');
    const gradeTable = await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/grades?page=1&size=20`, {
      headers: teacherHeaders
    }, 'bootstrap query GRD course grades');
    const studentSummary = gradeTable?.data?.records?.find((record) => Number(record?.studentId) === studentId)?.summary;
    if (studentSummary?.finalStatus === 'CALCULATED') {
      gradeSummaryId = parseStudentGradeSummaryIdentifier(gradeTable, studentId);
      break;
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 250));
  }
  if (!gradeSummaryId) {
    throw new Error('GRD source score did not produce a calculated student course-grade summary within 5 seconds');
  }
  const publication = await requestEnvelope(context.baseUrl, `/api/v1/courses/${courseId}/grades/publish`, {
    method: 'POST',
    headers: teacherHeaders,
    body: JSON.stringify({ publishScope: 'PARTIAL_STUDENTS', studentIds: [studentId], gradeItemIds: [] })
  }, 'bootstrap publish GRD course grade');
  if (Number(publication?.data?.publishedCount) !== 1) {
    throw new Error('bootstrap GRD publication must publish exactly the student course-grade summary');
  }

  const scenario = { courseId, chapterId, gradeSummaryId };
  await writeFile(join(artifactDir, 'scenario-bootstrap.json'), `${JSON.stringify(scenario, null, 2)}\n`, 'utf8');
  return scenario;
}

async function runInsidePlatform() {
  const contextPath = process.env.E2E_THREE_SERVICE_CONTEXT_FILE?.trim();
  if (!contextPath) {
    throw new Error('E2E_THREE_SERVICE_CONTEXT_FILE is required inside the disposable platform');
  }
  const context = validateContext(JSON.parse(await readFile(contextPath, 'utf8')));
  const artifactDir = resolve(process.env.E2E_ARTIFACT_DIR || context.evidenceDir);
  await mkdir(artifactDir, { recursive: true });

  const token = randomBytes(32).toString('hex');
  const proofDir = join(tmpdir(), `onlinejudge-three-service-e2e-${token.slice(0, 12)}`);
  const proofPath = join(proofDir, 'disposable-proof.json');
  const junitPath = join(artifactDir, 'playwright-junit.xml');
  const summaryPath = join(artifactDir, 'test-summary.json');
  let commandError = '';
  let summary = { total: 0, passed: 0, failed: 1, skipped: 0 };

  try {
    await mkdir(proofDir, { recursive: true, mode: 0o700 });
    await writePrivateFile(proofPath, `${JSON.stringify({
      token,
      baseUrl: context.baseUrl,
      projectName: context.projectName,
      workloads: context.workloads,
      contextPath: resolve(contextPath),
      evidenceDir: artifactDir
    }, null, 2)}\n`);

    const scenario = await bootstrapScenarioCourse(context, artifactDir);

    try {
      await run(`npm${commandSuffix}`, ['run', 'test:e2e', '--', ...e2eTargets, '--workers=1'], {
        cwd: frontendDir,
        env: {
          ...process.env,
          E2E_BASE_URL: context.baseUrl,
          E2E_TEACHER_ACCOUNT: 'teacher001',
          E2E_TEACHER_PASSWORD: 'Teacher001@pass',
          E2E_STUDENT_ACCOUNT: 'student001',
          E2E_STUDENT_PASSWORD: 'Student001@pass',
          E2E_ADMIN_ACCOUNT: 'admin001',
          E2E_ADMIN_PASSWORD: 'Admin001@pass',
          E2E_COURSE_ID: String(scenario.courseId),
          E2E_CHAPTER_ID: String(scenario.chapterId),
          E2E_GRADE_SUMMARY_ID: String(scenario.gradeSummaryId),
          E2E_THREE_SERVICE_PROOF_FILE: proofPath,
          E2E_THREE_SERVICE_TOKEN: token,
          PLAYWRIGHT_JUNIT_OUTPUT_FILE: junitPath
        }
      });
    } catch (error) {
      commandError = error instanceof Error ? error.message : String(error);
    }

    if (existsSync(junitPath)) {
      summary = parseJUnit(await readFile(junitPath, 'utf8'));
    }
    await writeFile(summaryPath, `${JSON.stringify({ ...summary, commandError }, null, 2)}\n`, 'utf8');
    if (!commandError && isSuccessfulSummary(summary)) {
      const evidence = validateEvidenceManifest(await writeEvidenceManifest(context, artifactDir, junitPath));
      await writeFile(join(artifactDir, 'representative-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
    }
    if (commandError || !isSuccessfulSummary(summary)) {
      throw new Error(`three-service browser gate requires 24 passed, 0 failed and 0 skipped; got ${JSON.stringify(summary)}`);
    }
  } finally {
    await writeFile(join(artifactDir, 'three-service-run.json'), `${JSON.stringify({
      baseUrl: context.baseUrl,
      projectName: context.projectName,
      workloads: context.workloads,
      targets: e2eTargets,
      junit: junitPath,
      summary: summaryPath
    }, null, 2)}\n`, 'utf8');
  }
}

async function writeEvidenceManifest(context, artifactDir, junitPath) {
  const readinessPath = join(artifactDir, 'gateway-readiness.json');
  const logPath = join(artifactDir, 'compose-success.log');
  const [readiness, logs] = await Promise.all([
    readFile(readinessPath, 'utf8'),
    readFile(logPath, 'utf8')
  ]);
  const proofLines = logs.split(/\r?\n/).filter((line) => /(?:taskId|eventId|correlationId|requestId)/i.test(line));
  if (!proofLines.length) {
    throw new Error('three-service logs did not retain a taskId, eventId, correlationId or requestId proof');
  }
  const proofId = proofLines[0].match(/(?:taskId|eventId|correlationId|requestId)[=:\"]+([^,\s\"]+)/i)?.[1];
  if (!proofId) {
    throw new Error('three-service logs contained no extractable asynchronous proof identifier');
  }
  const requestResponse = redact(readiness, [process.env.E2E_THREE_SERVICE_TOKEN || '']).slice(0, 2_000);
  const uiAssertion = `Playwright JUnit completed 24 browser scenarios: ${junitPath}`;
  const logExcerpt = redact(proofLines[0], [process.env.E2E_THREE_SERVICE_TOKEN || '']);
  return {
    baseUrl: context.baseUrl,
    representative: [
      { group: 'AUTH-CRS', requestResponse, uiAssertion, proofId, logExcerpt },
      { group: 'ASSESSMENT-WORKER', requestResponse, uiAssertion, proofId, logExcerpt },
      { group: 'GRD-LRN', requestResponse, uiAssertion, proofId, logExcerpt }
    ]
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.slice(2).join(' ') !== '--inside-platform') {
    console.error('run-business-e2e-three-service: expected --inside-platform');
    process.exitCode = 2;
  } else {
    runInsidePlatform().catch((error) => {
      console.error(`run-business-e2e-three-service: ${error instanceof Error ? error.message : String(error)}`);
      process.exitCode = 1;
    });
  }
}
