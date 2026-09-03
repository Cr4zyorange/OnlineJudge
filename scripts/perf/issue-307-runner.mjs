import { randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { performance } from "node:perf_hooks";

import { parseDockerStatsLine } from "./issue-307-lib.mjs";

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function replaceTemplate(template, { environment, sequence, requestId }) {
  if (template === undefined || template === null) {
    return template;
  }
  return String(template)
    .replace(/\$\{([A-Z][A-Z0-9_]*)\}/g, (_match, name) => {
      invariant(name.startsWith("OJ_PERF_"), `template variable ${name} must use the OJ_PERF_ prefix`);
      invariant(environment[name] !== undefined && environment[name] !== "", `missing environment variable ${name}`);
      return String(environment[name]);
    })
    .replaceAll("{{sequence}}", String(sequence))
    .replaceAll("{{requestId}}", requestId);
}

export function materializeRequest(scenario, { environment, sequence, requestId }) {
  const path = replaceTemplate(scenario.pathTemplate, { environment, sequence, requestId });
  let body;
  if (scenario.bodyTemplateEnv) {
    const template = environment[scenario.bodyTemplateEnv];
    invariant(template !== undefined && template !== "", `missing environment variable ${scenario.bodyTemplateEnv}`);
    body = replaceTemplate(template, { environment, sequence, requestId });
  } else if (scenario.bodyTemplate !== undefined) {
    body = replaceTemplate(scenario.bodyTemplate, { environment, sequence, requestId });
  }
  return {
    method: scenario.method,
    path,
    body,
    expectedStatuses: [...scenario.expectedStatuses],
  };
}

export function validateFormalWindowEvidence(evidence) {
  invariant(evidence && typeof evidence === "object", "formal window evidence is required");
  invariant(
    typeof evidence.environmentReadySignal === "string" &&
      evidence.environmentReadySignal.startsWith("ENVIRONMENT_READY"),
    "formal counting requires the ENVIRONMENT_READY signal from #318",
  );
  invariant(evidence.dockerDaemonReady === true, "formal counting requires a ready Docker daemon");
  invariant(evidence.exclusiveWindow === true, "formal counting requires an exclusive test window");
  invariant(
    typeof evidence.datasetRestoreEvidence === "string" && evidence.datasetRestoreEvidence.length > 0,
    "formal counting requires dataset restore evidence for the current round",
  );
  invariant(
    typeof evidence.resourcePolicyEvidence === "string" && evidence.resourcePolicyEvidence.length > 0,
    "formal counting requires resource policy evidence",
  );
  const contaminants = [
    ["HPA", evidence.hpaEnabled],
    ["E2E", evidence.e2eRunning],
    ["fault injection", evidence.faultInjectionRunning],
    ["other pressure", evidence.otherPressureRunning],
  ].filter(([, active]) => active !== false);
  invariant(
    contaminants.length === 0,
    `formal test window is contaminated or unverified: ${contaminants.map(([name]) => name).join(", ")}`,
  );
  return evidence;
}

/**
 * Makes the protected-API preflight auditable without ever retaining a bearer
 * token or a response body.  A single happy-path request is not enough: every
 * virtual student that will enter the measured window must meet the declared
 * success-rate gate first.
 */
export function summarizePreflight({
  scenario,
  expectedStatuses,
  minimumSuccessRatePercent,
  expectedApiVisibleCourseTotal,
  responses,
}) {
  invariant(typeof scenario === "string" && scenario.length > 0, "preflight scenario is required");
  invariant(
    Array.isArray(expectedStatuses) && expectedStatuses.length > 0 &&
      expectedStatuses.every((status) => Number.isInteger(status) && status >= 100 && status <= 599),
    "preflight expected statuses are required",
  );
  invariant(
    Number.isFinite(minimumSuccessRatePercent) && minimumSuccessRatePercent > 0 && minimumSuccessRatePercent <= 100,
    "preflight minimum success rate must be in (0, 100]",
  );
  invariant(Array.isArray(responses) && responses.length > 0, "preflight responses are required");

  const seen = new Set();
  const requiresCourseTotal = scenario === "course-list";
  if (requiresCourseTotal) {
    invariant(
      Number.isInteger(expectedApiVisibleCourseTotal) && expectedApiVisibleCourseTotal > 0,
      "preflight expected API-visible course total is required",
    );
  }
  const sanitized = responses.map(({ student, attempt, status, responseFile, apiVisibleCourseTotal }) => {
    invariant(Number.isInteger(student) && student > 0, "preflight student must be a positive integer");
    invariant(Number.isInteger(attempt) && attempt > 0, "preflight attempt must be a positive integer");
    invariant(Number.isInteger(status) && status >= 0 && status <= 599, "preflight status is invalid");
    invariant(typeof responseFile === "string" && responseFile.length > 0, "preflight response file is required");
    const key = `${student}:${attempt}`;
    invariant(!seen.has(key), `duplicate preflight response ${key}`);
    seen.add(key);
    if (requiresCourseTotal) {
      invariant(
        apiVisibleCourseTotal === expectedApiVisibleCourseTotal,
        `course-list preflight API-visible total ${apiVisibleCourseTotal} does not equal ${expectedApiVisibleCourseTotal}`,
      );
      return { student, attempt, status, responseFile, apiVisibleCourseTotal };
    }
    return { student, attempt, status, responseFile };
  });
  const successfulRequestCount = sanitized.filter(({ status }) => expectedStatuses.includes(status)).length;
  const requestCount = sanitized.length;
  const successRatePercent = Math.round((successfulRequestCount / requestCount) * 100000) / 1000;
  invariant(
    successRatePercent >= minimumSuccessRatePercent,
    `preflight success rate ${successRatePercent}% is below required ${minimumSuccessRatePercent}% for ${scenario}`,
  );
  const summary = {
    scenario,
    expectedStatuses: [...expectedStatuses],
    minimumSuccessRatePercent,
    responses: sanitized,
    requestCount,
    successfulRequestCount,
    successRatePercent,
  };
  if (requiresCourseTotal) summary.expectedApiVisibleCourseTotal = expectedApiVisibleCourseTotal;
  return summary;
}

async function performRequest({ baseUrl, bearerToken, scenario, environment, sequence, requestTimeoutMs }) {
  const requestId = randomUUID();
  const request = materializeRequest(scenario, { environment, sequence, requestId });
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);
  const headers = {
    accept: "application/json",
    "x-request-id": requestId,
  };
  if (bearerToken) {
    headers.authorization = `Bearer ${bearerToken}`;
  }
  if (request.body !== undefined) {
    headers["content-type"] = scenario.contentType ?? "application/json";
  }
  const started = performance.now();
  try {
    const response = await fetch(new URL(request.path, baseUrl), {
      method: request.method,
      headers,
      body: request.body,
      signal: controller.signal,
    });
    await response.arrayBuffer();
    return {
      requestId,
      durationMs: Math.max(0, performance.now() - started),
      status: response.status,
      ok: request.expectedStatuses.includes(response.status),
    };
  } catch (error) {
    return {
      requestId,
      durationMs: Math.max(0, performance.now() - started),
      status: 0,
      ok: false,
      error: error?.name === "AbortError" ? "request-timeout" : "request-failed",
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function runPhase({ durationSeconds, concurrency, minimumRequestIntervalMs, execute, collect }) {
  const started = performance.now();
  const deadline = started + durationSeconds * 1000;
  let sequence = 0;
  const samples = [];
  async function worker(workerIndex) {
    if (minimumRequestIntervalMs > 0) {
      await delay((minimumRequestIntervalMs / concurrency) * workerIndex);
    }
    while (performance.now() < deadline) {
      const requestStarted = performance.now();
      const current = sequence;
      sequence += 1;
      const sample = await execute(current, workerIndex);
      if (collect) {
        samples.push(sample);
      }
      const remainingInterval = requestStarted + minimumRequestIntervalMs - performance.now();
      if (remainingInterval > 0) {
        await delay(remainingInterval);
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, (_, workerIndex) => worker(workerIndex)));
  return { samples, elapsedMs: performance.now() - started };
}

export function selectBearerToken({ bearerToken, bearerTokens }, workerIndex) {
  const tokens = bearerTokens ?? (bearerToken ? [bearerToken] : []);
  invariant(Array.isArray(tokens) && tokens.length > 0, "at least one bearer token is required");
  invariant(tokens.every((token) => typeof token === "string" && token.length > 0), "bearer tokens must be non-empty strings");
  invariant(Number.isInteger(workerIndex) && workerIndex >= 0, "worker index must be a non-negative integer");
  return tokens[workerIndex % tokens.length];
}

export async function runHttpLoadRound({
  baseUrl,
  bearerToken,
  bearerTokens,
  scenario,
  environment,
  load,
  resourceSampleIntervalMs = 1000,
  sampleResources,
}) {
  invariant(new URL(baseUrl).protocol.match(/^https?:$/), "baseUrl must use HTTP or HTTPS");
  invariant(Number.isInteger(load.concurrency) && load.concurrency > 0, "load concurrency must be positive");
  invariant(load.warmupSeconds > 0 && load.durationSeconds > 0, "warmup and duration must be positive");
  invariant(load.requestTimeoutMs > 0, "request timeout must be positive");
  const minimumRequestIntervalMs = load.minimumRequestIntervalMs ?? 0;
  invariant(Number.isFinite(minimumRequestIntervalMs) && minimumRequestIntervalMs >= 0,
    "minimum request interval must be non-negative");
  invariant(typeof sampleResources === "function", "resource sampler is required");

  const execute = (sequence, workerIndex) =>
    performRequest({
      baseUrl,
      bearerToken: selectBearerToken({ bearerToken, bearerTokens }, workerIndex),
      scenario,
      environment,
      sequence,
      requestTimeoutMs: load.requestTimeoutMs,
    });

  await runPhase({
    durationSeconds: load.warmupSeconds,
    concurrency: load.concurrency,
    minimumRequestIntervalMs,
    execute,
    collect: false,
  });

  let sampling = true;
  const resourceSamples = [];
  const sampler = (async () => {
    while (sampling) {
      const sample = await sampleResources();
      if (sample) {
        resourceSamples.push(sample);
      }
      await delay(resourceSampleIntervalMs);
    }
  })();
  const measured = await runPhase({
    durationSeconds: load.durationSeconds,
    concurrency: load.concurrency,
    minimumRequestIntervalMs,
    execute,
    collect: true,
  });
  sampling = false;
  await sampler;

  return {
    measuredDurationMs: measured.elapsedMs,
    requests: measured.samples,
    resourceSamples,
  };
}

function runCommand(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve(stdout);
      } else {
        reject(new Error(`${command} exited ${code}: ${stderr.trim() || "no stderr"}`));
      }
    });
  });
}

