import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const configUrl = new URL("../../../deploy/gateway/proxy-request-headers.conf", import.meta.url);
const config = readFileSync(fileURLToPath(configUrl), "utf8");

assert.match(config, /proxy_pass_request_headers off;/);

const allowed = [...config.matchAll(/proxy_set_header\s+([^\s]+)\s+/g)]
  .map((match) => match[1]);
assert.deepEqual(allowed.sort(), [
  "Accept",
  "Accept-Language",
  "Authorization",
  "Content-Encoding",
  "Content-Length",
  "Content-Type",
  "Host",
  "Idempotency-Key",
  "If-Modified-Since",
  "If-None-Match",
  "If-Range",
  "Range",
  "User-Agent",
  "X-Forwarded-For",
  "X-Forwarded-Proto",
  "X-Real-IP",
  "X-Request-Id",
].sort());

for (const forbidden of [
  "X-User-Id",
  "X-User-Future-Claim",
  "X-Internal-Token",
  "X-OnlineJudge-Service-Authorization",
  "Connection",
  "Upgrade",
  "TE",
  "Trailer",
  "X-Smuggled-Identity",
]) {
  assert.equal(
    allowed.some((name) => name.toLowerCase() === forbidden.toLowerCase()),
    false,
    `${forbidden} must not be forwarded`,
  );
}

console.log("request-boundary.test: PASS");
