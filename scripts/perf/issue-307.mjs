#!/usr/bin/env node

import { createHash } from "node:crypto";
import { realpathSync } from "node:fs";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import process from "node:process";
import { gunzipSync } from "node:zlib";

import { aggregateComparison, machineFingerprint, validatePlan } from "./issue-307-lib.mjs";
import {
  renderCsvReport,
  renderMarkdownReport,
  runHttpLoadRound,
  sampleDockerResources,
  summarizePreflight,
  validateFormalWindowEvidence,
} from "./issue-307-runner.mjs";

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function parseOptions(argumentsList) {
  const [command, ...rest] = argumentsList;
  const options = {};
  for (let index = 0; index < rest.length; index += 1) {
    const item = rest[index];
    invariant(item.startsWith("--"), `unexpected argument: ${item}`);
    const key = item.slice(2);
    invariant(key.length > 0, "empty option name");
    invariant(index + 1 < rest.length && !rest[index + 1].startsWith("--"), `missing value for --${key}`);
    options[key] = rest[index + 1];
    index += 1;
  }
  return { command, options };
}

export async function readJson(file) {
  const bytes = await readFile(file);
  return JSON.parse(file.endsWith(".gz") ? gunzipSync(bytes).toString("utf8") : bytes.toString("utf8"));
}

async function sha256File(file) {
  return createHash("sha256").update(await readFile(file)).digest("hex");
}

function requiredOption(options, name) {
  invariant(options[name], `--${name} is required`);
  return options[name];
}

function requiredEnvironment(name) {
  const value = process.env[name];
  invariant(value, `environment variable ${name} is required`);
  return value;
}

function requiredBearerTokens(name) {
  const raw = requiredEnvironment(name);
  try {
    const tokens = JSON.parse(raw);
    invariant(Array.isArray(tokens) && tokens.length > 0, `${name} must be a non-empty JSON array`);
    invariant(tokens.every((token) => typeof token === "string" && token.length > 0), `${name} must contain non-empty strings`);
    return tokens;
  } catch (error) {
    if (error instanceof SyntaxError) {
      return [raw];
    }
    throw error;
  }
}

async function loadAndValidatePlan(file) {
  const plan = validatePlan(await readJson(file));
  invariant(plan.environment.datasetFile, "plan environment.datasetFile is required");
  const actualDatasetSha = await sha256File(plan.environment.datasetFile);
  invariant(
    actualDatasetSha === plan.environment.datasetSha256,
    `dataset checksum mismatch: expected ${plan.environment.datasetSha256}, got ${actualDatasetSha}`,
  );
  return plan;
}

async function listJsonFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listJsonFiles(target)));
    } else if (entry.isFile() && (entry.name.endsWith(".json") || entry.name.endsWith(".json.gz"))) {
      files.push(target);
    }
  }
  return files.sort();
}

async function validatePlanCommand(options) {
  const plan = await loadAndValidatePlan(requiredOption(options, "plan"));
  process.stdout.write(
    `PLAN_VALID issue=#${plan.issue} scenarios=${plan.scenarios.length} rounds=${plan.load.rounds} monolith=${plan.baselines.monolith.sha} three-service=${plan.baselines.threeService.sha}\n`,
  );
}

async function validateWindowCommand(options) {
  const evidence = validateFormalWindowEvidence(await readJson(requiredOption(options, "evidence")));
  process.stdout.write(`WINDOW_VALID signal=${evidence.environmentReadySignal}\n`);
}

async function validatePreflightCommand(options) {
  const summary = summarizePreflight(await readJson(requiredOption(options, "evidence")));
  process.stdout.write(
    `PREFLIGHT_VALID scenario=${summary.scenario} requests=${summary.requestCount} ` +
    `successful=${summary.successfulRequestCount} success-rate=${summary.successRatePercent}%\n`,
  );
}

