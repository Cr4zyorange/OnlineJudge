import assert from "node:assert/strict";
import test from "node:test";

import {
  aggregateComparison,
  parseDockerStatsLine,
  percentileNearestRank,
  validatePlan,
} from "../issue-307-lib.mjs";

const MONOLITH_SHA = "78715f21288782a2c7ef1d9c23f933c46569b108";
const THREE_SERVICE_SHA = "f948869799e2e561d6cfa2208acaf26627aa1ba1";

function validPlan() {
  return {
    schemaVersion: 1,
    issue: 307,
    baselines: {
      monolith: { ref: "monolith-start", sha: MONOLITH_SHA },
      threeService: {
        ref: "origin/dev",
        sha: THREE_SERVICE_SHA,
        contentSha: "921af331e785551107466c8267d5f988436e1d14",
      },
    },
    environment: {
      datasetId: "issue-307-v1",
      datasetSha256: "a".repeat(64),
      resourceBudget: { cpuCores: 4, memoryMiB: 6144 },
      maxBusinessConcurrency: 20,
    },
    load: {
      rounds: 3,
      warmupSeconds: 20,
      durationSeconds: 60,
      concurrency: 10,
      minimumRequestIntervalMs: 1000,
      requestTimeoutMs: 10000,
    },
    preflight: {
      requestsPerVirtualStudent: 1,
      minimumSuccessRatePercent: 100,
    },
    scenarios: [
      {
        id: "course-list",
        category: "read",
        method: "GET",
        pathTemplate: "/api/v1/courses?page=0&size=20",
        expectedStatuses: [200],
      },
      {
        id: "homework-submission",
        category: "write",
        method: "POST",
        pathTemplate: "/api/v1/homeworks/${OJ_PERF_HOMEWORK_ID}/submissions",
        bodyTemplateEnv: "OJ_PERF_HOMEWORK_BODY",
        expectedStatuses: [200, 201, 202],
      },
      {
        id: "my-grades",
        category: "split-main-path-read",
        method: "GET",
        pathTemplate: "/api/v1/courses/${OJ_PERF_COURSE_ID}/my-grades",
        expectedStatuses: [200],
      },
    ],
    architectures: [
      {
        id: "monolith",
        baseline: "monolith",
        baseUrlEnv: "OJ_PERF_MONOLITH_URL",
        bearerTokenEnv: "OJ_PERF_MONOLITH_TOKEN",
        containersEnv: "OJ_PERF_MONOLITH_CONTAINERS",
      },
      {
        id: "three-service",
        baseline: "threeService",
        baseUrlEnv: "OJ_PERF_THREE_SERVICE_URL",
        bearerTokenEnv: "OJ_PERF_THREE_SERVICE_TOKEN",
        containersEnv: "OJ_PERF_THREE_SERVICE_CONTAINERS",
      },
    ],
  };
}

function rawRound({ architecture, scenario, round, contaminated = false, machine = "machine-a" }) {
  const baselineSha = architecture === "monolith" ? MONOLITH_SHA : THREE_SERVICE_SHA;
  return {
    schemaVersion: 1,
    issue: 307,
    architecture,
    scenario,
    round,
    baselineSha,
    machineFingerprint: machine,
    datasetSha256: "a".repeat(64),
    resourceBudget: { cpuCores: 4, memoryMiB: 6144 },
    load: {
      warmupSeconds: 20,
      durationSeconds: 60,
      concurrency: 10,
      minimumRequestIntervalMs: 1000,
      requestTimeoutMs: 10000,
    },
    formalWindow: {
      environmentReadySignal: "ENVIRONMENT_READY issue=#318 sha=example",
      dockerDaemonReady: true,
      exclusiveWindow: true,
      hpaEnabled: false,
      e2eRunning: false,
      faultInjectionRunning: false,
      otherPressureRunning: contaminated,
      datasetRestoreEvidence: "snapshot=dataset-v1 round-reset=verified",
      resourcePolicyEvidence: "cpu=4 memory=6144MiB limits=verified",
    },
    measuredDurationMs: 1000,
    requests: [
      { durationMs: 10, status: 200, ok: true },
      { durationMs: 20, status: 200, ok: true },
      { durationMs: 30, status: 500, ok: false },
      { durationMs: 40, status: 200, ok: true },
    ],
    resourceSamples: [
      { atMs: 0, cpuPercent: 10, memoryMiB: 100 },
      { atMs: 1000, cpuPercent: 30, memoryMiB: 140 },
    ],
  };
}

