#!/usr/bin/env node
/**
 * Issue #367 regression tests for the inventory/mapping tooling.
 *
 * Guards the two previously reported false positives:
 *  1. Cross-service shared paths (GET /health/ready on assessment, grade and
 *     gateway) must remain distinct inventory + mapping entries.
 *  2. A mapping may only cite a test that actually executes the endpoint:
 *     gateway endpoints are mapped solely from requests the runtime smoke
 *     really sends, and that script must be invoked by the issue runner.
 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const apiDir = dirname(fileURLToPath(import.meta.url));
const root = join(apiDir, "..", "..");
const inventory = JSON.parse(readFileSync(join(apiDir, "inventory.json"), "utf8")).endpoints;
const { mapping } = JSON.parse(readFileSync(join(apiDir, "mapping.json"), "utf8"));
const coverage = JSON.parse(readFileSync(join(apiDir, "coverage-report.json"), "utf8"));
const runtimeSource = readFileSync(join(root, "scripts/gateway/tests/gateway-runtime.test.sh"), "utf8");
const configSource = readFileSync(join(root, "scripts/gateway/tests/kind-gateway-config.test.sh"), "utf8");
const runnerSource = readFileSync(join(root, "scripts/test/run-api-coverage-367.sh"), "utf8");

// 1. Service-dimension keys keep shared paths distinct.
const keyOf = (entry) => `${entry.service}|${entry.method} ${entry.path}`;
const keys = new Set();
for (const entry of inventory) {
  assert.ok(!keys.has(keyOf(entry)), `duplicate inventory key: ${keyOf(entry)}`);
  keys.add(keyOf(entry));
}
for (const service of ["assessment", "grade", "gateway"]) {
  assert.ok(
    inventory.some((entry) => entry.service === service && entry.method === "GET" && entry.path === "/health/ready"),
    `${service} GET /health/ready was merged away`,
  );
}
const byService = {};
for (const entry of inventory) {
  byService[entry.service] = (byService[entry.service] || 0) + 1;
}
assert.equal(byService.grade, 22, "grade must keep all 22 controller+readiness routes");
assert.equal(byService.gateway, 10, "gateway must keep all 10 owned endpoints");
assert.equal(byService.assessment, 27);
assert.equal(byService.course, 42);
assert.equal(byService.identity, 23);

// 2. Every inventory entry maps under its own service-qualified key.
assert.equal(coverage.totals.unmapped, 0, "unmapped endpoints must be zero");
for (const entry of inventory) {
  const key = keyOf(entry);
  assert.ok(mapping[key] && mapping[key].length > 0, `no executed test mapped for ${key}`);
}

// 3. Gateway mappings must come from requests the runtime smoke really sends,
//    and the runtime script must itself be executed by the issue runner.
const gatewayEntries = inventory.filter((entry) => entry.service === "gateway");
const mappedRuntimeLines = [];
for (const entry of gatewayEntries) {
  const key = keyOf(entry);
  const tests = mapping[key] || [];
  assert.ok(tests.length > 0, `gateway endpoint has no executed test: ${key}`);
  for (const test of tests) {
    assert.equal(
      test.file,
      "scripts/gateway/tests/gateway-runtime.test.sh",
      `gateway endpoint ${key} mapped to a file that does not execute it`,
    );
  }
}
assert.ok(
  runnerSource.includes("scripts/gateway/tests/gateway-runtime.test.sh"),
  "runner must execute the gateway runtime smoke",
);

// 4. The static kind-config script performs no HTTP requests, so it must never
//    be cited as an executed API test for any endpoint.
for (const tests of Object.values(mapping)) {
  for (const test of tests) {
    assert.notEqual(
      test.file,
      "scripts/gateway/tests/kind-gateway-config.test.sh",
      "kind-gateway-config.test.sh performs no HTTP request and cannot map an API test",
    );
  }
}

// 5. Shared-path regression examples from the previously reported issue.
assert.ok(mapping["assessment|GET /health/ready"], "assessment readiness mapping missing");
assert.ok(mapping["grade|GET /health/ready"], "grade readiness mapping missing");
assert.ok(mapping["gateway|GET /health/ready"], "gateway readiness mapping missing");
assert.ok(mapping["gateway|GET /health/startup"], "gateway startup probe is not actually requested");
assert.ok(mapping["gateway|GET /health/live"], "gateway live probe is not actually requested");

// 6. Evidence anchors for error pages must exist in the runtime script.
const evidenceAnchors = [
  "request GET /internal/v2/source-grades 404",
  "request GET /api/v1/unknown 404",
  "request POST /api/v1/auth/oversize 413",
  "request POST /api/v1/homeworks/unavailable 502",
  "request GET /api/v1/grades/controlled-unavailable 503",
  "request GET /api/v1/notifications/slow 504",
  "request GET /health/startup 200",
  "request GET /health/ready 200",
];
for (const anchor of evidenceAnchors) {
  assert.ok(runtimeSource.includes(anchor), `gateway runtime test lacks executed request: ${anchor}`);
}
assert.ok(configSource.length > 0, "kind-gateway-config.test.sh must exist");

console.log(
  `api-coverage.test: PASS (${inventory.length} endpoints, ${gatewayEntries.length} gateway-owned, shared paths distinct)`,
);
