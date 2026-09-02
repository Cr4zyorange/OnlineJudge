import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import http from "node:http";
import test from "node:test";

import {
  materializeRequest,
  renderCsvReport,
  renderMarkdownReport,
  runHttpLoadRound,
  summarizePreflight,
  validateFormalWindowEvidence,
} from "../issue-307-runner.mjs";

test("request templates use environment values without persisting credentials", () => {
  const request = materializeRequest(
    {
      method: "POST",
      pathTemplate: "/api/v1/homeworks/${OJ_PERF_HOMEWORK_ID}/submissions?sequence={{sequence}}",
      bodyTemplateEnv: "OJ_PERF_HOMEWORK_BODY",
      expectedStatuses: [202],
    },
    {
      environment: {
        OJ_PERF_HOMEWORK_ID: "42",
        OJ_PERF_HOMEWORK_BODY: '{"answer":"{{requestId}}","sequence":{{sequence}}}',
      },
      sequence: 7,
      requestId: "request-7",
    },
  );

  assert.equal(request.path, "/api/v1/homeworks/42/submissions?sequence=7");
  assert.equal(request.body, '{"answer":"request-7","sequence":7}');
  assert.deepEqual(request.expectedStatuses, [202]);
});

test("formal window evidence must prove readiness and an uncontaminated exclusive window", () => {
  const evidence = {
    environmentReadySignal: "ENVIRONMENT_READY issue=#318 sha=abc evidence=run",
    dockerDaemonReady: true,
    exclusiveWindow: true,
    hpaEnabled: false,
    e2eRunning: false,
    faultInjectionRunning: false,
    otherPressureRunning: false,
    datasetRestoreEvidence: "snapshot=dataset-v1 round-reset=verified",
    resourcePolicyEvidence: "cpu=4 memory=6144MiB limits=verified",
  };
  assert.doesNotThrow(() => validateFormalWindowEvidence(evidence));

  assert.throws(
    () => validateFormalWindowEvidence({ ...evidence, environmentReadySignal: "STARTABLE_BY #318" }),
    /ENVIRONMENT_READY/,
  );
  assert.throws(
    () => validateFormalWindowEvidence({ ...evidence, hpaEnabled: true }),
    /contaminated/i,
  );
  assert.throws(
    () => validateFormalWindowEvidence({ ...evidence, exclusiveWindow: false }),
    /exclusive/i,
  );
  assert.throws(
    () => validateFormalWindowEvidence({ ...evidence, datasetRestoreEvidence: null }),
    /dataset.*restore/i,
  );
});

test("preflight rejects an accepted status when its virtual-student success rate is below the gate", () => {
  const valid = {
    scenario: "my-grades",
    expectedStatuses: [200],
    minimumSuccessRatePercent: 100,
    responses: [
      { student: 1, attempt: 1, status: 200, responseFile: "responses/student-001-attempt-1.json" },
      { student: 2, attempt: 1, status: 200, responseFile: "responses/student-002-attempt-1.json" },
      { student: 3, attempt: 1, status: 200, responseFile: "responses/student-003-attempt-1.json" },
    ],
  };

  assert.deepEqual(summarizePreflight(valid), {
    ...valid,
    requestCount: 3,
    successfulRequestCount: 3,
    successRatePercent: 100,
  });

  assert.throws(
    () => summarizePreflight({
      ...valid,
      responses: [...valid.responses, { student: 4, attempt: 1, status: 401, responseFile: "responses/student-004-attempt-1.json" }],
    }),
    /success rate.*100/i,
  );
});

test("course-list preflight requires every API-visible total to equal the frozen dataset total", () => {
  const valid = {
    scenario: "course-list",
    expectedStatuses: [200],
    minimumSuccessRatePercent: 100,
    expectedApiVisibleCourseTotal: 105,
    responses: [
      { student: 1, attempt: 1, status: 200, responseFile: "responses/student-001-attempt-1.json", apiVisibleCourseTotal: 105 },
      { student: 2, attempt: 1, status: 200, responseFile: "responses/student-002-attempt-1.json", apiVisibleCourseTotal: 105 },
    ],
  };

  assert.doesNotThrow(() => summarizePreflight(valid));
  assert.throws(
    () => summarizePreflight({
      ...valid,
      responses: [{ ...valid.responses[0], apiVisibleCourseTotal: 106 }, valid.responses[1]],
    }),
    /API-visible.*105/i,
  );
  assert.throws(
    () => summarizePreflight({ ...valid, expectedApiVisibleCourseTotal: undefined }),
    /expected API-visible course total/i,
  );
});