async function runCommand(options) {
  const plan = await loadAndValidatePlan(requiredOption(options, "plan"));
  const architecture = plan.architectures.find(({ id }) => id === requiredOption(options, "architecture"));
  invariant(architecture, `unknown architecture: ${options.architecture}`);
  const scenario = plan.scenarios.find(({ id }) => id === requiredOption(options, "scenario"));
  invariant(scenario, `unknown scenario: ${options.scenario}`);
  const runNumber = Number(requiredOption(options, "round"));
  invariant(
    Number.isInteger(runNumber) && runNumber >= 1 && runNumber <= plan.load.rounds,
    `round must be between 1 and ${plan.load.rounds}`,
  );
  const formalWindow = validateFormalWindowEvidence(
    await readJson(requiredOption(options, "formal-window")),
  );
  invariant(
    typeof formalWindow.datasetRestoreEvidence === "string" && formalWindow.datasetRestoreEvidence.length > 0,
    "formal window must record datasetRestoreEvidence for the current round",
  );
  invariant(
    typeof formalWindow.resourcePolicyEvidence === "string" && formalWindow.resourcePolicyEvidence.length > 0,
    "formal window must record resourcePolicyEvidence",
  );
  const preflight = summarizePreflight(await readJson(requiredOption(options, "preflight-evidence")));
  invariant(preflight.scenario === scenario.id, "preflight scenario must match the measured scenario");
  invariant(
    preflight.expectedStatuses.length === scenario.expectedStatuses.length &&
      preflight.expectedStatuses.every((status) => scenario.expectedStatuses.includes(status)),
    "preflight expected statuses must match the measured scenario contract",
  );
  invariant(
    preflight.minimumSuccessRatePercent === plan.preflight.minimumSuccessRatePercent,
    "preflight success-rate gate must match the frozen plan",
  );
  invariant(
    preflight.requestCount === plan.load.concurrency * plan.preflight.requestsPerVirtualStudent,
    "preflight must cover every virtual student and configured attempt",
  );

  const containers = requiredEnvironment(architecture.containersEnv)
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  invariant(containers.length > 0, `${architecture.containersEnv} must contain at least one container`);
  const fingerprint = machineFingerprint();
  const startedAt = new Date().toISOString();
  const measured = await runHttpLoadRound({
    baseUrl: requiredEnvironment(architecture.baseUrlEnv),
    bearerTokens: requiredBearerTokens(architecture.bearerTokenEnv),
    scenario,
    environment: process.env,
    load: plan.load,
    resourceSampleIntervalMs: plan.environment.resourceSampleIntervalMs,
    sampleResources: () => sampleDockerResources(containers),
  });
  const baselineSha = plan.baselines[architecture.baseline].sha;
  const raw = {
    schemaVersion: 1,
    issue: 307,
    architecture: architecture.id,
    scenario: scenario.id,
    round: runNumber,
    baselineSha,
    machineFingerprint: fingerprint.sha256,
    machine: fingerprint.descriptor,
    datasetSha256: plan.environment.datasetSha256,
    resourceBudget: plan.environment.resourceBudget,
    load: {
      warmupSeconds: plan.load.warmupSeconds,
      durationSeconds: plan.load.durationSeconds,
      concurrency: plan.load.concurrency,
      minimumRequestIntervalMs: plan.load.minimumRequestIntervalMs,
      requestTimeoutMs: plan.load.requestTimeoutMs,
    },
    formalWindow,
    preflight,
    startedAt,
    completedAt: new Date().toISOString(),
    ...measured,
  };
  const output = requiredOption(options, "output");
  await mkdir(path.dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(raw, null, 2)}\n`, "utf8");
  process.stdout.write(
    `ROUND_RECORDED architecture=${architecture.id} scenario=${scenario.id} round=${runNumber} requests=${raw.requests.length} output=${output}\n`,
  );
}

async function aggregateCommand(options) {
  const plan = await loadAndValidatePlan(requiredOption(options, "plan"));
  const rawFiles = await listJsonFiles(requiredOption(options, "raw-dir"));
  const raw = [];
  for (const file of rawFiles) {
    const value = await readJson(file);
    if (value.issue === 307 && value.architecture && value.scenario && value.round) {
      raw.push(value);
    }
  }
  const report = aggregateComparison(plan, raw);
  const outputDirectory = requiredOption(options, "output-dir");
  await mkdir(outputDirectory, { recursive: true });
  await Promise.all([
    writeFile(path.join(outputDirectory, "comparison.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8"),
    writeFile(path.join(outputDirectory, "comparison.md"), renderMarkdownReport(report), "utf8"),
    writeFile(path.join(outputDirectory, "rounds.csv"), renderCsvReport(report), "utf8"),
  ]);
  process.stdout.write(
    `COMPARISON_READY raw=${raw.length} summaries=${report.summary.length} output=${outputDirectory}\n`,
  );
}

async function main() {
  const { command, options } = parseOptions(process.argv.slice(2));
  switch (command) {
    case "validate-plan":
      await validatePlanCommand(options);
      break;
    case "validate-window":
      await validateWindowCommand(options);
      break;
    case "validate-preflight":
      await validatePreflightCommand(options);
      break;
    case "machine":
      process.stdout.write(`${JSON.stringify(machineFingerprint(), null, 2)}\n`);
      break;
    case "run":
      await runCommand(options);
      break;
    case "aggregate":
      await aggregateCommand(options);
      break;
    default:
      throw new Error(
        "usage: issue-307.mjs <validate-plan|validate-window|validate-preflight|machine|run|aggregate> [options]",
      );
  }
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
  main().catch((error) => {
    process.stderr.write(`ERROR: ${error.message}\n`);
    process.exitCode = 1;
  });
}
