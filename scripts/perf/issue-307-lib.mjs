import { createHash } from "node:crypto";
import os from "node:os";

const SHA_PATTERN = /^[0-9a-f]{40}$/;
const DATASET_PATTERN = /^[0-9a-f]{64}$/;

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function isPositiveNumber(value) {
  return Number.isFinite(value) && value > 0;
}

function stableJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

function sameValue(left, right) {
  return stableJson(left) === stableJson(right);
}

function round(value, digits = 3) {
  const factor = 10 ** digits;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

export function validatePlan(plan) {
  invariant(plan && typeof plan === "object", "plan must be an object");
  invariant(plan.schemaVersion === 1, "plan schemaVersion must be 1");
  invariant(plan.issue === 307, "plan must belong to issue #307");

  invariant(plan.baselines && typeof plan.baselines === "object", "plan baselines are required");
  for (const name of ["monolith", "threeService"]) {
    const baseline = plan.baselines[name];
    invariant(baseline && typeof baseline === "object", `${name} baseline is required`);
    invariant(typeof baseline.ref === "string" && baseline.ref.length > 0, `${name} ref is required`);
    invariant(SHA_PATTERN.test(baseline.sha), `${name} SHA must be a full lowercase Git SHA`);
  }
  invariant(
    plan.baselines.monolith.sha !== plan.baselines.threeService.sha,
    "monolith and three-service baselines must be different commits",
  );
  if (plan.baselines.threeService.contentSha !== undefined) {
    invariant(
      SHA_PATTERN.test(plan.baselines.threeService.contentSha),
      "three-service contentSha must be a full lowercase Git SHA",
    );
  }

  const environment = plan.environment;
  invariant(environment && typeof environment === "object", "environment is required");
  invariant(
    typeof environment.datasetId === "string" && environment.datasetId.length > 0,
    "environment datasetId is required",
  );
  invariant(
    DATASET_PATTERN.test(environment.datasetSha256),
    "environment datasetSha256 must be a lowercase SHA-256",
  );
  invariant(
    Number.isInteger(environment.maxBusinessConcurrency) && environment.maxBusinessConcurrency > 0,
    "maxBusinessConcurrency must be a positive integer",
  );
  invariant(
    isPositiveNumber(environment.resourceBudget?.cpuCores) &&
      isPositiveNumber(environment.resourceBudget?.memoryMiB),
    "resourceBudget must define positive cpuCores and memoryMiB",
  );

  const load = plan.load;
  invariant(load && typeof load === "object", "load configuration is required");
  invariant(Number.isInteger(load.rounds) && load.rounds >= 3, "at least 3 rounds are required");
  invariant(isPositiveNumber(load.warmupSeconds), "warmupSeconds must be positive");
  invariant(isPositiveNumber(load.durationSeconds), "durationSeconds must be positive");
  invariant(Number.isInteger(load.concurrency) && load.concurrency > 0, "concurrency must be positive");
  invariant(
    load.concurrency <= environment.maxBusinessConcurrency,
    `concurrency must not exceed ${environment.maxBusinessConcurrency}`,
  );
  invariant(isPositiveNumber(load.requestTimeoutMs), "requestTimeoutMs must be positive");

  invariant(
    Array.isArray(plan.scenarios) && plan.scenarios.length >= 2 && plan.scenarios.length <= 3,
    "plan must define 2 to 3 representative scenarios",
  );
  const scenarioIds = new Set();
  let hasRead = false;
  let hasWrite = false;
  for (const scenario of plan.scenarios) {
    invariant(typeof scenario.id === "string" && scenario.id.length > 0, "scenario id is required");
    invariant(!scenarioIds.has(scenario.id), `duplicate scenario id: ${scenario.id}`);
    scenarioIds.add(scenario.id);
    invariant(["GET", "POST", "PUT", "PATCH", "DELETE"].includes(scenario.method), `${scenario.id} method is invalid`);
    invariant(
      typeof scenario.pathTemplate === "string" && scenario.pathTemplate.startsWith("/api/"),
      `${scenario.id} pathTemplate must be a public /api/ path`,
    );
    invariant(
      Array.isArray(scenario.expectedStatuses) &&
        scenario.expectedStatuses.length > 0 &&
        scenario.expectedStatuses.every((status) => Number.isInteger(status) && status >= 100 && status <= 599),
      `${scenario.id} expectedStatuses are required`,
    );
    hasRead ||= scenario.category.includes("read") || scenario.method === "GET";
    hasWrite ||= scenario.category.includes("write") || scenario.method !== "GET";
  }
  invariant(hasRead && hasWrite, "scenarios must represent both read and write behavior");

  invariant(Array.isArray(plan.architectures) && plan.architectures.length === 2, "exactly two architectures are required");
  const architectureIds = new Set(plan.architectures.map(({ id }) => id));
  invariant(
    architectureIds.size === 2 && architectureIds.has("monolith") && architectureIds.has("three-service"),
    "architectures must be monolith and three-service",
  );
  for (const architecture of plan.architectures) {
    invariant(plan.baselines[architecture.baseline], `${architecture.id} references an unknown baseline`);
    for (const field of ["baseUrlEnv", "bearerTokenEnv", "containersEnv"]) {
      invariant(
        typeof architecture[field] === "string" && architecture[field].startsWith("OJ_PERF_"),
        `${architecture.id} ${field} must name an OJ_PERF_ environment variable`,
      );
    }
  }
  return plan;
}

export function percentileNearestRank(values, percentile) {
  invariant(Array.isArray(values) && values.length > 0, "percentile requires at least one value");
  invariant(percentile > 0 && percentile <= 1, "percentile must be in (0, 1]");
  const sorted = values.map(Number).sort((left, right) => left - right);
  invariant(sorted.every(Number.isFinite), "percentile values must be finite numbers");
  return sorted[Math.max(0, Math.ceil(percentile * sorted.length) - 1)];
}

function memoryToMiB(raw) {
  const match = String(raw).trim().match(/^([0-9.]+)\s*(B|KiB|MiB|GiB|TiB)$/i);
  invariant(match, `unsupported Docker memory value: ${raw}`);
  const value = Number(match[1]);
  const multipliers = { B: 1 / (1024 * 1024), KIB: 1 / 1024, MIB: 1, GIB: 1024, TIB: 1024 * 1024 };
  return value * multipliers[match[2].toUpperCase()];
}

export function parseDockerStatsLine(line) {
  const value = JSON.parse(line);
  const cpuPercent = Number.parseFloat(String(value.CPUPerc ?? "").replace("%", ""));
  const usedMemory = String(value.MemUsage ?? "").split("/")[0].trim();
  invariant(Number.isFinite(cpuPercent), `invalid Docker CPU percentage: ${value.CPUPerc}`);
  return {
    container: String(value.Name ?? value.Container ?? "unknown"),
    cpuPercent,
    memoryMiB: round(memoryToMiB(usedMemory), 6),
  };
}

export function machineFingerprint() {
  const descriptor = {
    platform: os.platform(),
    release: os.release(),
    arch: os.arch(),
    cpuModel: os.cpus()[0]?.model ?? "unknown",
    cpuCount: os.cpus().length,
    totalMemoryBytes: os.totalmem(),
  };
  return {
    descriptor,
    sha256: createHash("sha256").update(stableJson(descriptor)).digest("hex"),
  };
}

function metricsForRound(raw) {
  invariant(Array.isArray(raw.requests) && raw.requests.length > 0, "raw round has no request samples");
  invariant(isPositiveNumber(raw.measuredDurationMs), "raw round measuredDurationMs must be positive");
  invariant(
    Array.isArray(raw.resourceSamples) && raw.resourceSamples.length > 0,
    "raw round has no resource samples",
  );
  const durations = raw.requests.map(({ durationMs }) => Number(durationMs));
  invariant(durations.every((value) => Number.isFinite(value) && value >= 0), "request durations must be finite and non-negative");
  const failures = raw.requests.filter(({ ok }) => !ok).length;
  const cpu = raw.resourceSamples.map(({ cpuPercent }) => Number(cpuPercent));
  const memory = raw.resourceSamples.map(({ memoryMiB }) => Number(memoryMiB));
  invariant(cpu.every(Number.isFinite), "resource CPU samples must be finite");
  invariant(memory.every(Number.isFinite), "resource memory samples must be finite");
  return {
    architecture: raw.architecture,
    scenario: raw.scenario,
    round: raw.round,
    requestCount: raw.requests.length,
    averageMs: round(durations.reduce((sum, value) => sum + value, 0) / durations.length),
    p95Ms: round(percentileNearestRank(durations, 0.95)),
    throughputRequestsPerSecond: round(raw.requests.length / (raw.measuredDurationMs / 1000)),
    errorRatePercent: round((failures / raw.requests.length) * 100),
    cpuAveragePercent: round(cpu.reduce((sum, value) => sum + value, 0) / cpu.length),
    cpuMaxPercent: round(Math.max(...cpu)),
    memoryAverageMiB: round(memory.reduce((sum, value) => sum + value, 0) / memory.length),
    memoryMaxMiB: round(Math.max(...memory)),
  };
}

function assertFormalWindow(raw) {
  const window = raw.formalWindow;
  invariant(
    typeof window?.environmentReadySignal === "string" && window.environmentReadySignal.startsWith("ENVIRONMENT_READY"),
    `${raw.architecture}/${raw.scenario}/round-${raw.round} lacks ENVIRONMENT_READY evidence`,
  );
  invariant(
    window.dockerDaemonReady === true,
    `${raw.architecture}/${raw.scenario}/round-${raw.round} lacks Docker daemon readiness evidence`,
  );
  invariant(
    window.exclusiveWindow === true,
    `${raw.architecture}/${raw.scenario}/round-${raw.round} lacks an exclusive formal window`,
  );
  invariant(
    typeof window.datasetRestoreEvidence === "string" && window.datasetRestoreEvidence.length > 0,
    `${raw.architecture}/${raw.scenario}/round-${raw.round} lacks dataset restore evidence`,
  );
  invariant(
    typeof window.resourcePolicyEvidence === "string" && window.resourcePolicyEvidence.length > 0,
    `${raw.architecture}/${raw.scenario}/round-${raw.round} lacks resource policy evidence`,
  );
  const contaminants = [
    ["HPA", window.hpaEnabled],
    ["E2E", window.e2eRunning],
    ["fault injection", window.faultInjectionRunning],
    ["other pressure", window.otherPressureRunning],
  ].filter(([, active]) => active);
  invariant(
    contaminants.length === 0,
    `${raw.architecture}/${raw.scenario}/round-${raw.round} is contaminated by ${contaminants
      .map(([name]) => name)
      .join(", ")}`,
  );
}

function percentDelta(current, baseline) {
  if (baseline === 0) {
    return null;
  }
  return round(((current - baseline) / baseline) * 100);
}

export function aggregateComparison(plan, rawRounds) {
  validatePlan(plan);
  invariant(Array.isArray(rawRounds), "rawRounds must be an array");

  const architectureOrder = new Map(plan.architectures.map(({ id }, index) => [id, index]));
  const scenarioOrder = new Map(plan.scenarios.map(({ id }, index) => [id, index]));
  const expected = new Set();
  for (const { id: architecture } of plan.architectures) {
    for (const { id: scenario } of plan.scenarios) {
      for (let run = 1; run <= plan.load.rounds; run += 1) {
        expected.add(`${architecture}/${scenario}/${run}`);
      }
    }
  }

  const seen = new Set();
  let sharedMachine = null;
  for (const raw of rawRounds) {
    const key = `${raw.architecture}/${raw.scenario}/${raw.round}`;
    invariant(expected.has(key), `unexpected round: ${key}`);
    invariant(!seen.has(key), `duplicate round: ${key}`);
    seen.add(key);
    assertFormalWindow(raw);

    const architecture = plan.architectures.find(({ id }) => id === raw.architecture);
    const expectedSha = plan.baselines[architecture.baseline].sha;
    invariant(raw.baselineSha === expectedSha, `${key} baseline SHA does not match the frozen plan`);
    invariant(raw.datasetSha256 === plan.environment.datasetSha256, `${key} does not use the same dataset`);
    invariant(sameValue(raw.resourceBudget, plan.environment.resourceBudget), `${key} does not use comparable resources`);
    invariant(
      sameValue(raw.load, {
        warmupSeconds: plan.load.warmupSeconds,
        durationSeconds: plan.load.durationSeconds,
        concurrency: plan.load.concurrency,
        requestTimeoutMs: plan.load.requestTimeoutMs,
      }),
      `${key} does not use the same load configuration`,
    );
    if (sharedMachine === null) {
      sharedMachine = raw.machineFingerprint;
    }
    invariant(raw.machineFingerprint === sharedMachine, `${key} was not executed on the same machine`);
  }
  for (const key of expected) {
    invariant(seen.has(key), `missing round: ${key}`);
  }

  const orderedRaw = [...rawRounds].sort(
    (left, right) =>
      architectureOrder.get(left.architecture) - architectureOrder.get(right.architecture) ||
      scenarioOrder.get(left.scenario) - scenarioOrder.get(right.scenario) ||
      left.round - right.round,
  );
  const rounds = orderedRaw.map(metricsForRound);
  const summary = [];
  for (const { id: architecture } of plan.architectures) {
    for (const { id: scenario } of plan.scenarios) {
      const group = orderedRaw.filter((raw) => raw.architecture === architecture && raw.scenario === scenario);
      const requests = group.flatMap(({ requests: samples }) => samples);
      const durations = requests.map(({ durationMs }) => Number(durationMs));
      const resources = group.flatMap(({ resourceSamples }) => resourceSamples);
      const totalMeasuredMs = group.reduce((sum, { measuredDurationMs }) => sum + measuredDurationMs, 0);
      const failures = requests.filter(({ ok }) => !ok).length;
      summary.push({
        architecture,
        scenario,
        roundCount: group.length,
        requestCount: requests.length,
        averageMs: round(durations.reduce((sum, value) => sum + value, 0) / durations.length),
        p95Ms: round(percentileNearestRank(durations, 0.95)),
        throughputRequestsPerSecond: round(requests.length / (totalMeasuredMs / 1000)),
        errorRatePercent: round((failures / requests.length) * 100),
        cpuAveragePercent: round(
          resources.reduce((sum, { cpuPercent }) => sum + Number(cpuPercent), 0) / resources.length,
        ),
        cpuMaxPercent: round(Math.max(...resources.map(({ cpuPercent }) => Number(cpuPercent)))),
        memoryAverageMiB: round(
          resources.reduce((sum, { memoryMiB }) => sum + Number(memoryMiB), 0) / resources.length,
        ),
        memoryMaxMiB: round(Math.max(...resources.map(({ memoryMiB }) => Number(memoryMiB)))),
      });
    }
  }

  const comparisons = plan.scenarios.map(({ id: scenario }) => {
    const monolith = summary.find((item) => item.architecture === "monolith" && item.scenario === scenario);
    const threeService = summary.find(
      (item) => item.architecture === "three-service" && item.scenario === scenario,
    );
    return {
      scenario,
      threeServiceMinusMonolith: {
        p95Percent: percentDelta(threeService.p95Ms, monolith.p95Ms),
        throughputPercent: percentDelta(
          threeService.throughputRequestsPerSecond,
          monolith.throughputRequestsPerSecond,
        ),
        errorRatePercentagePoints: round(threeService.errorRatePercent - monolith.errorRatePercent),
        cpuAveragePercentagePoints: round(threeService.cpuAveragePercent - monolith.cpuAveragePercent),
        memoryAverageMiB: round(threeService.memoryAverageMiB - monolith.memoryAverageMiB),
      },
    };
  });

  return {
    schemaVersion: 1,
    issue: 307,
    generatedAt: new Date().toISOString(),
    machineFingerprint: sharedMachine,
    datasetSha256: plan.environment.datasetSha256,
    baselines: plan.baselines,
    units: {
      latency: "ms",
      throughput: "requests/second",
      errorRate: "percent",
      cpu: "percent",
      memory: "MiB",
    },
    rounds,
    summary,
    comparisons,
    interpretationBoundary: [
      "The report records observed deltas; it does not claim an unmeasured cause.",
      "Candidate causes must be supported by process, network, serialization, connection-pool or cache evidence.",
      "All configured rounds are included; favorable rounds are never selected or discarded.",
    ],
  };
}