export async function assertExclusiveDockerContainers(containers) {
  invariant(Array.isArray(containers) && containers.length > 0, "at least one exclusive Docker container is required");
  const expected = [...new Set(containers)].sort();
  invariant(expected.length === containers.length, "exclusive Docker container list contains duplicates");
  const live = (await runCommand("docker", ["ps", "--no-trunc", "--format", "{{.ID}}"])).split(/\r?\n/).filter(Boolean).sort();
  invariant(
    live.length === expected.length && live.every((container, index) => container === expected[index]),
    `formal window is not exclusive: expected ${expected.length} benchmark containers, found ${live.length} running containers`,
  );
  return { expectedContainerCount: expected.length, observedContainerCount: live.length };
}

export async function sampleDockerResources(containers) {
  invariant(Array.isArray(containers) && containers.length > 0, "at least one Docker container is required");
  const output = await runCommand("docker", [
    "stats",
    "--no-stream",
    "--format",
    "{{json .}}",
    ...containers,
  ]);
  const samples = output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map(parseDockerStatsLine);
  invariant(samples.length === containers.length, "Docker stats did not return every configured container");
  return {
    atMs: Date.now(),
    cpuPercent: samples.reduce((sum, { cpuPercent }) => sum + cpuPercent, 0),
    memoryMiB: samples.reduce((sum, { memoryMiB }) => sum + memoryMiB, 0),
    containers: samples,
  };
}

