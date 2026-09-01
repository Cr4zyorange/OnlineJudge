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

async function runPhase({ durationSeconds, concurrency, execute, collect }) {
  const started = performance.now();
  const deadline = started + durationSeconds * 1000;
  let sequence = 0;
  const samples = [];
  async function worker() {
    while (performance.now() < deadline) {
      const current = sequence;
      sequence += 1;
      const sample = await execute(current);
      if (collect) {
        samples.push(sample);
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return { samples, elapsedMs: performance.now() - started };
}

export async function runHttpLoadRound({
  baseUrl,
  bearerToken,
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
  invariant(typeof sampleResources === "function", "resource sampler is required");

  const execute = (sequence) =>
    performRequest({
      baseUrl,
      bearerToken,
      scenario,
      environment,
      sequence,
      requestTimeoutMs: load.requestTimeoutMs,
    });

  await runPhase({ durationSeconds: load.warmupSeconds, concurrency: load.concurrency, execute, collect: false });

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
    "| Architecture | Scenario | Round | Requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |",
    "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
  ];
  for (const item of report.rounds) {
    lines.push(
      `| ${markdownCell(item.architecture)} | ${markdownCell(item.scenario)} | ${item.round} | ${item.requestCount} | ${item.averageMs} | ${item.p95Ms} | ${item.throughputRequestsPerSecond} | ${item.errorRatePercent} | ${item.cpuAveragePercent} | ${item.cpuMaxPercent} | ${item.memoryAverageMiB} | ${item.memoryMaxMiB} |`,
    );
  }
  lines.push("", "## 全量聚合", "");
  lines.push(
    "| Architecture | Scenario | Rounds | Requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |",
    "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
  );
  for (const item of report.summary) {
    lines.push(
      `| ${markdownCell(item.architecture)} | ${markdownCell(item.scenario)} | ${item.roundCount} | ${item.requestCount} | ${item.averageMs} | ${item.p95Ms} | ${item.throughputRequestsPerSecond} | ${item.errorRatePercent} | ${item.cpuAveragePercent} | ${item.cpuMaxPercent} | ${item.memoryAverageMiB} | ${item.memoryMaxMiB} |`,
    );
  }
  lines.push("", "## 差异与解释边界", "");
  for (const item of report.comparisons) {
    const delta = item.threeServiceMinusMonolith;
    lines.push(
      `- ${item.scenario}：三服务相对单体 P95 差异 ${delta.p95Percent ?? "N/A"}%，吞吐差异 ${delta.throughputPercent ?? "N/A"}%，错误率差异 ${delta.errorRatePercentagePoints} 个百分点，CPU 平均差异 ${delta.cpuAveragePercentagePoints} 个百分点，内存平均差异 ${delta.memoryAverageMiB} MiB。`,
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
