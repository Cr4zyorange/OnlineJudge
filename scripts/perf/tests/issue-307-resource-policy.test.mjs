import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { promisify } from "node:util";

import { renderComposeOverride, validateResourcePolicy } from "../issue-307-resource-policy.mjs";

const policy = JSON.parse(await readFile("performance/issue-307/resource-policy.json", "utf8"));
const execFileAsync = promisify(execFile);

test("both architectures have the same exact 4 CPU and 6144 MiB budget", () => {
  const validated = validateResourcePolicy(policy);
  for (const architecture of ["monolith", "three-service"]) {
    assert.deepEqual(validated.totals[architecture], { cpuCores: 4, memoryMiB: 6144 });
  }
});

test("Compose overrides contain every measured container and explicit hard limits", () => {
  for (const architecture of ["monolith", "three-service"]) {
    const yaml = renderComposeOverride(policy, architecture);
    for (const service of Object.keys(policy.architectures[architecture])) {
      assert.match(yaml, new RegExp(`  ${service}:\\n`));
    }
    assert.match(yaml, /cpus: "/);
    assert.match(yaml, architecture === "three-service" ? /memory: "/ : /mem_limit: "/);
  }
});

test("three-service benchmark overlay enables only the authentication seed fixture", () => {
  const yaml = renderComposeOverride(policy, "three-service");
  assert.match(
    yaml,
    /identity-service:[\s\S]*?environment:\n      IDENTITY_SEED_DATA_ENABLED: "true"/,
  );
});

test("three-service overlay replaces manifest deploy limits instead of conflicting with them", () => {
  const yaml = renderComposeOverride(policy, "three-service");
  assert.match(
    yaml,
    /mysql:\n    deploy:\n      resources:\n        reservations:\n          cpus: "0\.75"\n          memory: "1536m"\n        limits:\n          cpus: "0\.75"\n          memory: "1536m"/,
  );
  assert.doesNotMatch(yaml, /mysql:\n    cpus:/);
});

test("resource-policy CLI emits an override when invoked through the macOS /tmp alias", async (t) => {
  const script = fileURLToPath(new URL("../issue-307-resource-policy.mjs", import.meta.url));
  const tmpAlias = script.replace(/^\/private\/tmp\//, "/tmp/");
  if (tmpAlias === script) {
    t.skip("this checkout is not under the macOS /private/tmp alias");
    return;
  }
  const { stdout } = await execFileAsync(process.execPath, [tmpAlias, "--architecture", "monolith"]);
  assert.match(stdout, /cpus: "2.5"/);
});