test("HTTP runner excludes warmup, records raw samples and keeps resource measurements", async (context) => {
  const authorizations = new Set();
  const server = http.createServer((request, response) => {
    authorizations.add(request.headers.authorization);
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"ok":true}');
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();

  let sample = 0;
  const result = await runHttpLoadRound({
    baseUrl: `http://127.0.0.1:${port}`,
    bearerTokens: ["student-token-a", "student-token-b"],
    scenario: {
      id: "course-list",
      method: "GET",
      pathTemplate: "/api/v1/courses?page=0&size=20",
      expectedStatuses: [200],
    },
    environment: {},
    load: {
      warmupSeconds: 0.02,
      durationSeconds: 0.08,
      concurrency: 2,
      requestTimeoutMs: 1000,
    },
    resourceSampleIntervalMs: 10,
    sampleResources: async () => ({
      atMs: sample++ * 10,
      cpuPercent: 12.5,
      memoryMiB: 128,
    }),
  });

  assert.deepEqual(authorizations, new Set(["Bearer student-token-a", "Bearer student-token-b"]));
  assert.ok(result.requests.length > 0);
  assert.ok(result.requests.every(({ ok, status, durationMs }) => ok && status === 200 && durationMs >= 0));
  assert.ok(result.measuredDurationMs >= 70);
  assert.ok(result.resourceSamples.length > 0);
  assert.equal(JSON.stringify(result).includes("student-token-a"), false);
  assert.equal(JSON.stringify(result).includes("student-token-b"), false);
});

test("HTTP runner spaces each virtual student's requests at the configured interval", async (context) => {
  const server = http.createServer((_request, response) => {
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"ok":true}');
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();

  const result = await runHttpLoadRound({
    baseUrl: `http://127.0.0.1:${port}`,
    bearerToken: "paced-student-token",
    scenario: { id: "course-list", method: "GET", pathTemplate: "/api/v1/courses", expectedStatuses: [200] },
    environment: {},
    load: {
      warmupSeconds: 0.01,
      durationSeconds: 0.095,
      concurrency: 1,
      minimumRequestIntervalMs: 30,
      requestTimeoutMs: 1000,
    },
    resourceSampleIntervalMs: 10,
    sampleResources: async () => ({ atMs: 0, cpuPercent: 0, memoryMiB: 0 }),
  });

  assert.ok(result.requests.length >= 2, "the measured phase should issue more than one paced request");
  assert.ok(result.requests.length <= 4, "the interval must prevent an unbounded tight request loop");
});

test("formal measurement rechecks Docker exclusivity with every resource sample", () => {
  const source = readFileSync(new URL("../issue-307.mjs", import.meta.url), "utf8");
  assert.match(source, /assertExclusiveDockerContainers/);
  assert.match(source, /sampleResources:\s*async\s*\(\)\s*=>\s*\{[\s\S]*assertExclusiveDockerContainers/);
  const runnerSource = readFileSync(new URL("../issue-307-runner.mjs", import.meta.url), "utf8");
  assert.match(runnerSource, /"ps", "--no-trunc", "--format", "\{\{\.ID\}\}"/);
});

test("reports include every required unit and avoid causal performance claims", () => {
  const report = {
    issue: 307,
    generatedAt: "2026-09-01T00:00:00.000Z",
    machineFingerprint: "machine-a",
    datasetSha256: "a".repeat(64),
    baselines: {
      monolith: { sha: "1".repeat(40) },
      threeService: { sha: "2".repeat(40) },
    },
    units: {
      latency: "ms",
      throughput: "requests/second",
      errorRate: "percent",
      cpu: "percent",
      memory: "MiB",
    },
    rounds: [
      {
        architecture: "monolith",
        scenario: "course-list",
        round: 1,
        requestCount: 10,
        averageMs: 10,
        p95Ms: 20,
        throughputRequestsPerSecond: 100,
        successfulRequestCount: 10,
        successfulThroughputRequestsPerSecond: 100,
        errorRatePercent: 0,
        cpuAveragePercent: 10,
        cpuMaxPercent: 15,
        memoryAverageMiB: 100,
        memoryMaxMiB: 120,
      },
    ],
    summary: [],
    comparisons: [],
    interpretationBoundary: ["Observed deltas are not proof of cause."],
  };

  const markdown = renderMarkdownReport(report);
  const csv = renderCsvReport(report);
  assert.match(markdown, /P95 \(ms\)/);
  assert.match(markdown, /Successful throughput \(requests\/second\)/);
  assert.match(markdown, /CPU avg \(%\)/);
  assert.match(markdown, /Memory max \(MiB\)/);
  assert.match(markdown, /Observed deltas are not proof of cause/);
  assert.doesNotMatch(markdown, /microservices? (are|is) faster/i);
  assert.match(csv, /throughput_requests_per_second/);
  assert.match(csv, /monolith,course-list,1/);
});