function markdownCell(value) {
  return String(value ?? "").replaceAll("|", "\\|").replaceAll("\n", " ");
}

export function renderMarkdownReport(report) {
  const lines = [
    "# Issue #307 单体与三服务同条件性能对比",
    "",
    `- 生成时间：${report.generatedAt}`,
    `- 单体 SHA：\`${report.baselines.monolith.sha}\``,
    `- 三服务 SHA：\`${report.baselines.threeService.sha}\``,
    `- 机器指纹：\`${report.machineFingerprint}\``,
    `- 数据集 SHA-256：\`${report.datasetSha256}\``,
    "",
    "## 原始轮次指标",
    "",
    "| Architecture | Scenario | Round | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |",
    "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
  ];
  for (const item of report.rounds) {
    lines.push(
      `| ${markdownCell(item.architecture)} | ${markdownCell(item.scenario)} | ${item.round} | ${item.requestCount} | ${item.successfulRequestCount} | ${item.averageMs} | ${item.p95Ms} | ${item.throughputRequestsPerSecond} | ${item.successfulThroughputRequestsPerSecond} | ${item.errorRatePercent} | ${item.cpuAveragePercent} | ${item.cpuMaxPercent} | ${item.memoryAverageMiB} | ${item.memoryMaxMiB} |`,
    );
  }
  lines.push("", "## 全量聚合", "");
  lines.push(
    "| Architecture | Scenario | Rounds | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |",
    "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
  );
  for (const item of report.summary) {
    lines.push(
      `| ${markdownCell(item.architecture)} | ${markdownCell(item.scenario)} | ${item.roundCount} | ${item.requestCount} | ${item.successfulRequestCount} | ${item.averageMs} | ${item.p95Ms} | ${item.throughputRequestsPerSecond} | ${item.successfulThroughputRequestsPerSecond} | ${item.errorRatePercent} | ${item.cpuAveragePercent} | ${item.cpuMaxPercent} | ${item.memoryAverageMiB} | ${item.memoryMaxMiB} |`,
    );
  }
  lines.push("", "## 差异与解释边界", "");
  for (const item of report.comparisons) {
    const delta = item.threeServiceMinusMonolith;
    lines.push(
      `- ${item.scenario}：三服务相对单体 P95 差异 ${delta.p95Percent ?? "N/A"}%，总请求吞吐差异 ${delta.throughputPercent ?? "N/A"}%，成功请求吞吐差异 ${delta.successfulThroughputPercent ?? "N/A"}%，错误率差异 ${delta.errorRatePercentagePoints} 个百分点，CPU 平均差异 ${delta.cpuAveragePercentagePoints} 个百分点，内存平均差异 ${delta.memoryAverageMiB} MiB。`,
    );
  }
  for (const boundary of report.interpretationBoundary) {
    lines.push(`- ${boundary}`);
  }
  return `${lines.join("\n")}\n`;
}

function csvValue(value) {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function renderCsvReport(report) {
  const fields = [
    ["architecture", "architecture"],
    ["scenario", "scenario"],
    ["round", "round"],
    ["request_count", "requestCount"],
    ["average_ms", "averageMs"],
    ["p95_ms", "p95Ms"],
    ["throughput_requests_per_second", "throughputRequestsPerSecond"],
    ["successful_request_count", "successfulRequestCount"],
    ["successful_throughput_requests_per_second", "successfulThroughputRequestsPerSecond"],
    ["error_rate_percent", "errorRatePercent"],
    ["cpu_average_percent", "cpuAveragePercent"],
    ["cpu_max_percent", "cpuMaxPercent"],
    ["memory_average_mib", "memoryAverageMiB"],
    ["memory_max_mib", "memoryMaxMiB"],
  ];
  return `${[
    fields.map(([header]) => header).join(","),
    ...report.rounds.map((item) => fields.map(([, key]) => csvValue(item[key])).join(",")),
  ].join("\n")}\n`;
}
