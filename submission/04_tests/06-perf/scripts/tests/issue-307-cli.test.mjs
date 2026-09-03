import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

function run(...args) {
  return spawnSync(process.execPath, ["scripts/perf/issue-307.mjs", ...args], {
    cwd: repositoryRoot,
    encoding: "utf8",
    windowsHide: true,
  });
}

test("CLI validates the frozen #307 plan", () => {
  const result = run("validate-plan", "--plan", "performance/issue-307/plan.json");
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /PLAN_VALID issue=#307 scenarios=3 rounds=3/);
});

test("CLI refuses formal counting with the intentionally blocked evidence template", () => {
  const result = run(
    "validate-window",
    "--evidence",
    "performance/issue-307/formal-window.template.json",
  );
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /ENVIRONMENT_READY/);
});

test("CLI emits a stable machine fingerprint without requiring Docker", () => {
  const result = run("machine");
  assert.equal(result.status, 0, result.stderr);
  const output = JSON.parse(result.stdout);
  assert.match(output.sha256, /^[0-9a-f]{64}$/);
  assert.ok(output.descriptor.cpuCount > 0);
  assert.ok(output.descriptor.totalMemoryBytes > 0);
});
