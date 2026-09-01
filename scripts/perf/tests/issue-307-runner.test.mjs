import assert from "node:assert/strict";
import http from "node:http";
import test from "node:test";

import {
  materializeRequest,
  renderCsvReport,
  renderMarkdownReport,
  runHttpLoadRound,
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

test("HTTP runner excludes warmup, records raw samples and keeps resource measurements", async (context) => {
  let authorization = null;
  const server = http.createServer((request, response) => {
    authorization = request.headers.authorization;
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"ok":true}');
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();

  let sample = 0;
  const result = await runHttpLoadRound({
    baseUrl: `http://127.0.0.1:${port}`,
    bearerToken: "not-written-to-result",
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

  assert.equal(authorization, "Bearer not-written-to-result");
  assert.ok(result.requests.length > 0);
  assert.ok(result.requests.every(({ ok, status, durationMs }) => ok && status === 200 && durationMs >= 0));
  assert.ok(result.measuredDurationMs >= 70);
  assert.ok(result.resourceSamples.length > 0);
  assert.equal(JSON.stringify(result).includes("not-written-to-result"), false);
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
  assert.match(markdown, /CPU avg \(%\)/);
  assert.match(markdown, /Memory max \(MiB\)/);
  assert.match(markdown, /Observed deltas are not proof of cause/);
  assert.doesNotMatch(markdown, /microservices? (are|is) faster/i);
  assert.match(csv, /throughput_requests_per_second/);
  assert.match(csv, /monolith,course-list,1/);
});
