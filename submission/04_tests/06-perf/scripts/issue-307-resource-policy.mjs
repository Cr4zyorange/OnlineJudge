#!/usr/bin/env node

import process from "node:process";
import { realpathSync } from "node:fs";
import { fileURLToPath } from "node:url";

const REQUIRED_ARCHITECTURES = ["monolith", "three-service"];
const EXPECTED_BUDGET = { cpuCores: 4, memoryMiB: 6144 };

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function formatCpu(cpuCores) {
  return Number.isInteger(cpuCores) ? String(cpuCores) : String(cpuCores);
}

function formatMemory(memoryMiB) {
  return `${memoryMiB}m`;
}

function total(services) {
  return Object.values(services).reduce(
    (sum, limit) => ({
      cpuCores: sum.cpuCores + limit.cpuCores,
      memoryMiB: sum.memoryMiB + limit.memoryMiB,
    }),
    { cpuCores: 0, memoryMiB: 0 },
  );
}

function sameBudget(left, right) {
  return left.cpuCores === right.cpuCores && left.memoryMiB === right.memoryMiB;
}

/**
 * Fail closed on a policy that would make the two formal benchmark windows
 * incomparable. The values are Docker hard limits, not scheduler requests.
 */
export function validateResourcePolicy(policy) {
  invariant(policy && typeof policy === "object", "resource policy must be an object");
  invariant(policy.schemaVersion === 1, "resource policy schemaVersion must be 1");
  invariant(policy.issue === 307, "resource policy must belong to issue #307");
  invariant(policy.architectures && typeof policy.architectures === "object", "resource policy architectures are required");
  invariant(
    sameBudget(policy.totalBudget, EXPECTED_BUDGET),
    "resource policy totalBudget must be exactly 4 CPU and 6144 MiB",
  );

  const totals = {};
  for (const architecture of REQUIRED_ARCHITECTURES) {
    const services = policy.architectures[architecture];
    invariant(services && typeof services === "object", `${architecture} services are required`);
    const entries = Object.entries(services);
    invariant(entries.length > 0, `${architecture} must limit at least one service`);
    for (const [service, limit] of entries) {
      invariant(/^[a-z][a-z0-9-]*$/.test(service), `${architecture} service name is invalid: ${service}`);
      invariant(
        Number.isFinite(limit?.cpuCores) && limit.cpuCores > 0,
        `${architecture}/${service} cpuCores must be positive`,
      );
      invariant(
        Number.isInteger(limit?.memoryMiB) && limit.memoryMiB > 0,
        `${architecture}/${service} memoryMiB must be a positive integer`,
      );
    }
    totals[architecture] = total(services);
    invariant(
      sameBudget(totals[architecture], EXPECTED_BUDGET),
      `${architecture} must total exactly 4 CPU and 6144 MiB`,
    );
  }
  return { policy, totals };
}

/** Render a Compose override that uses explicit Docker hard limits. */
export function renderComposeOverride(policy, architecture) {
  const { policy: validated } = validateResourcePolicy(policy);
  invariant(REQUIRED_ARCHITECTURES.includes(architecture), "architecture must be monolith or three-service");
  const lines = [
    "# Generated from performance/issue-307/resource-policy.json; do not edit.",
    "services:",
  ];
  for (const [service, limit] of Object.entries(validated.architectures[architecture])) {
    lines.push(`  ${service}:`);
    if (architecture === "three-service") {
      lines.push("    deploy:");
      lines.push("      resources:");
      lines.push("        reservations:");
      lines.push(`          cpus: \"${formatCpu(limit.cpuCores)}\"`);
      lines.push(`          memory: \"${formatMemory(limit.memoryMiB)}\"`);
      lines.push("        limits:");
      lines.push(`          cpus: \"${formatCpu(limit.cpuCores)}\"`);
      lines.push(`          memory: \"${formatMemory(limit.memoryMiB)}\"`);
    } else {
      lines.push(`    cpus: \"${formatCpu(limit.cpuCores)}\"`);
      lines.push(`    mem_limit: \"${formatMemory(limit.memoryMiB)}\"`);
    }
    if (architecture === "three-service" && service === "identity-service") {
      lines.push("    environment:");
      lines.push('      IDENTITY_SEED_DATA_ENABLED: "true"');
    }
  }
  return `${lines.join("\n")}\n`;
}

function parseArgs(argv) {
  invariant(argv.length === 2 && argv[0] === "--architecture", "usage: --architecture monolith|three-service");
  return argv[1];
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
  try {
    const architecture = parseArgs(process.argv.slice(2));
    const policy = JSON.parse(await import("node:fs/promises").then(({ readFile }) => readFile(
      new URL("../../performance/issue-307/resource-policy.json", import.meta.url),
      "utf8",
    )));
    process.stdout.write(renderComposeOverride(policy, architecture));
  } catch (error) {
    process.stderr.write(`issue-307-resource-policy: ${error.message}\n`);
    process.exitCode = 2;
  }
}
