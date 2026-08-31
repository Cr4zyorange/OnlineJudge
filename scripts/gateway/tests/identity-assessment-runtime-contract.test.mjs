import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const script = readFileSync(
  fileURLToPath(new URL("./identity-assessment-runtime.test.sh", import.meta.url)),
  "utf8",
);

for (const required of [
  "IDENTITY_BASE",
  "ASSESSMENT_BASE",
  "GATEWAY_BASE",
  "TEST_USERNAME",
  "TEST_PASSWORD_FILE",
  "IDENTITY_CONTAINER",
]) {
  assert.match(script, new RegExp(`\\$\\{${required}:\\?${required} is required\\}`));
}

assert.match(script, /docker info/);
assert.match(script, /exit 69/);
assert.match(script, /stat -c '%a' "\$TEST_PASSWORD_FILE"/);
assert.match(script, /chmod 600 "\$authorization_header"/);
assert.match(script, /trap restore_identity EXIT INT TERM/);
assert.match(script, /docker stop "\$IDENTITY_CONTAINER"/);
assert.match(script, /docker start "\$IDENTITY_CONTAINER"/);
assert.match(script, /\/api\/v1\/auth\/login/);
assert.match(script, /\/api\/v1\/evaluations\/gateway-probe-missing/);
assert.doesNotMatch(script, /(?:printf|echo)[^\n]*\$(?:TEST_PASSWORD|password|token)/i);

console.log("identity-assessment-runtime-contract.test: PASS");
