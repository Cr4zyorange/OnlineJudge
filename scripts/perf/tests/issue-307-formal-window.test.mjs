import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("formal-window writer only accepts explicit observed-window evidence", () => {
  const result = spawnSync(process.execPath, ["scripts/perf/issue-307-formal-window.mjs", "--help"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /--dataset-restore-evidence TEXT/);
  assert.match(result.stdout, /--resource-policy-evidence TEXT/);
  assert.match(result.stdout, /--observed-live-containers COUNT/);
});
