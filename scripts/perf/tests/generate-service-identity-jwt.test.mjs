import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import path from "node:path";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const generator = path.join(repositoryRoot, "scripts/platform/generate_service_identity_jwt.mjs");

function decode(part) {
  return JSON.parse(Buffer.from(part, "base64url"));
}

test("service identity generator emits a signed course-scoped JWT", () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const result = spawnSync(process.execPath, [generator], {
    cwd: repositoryRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      IDENTITY_JWT_SIGNING_KEY: privateKey.export({ format: "der", type: "pkcs8" }).toString("base64"),
      IDENTITY_JWT_KID: "issue307-test",
      SERVICE_IDENTITY_SUBJECT: "grade-service",
      SERVICE_IDENTITY_AUDIENCE: "course",
      SERVICE_IDENTITY_SCOPES: "course.authorizations.read,course.members.read",
    },
  });
  assert.equal(result.status, 0, result.stderr);
  const token = result.stdout.trim();
  const [headerPart, payloadPart, signaturePart] = token.split(".");
  const header = decode(headerPart);
  const claims = decode(payloadPart);
  assert.deepEqual(header, { alg: "RS256", kid: "issue307-test", typ: "JWT" });
  assert.equal(claims.iss, "onlinejudge.identity.v2");
  assert.equal(claims.aud, "course");
  assert.equal(claims.sub, "grade-service");
  assert.deepEqual(claims.scopes, ["course.authorizations.read", "course.members.read"]);
  assert.ok(claims.exp > claims.iat);
  assert.equal(
    verify("RSA-SHA256", Buffer.from(`${headerPart}.${payloadPart}`), publicKey, Buffer.from(signaturePart, "base64url")),
    true,
  );
});
