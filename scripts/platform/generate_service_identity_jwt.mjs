#!/usr/bin/env node

import { createPrivateKey, randomUUID, sign } from "node:crypto";
import process from "node:process";

function requiredEnvironment(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function base64url(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function serviceScopes() {
  const scopes = requiredEnvironment("SERVICE_IDENTITY_SCOPES")
    .split(",")
    .map((scope) => scope.trim())
    .filter(Boolean);
  if (scopes.length === 0) throw new Error("SERVICE_IDENTITY_SCOPES must contain at least one scope");
  return [...new Set(scopes)];
}

function expirationSeconds() {
  const raw = process.env.SERVICE_IDENTITY_TTL_SECONDS ?? "7200";
  const ttl = Number(raw);
  if (!Number.isInteger(ttl) || ttl < 60 || ttl > 86400) {
    throw new Error("SERVICE_IDENTITY_TTL_SECONDS must be an integer from 60 through 86400");
  }
  return ttl;
}

function generateToken() {
  const privateKey = createPrivateKey({
    key: Buffer.from(requiredEnvironment("IDENTITY_JWT_SIGNING_KEY"), "base64"),
    format: "der",
    type: "pkcs8",
  });
  if (privateKey.asymmetricKeyType !== "rsa") {
    throw new Error("IDENTITY_JWT_SIGNING_KEY must be an RSA PKCS#8 key");
  }
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", kid: requiredEnvironment("IDENTITY_JWT_KID"), typ: "JWT" };
  const claims = {
    iss: "onlinejudge.identity.v2",
    aud: requiredEnvironment("SERVICE_IDENTITY_AUDIENCE"),
    sub: requiredEnvironment("SERVICE_IDENTITY_SUBJECT"),
    scopes: serviceScopes(),
    iat: now,
    exp: now + expirationSeconds(),
    jti: randomUUID(),
  };
  const signingInput = `${base64url(header)}.${base64url(claims)}`;
  return `${signingInput}.${sign("RSA-SHA256", Buffer.from(signingInput), privateKey).toString("base64url")}`;
}

try {
  process.stdout.write(`${generateToken()}\n`);
} catch (error) {
  process.stderr.write(`generate-service-identity-jwt: ${error.message}\n`);
  process.exitCode = 2;
}
