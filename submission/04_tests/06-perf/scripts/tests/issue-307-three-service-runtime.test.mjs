import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const script = path.join(repositoryRoot, "scripts/perf/issue-307-three-service-runtime.sh");

test("three-service runtime launcher documents isolated frozen-SHA execution", () => {
  const result = spawnSync("bash", [script, "--help"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /--runtime-repo DIR/);
  assert.match(result.stdout, /--git-sha SHA/);
  assert.match(result.stdout, /--project oj307-NAME/);
});

test("three-service runtime launcher fails closed on a non-frozen checkout or project", async () => {
  const source = await readFile(script, "utf8");
  assert.match(source, /runtime checkout SHA/);
  assert.match(source, /\[\[ "\$project" == oj307-\* \]\]/);
  assert.match(source, /render_disposable_environment\.py/);
  assert.match(source, /output_dir="\$\(CDPATH= cd -- "\$output_dir" && pwd\)"/);
  assert.match(source, /issue-307-resource-policy\.mjs/);
  assert.match(source, /generate_service_identity_jwt\.mjs/);
  assert.match(source, /GRADE_SERVICE_IDENTITY="Bearer %s"/);
  assert.match(source, /"\$grade_service_token"/);
  assert.doesNotMatch(source, /GRADE_SERVICE_IDENTITY=issue307-grade/);
});
