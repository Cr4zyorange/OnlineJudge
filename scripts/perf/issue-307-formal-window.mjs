#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

import { validateFormalWindowEvidence } from "./issue-307-runner.mjs";

function usage() {
  return `Usage: scripts/perf/issue-307-formal-window.mjs \\
  --output FILE --architecture NAME --project NAME \\
  --environment-ready-signal TEXT --dataset-restore-evidence TEXT \\
  --resource-policy-evidence TEXT --expected-live-containers COUNT \\
  --observed-live-containers COUNT --docker-daemon-ready true|false\n`;
}

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function parseOptions(argumentsList) {
  if (argumentsList.length === 1 && ["--help", "-h"].includes(argumentsList[0])) return null;
  invariant(argumentsList.length % 2 === 0, "options must be --name value pairs");
  const options = {};
  for (let index = 0; index < argumentsList.length; index += 2) {
    const name = argumentsList[index];
    const value = argumentsList[index + 1];
    invariant(name.startsWith("--") && value, `invalid option near ${name ?? "end of input"}`);
    options[name.slice(2)] = value;
  }
  return options;
}

function required(options, name) {
  invariant(options[name], `--${name} is required`);
  return options[name];
}

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options === null) {
    process.stdout.write(usage());
    return;
  }
  const expected = Number(required(options, "expected-live-containers"));
  const observed = Number(required(options, "observed-live-containers"));
  invariant(Number.isInteger(expected) && expected > 0, "expected live container count must be positive");
  invariant(observed === expected, `exclusive window check failed: expected ${expected} live containers, observed ${observed}`);
  const daemonReady = required(options, "docker-daemon-ready") === "true";
  const evidence = {
    environmentReadySignal: required(options, "environment-ready-signal"),
    dockerDaemonReady: daemonReady,
    exclusiveWindow: true,
    hpaEnabled: false,
    e2eRunning: false,
    faultInjectionRunning: false,
    otherPressureRunning: false,
    datasetRestoreEvidence: required(options, "dataset-restore-evidence"),
    resourcePolicyEvidence: required(options, "resource-policy-evidence"),
    runtime: {
      architecture: required(options, "architecture"),
      project: required(options, "project"),
      expectedLiveContainers: expected,
      observedLiveContainers: observed,
    },
  };
  validateFormalWindowEvidence(evidence);
  const output = required(options, "output");
  await mkdir(path.dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  process.stdout.write(`FORMAL_WINDOW_RECORDED architecture=${evidence.runtime.architecture} project=${evidence.runtime.project} output=${output}\n`);
}

main().catch((error) => {
  process.stderr.write(`issue-307-formal-window: ${error.message}\n`);
  process.exitCode = 2;
});
