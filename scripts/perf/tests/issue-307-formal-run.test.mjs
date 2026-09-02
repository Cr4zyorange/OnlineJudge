import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("formal runner documents per-round reset and exclusive Docker-window checks", () => {
  const result = spawnSync("bash", ["scripts/perf/issue-307-formal-run.sh", "--help"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /--mysql-container NAME/);
  assert.match(result.stdout, /--expected-live-containers COUNT/);
  assert.match(result.stdout, /--from-scenario/);

  const source = readFileSync(path.join(repositoryRoot, "scripts/perf/issue-307-formal-run.sh"), "utf8");
  assert.match(source, /benchmark_tokens/);
  assert.match(source, /perf307_student%03d/);
  assert.match(source, /run_preflight/);
  assert.match(source, /minimumSuccessRatePercent/);
  assert.match(source, /preflight_minimum_success_rate/);
  assert.match(source, /expected_statuses=200,201,202/);
  assert.match(source, /validate-preflight/);
  assert.match(source, /preflight-evidence/);
  assert.match(source, /responses\/student-/);
  assert.match(source, /preflight-reset/);
  assert.match(source, /readiness_path/);
  assert.match(source, /\/api\/v1\/system\/health/);
  assert.match(source, /\/health\/ready/);
  assert.match(source, /X-Request-Id/);
  assert.match(source, /randomUUID/);
  assert.match(source, /from_scenario/);
  assert.match(source, /scenario_list/);
  const initialDatasetReset = source.indexOf("initial-login-reset.log");
  const benchmarkTokenLogin = source.indexOf("benchmark_tokens=()");
  assert.ok(initialDatasetReset >= 0, "a fresh runtime must be seeded before benchmark login");
  assert.ok(initialDatasetReset < benchmarkTokenLogin, "dataset seeding must precede benchmark login");
});
