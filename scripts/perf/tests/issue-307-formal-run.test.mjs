import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
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
});
