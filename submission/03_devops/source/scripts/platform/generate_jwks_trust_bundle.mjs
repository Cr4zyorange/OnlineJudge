#!/usr/bin/env node

import { createPrivateKey, createPublicKey } from "node:crypto";
import process from "node:process";

function requiredEnvironment(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function generateBundle() {
  const privateKey = createPrivateKey({
    key: Buffer.from(requiredEnvironment("IDENTITY_JWT_SIGNING_KEY"), "base64"),
    format: "der",
    type: "pkcs8",
  });
  if (privateKey.asymmetricKeyType !== "rsa") {
    throw new Error("IDENTITY_JWT_SIGNING_KEY must be an RSA PKCS#8 key");
  }
  const publicJwk = createPublicKey(privateKey).export({ format: "jwk" });
  if (!publicJwk.n || !publicJwk.e) {
    throw new Error("could not derive an RSA public key");
  }
  return {
    keys: [{
      kty: "RSA",
      use: "sig",
      alg: "RS256",
      kid: requiredEnvironment("IDENTITY_JWT_KID"),
      n: publicJwk.n,
      e: publicJwk.e,
    }],
  };
}

try {
  process.stdout.write(`${JSON.stringify(generateBundle())}\n`);
} catch (error) {
  process.stderr.write(`generate-jwks-trust-bundle: ${error.message}\n`);
  process.exitCode = 2;
}