test("AC-307 plan freezes two baselines, two-to-three representative APIs and at least three rounds", () => {
  assert.doesNotThrow(() => validatePlan(validPlan()));

  const tooFewRounds = validPlan();
  tooFewRounds.load.rounds = 2;
  assert.throws(() => validatePlan(tooFewRounds), /at least 3 rounds/i);

  const excessiveConcurrency = validPlan();
  excessiveConcurrency.load.concurrency = 21;
  assert.throws(() => validatePlan(excessiveConcurrency), /concurrency/i);

  const missingPreflight = validPlan();
  delete missingPreflight.preflight;
  assert.throws(() => validatePlan(missingPreflight), /preflight/i);

  const weakPreflight = validPlan();
  weakPreflight.preflight.minimumSuccessRatePercent = 99;
  assert.throws(() => validatePlan(weakPreflight), /100/i);

  const readOnly = validPlan();
  readOnly.scenarios = readOnly.scenarios.filter((scenario) => scenario.category !== "write");
  assert.throws(() => validatePlan(readOnly), /read and write/i);
});

test("nearest-rank P95 remains exactly reproducible from raw request durations", () => {
  assert.equal(percentileNearestRank([1, 2, 3, 4, 100], 0.95), 100);
  assert.equal(percentileNearestRank([40, 10, 30, 20], 0.5), 20);
});

test("aggregate keeps every round and reports P95, throughput, errors, CPU and memory with units", () => {
  const plan = validPlan();
  const raw = [];
  for (const architecture of ["monolith", "three-service"]) {
    for (const scenario of plan.scenarios.map(({ id }) => id)) {
      for (let round = 1; round <= 3; round += 1) {
        raw.push(rawRound({ architecture, scenario, round }));
      }
    }
  }

  const result = aggregateComparison(plan, raw);
  assert.equal(result.rounds.length, 18);
  assert.equal(result.summary.length, 6);
  assert.deepEqual(result.units, {
    latency: "ms",
    throughput: "requests/second",
    errorRate: "percent",
    cpu: "percent",
    memory: "MiB",
  });
  assert.equal(result.rounds[0].p95Ms, 40);
  assert.equal(result.rounds[0].throughputRequestsPerSecond, 4);
  assert.equal(result.rounds[0].successfulRequestCount, 3);
  assert.equal(result.rounds[0].successfulThroughputRequestsPerSecond, 3);
  assert.equal(result.rounds[0].errorRatePercent, 25);
  assert.equal(result.rounds[0].cpuAveragePercent, 20);
  assert.equal(result.rounds[0].memoryMaxMiB, 140);
});

test("aggregate rejects cherry-picked, contaminated or incomparable evidence", () => {
  const plan = validPlan();
  const complete = [];
  for (const architecture of ["monolith", "three-service"]) {
    for (const scenario of plan.scenarios.map(({ id }) => id)) {
      for (let round = 1; round <= 3; round += 1) {
        complete.push(rawRound({ architecture, scenario, round }));
      }
    }
  }

  assert.throws(() => aggregateComparison(plan, complete.slice(1)), /missing round/i);

  const contaminated = structuredClone(complete);
  contaminated[0].formalWindow.hpaEnabled = true;
  assert.throws(() => aggregateComparison(plan, contaminated), /contaminated/i);

  const differentMachine = structuredClone(complete);
  differentMachine.at(-1).machineFingerprint = "machine-b";
  assert.throws(() => aggregateComparison(plan, differentMachine), /same machine/i);

  const nonExclusive = structuredClone(complete);
  nonExclusive[0].formalWindow.exclusiveWindow = false;
  assert.throws(() => aggregateComparison(plan, nonExclusive), /exclusive/i);

  const missingReset = structuredClone(complete);
  missingReset[0].formalWindow.datasetRestoreEvidence = null;
  assert.throws(() => aggregateComparison(plan, missingReset), /dataset.*restore/i);
});

test("docker stats parser exposes aggregateable CPU and memory values", () => {
  assert.deepEqual(
    parseDockerStatsLine(
      JSON.stringify({ Name: "course", CPUPerc: "12.50%", MemUsage: "128MiB / 1GiB" }),
    ),
    { container: "course", cpuPercent: 12.5, memoryMiB: 128 },
  );
  assert.equal(
    parseDockerStatsLine(
      JSON.stringify({ Name: "mysql", CPUPerc: "1.00%", MemUsage: "1.5GiB / 4GiB" }),
    ).memoryMiB,
    1536,
  );
});
