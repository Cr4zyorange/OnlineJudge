#!/usr/bin/env bash
set -euo pipefail

# Dev-only renewable workload identity for the supported Compose deployment.
# The script is idempotent: re-running it after removing the volume (or the
# ready marker) rotates every credential, which is how an operator renews the
# Course -> Learning mTLS path without changing any service configuration.
out="${1:-/tls}"
storepass="changeit"
keytool="$(command -v keytool)"

mkdir -p "$out"
if [[ -f "$out/ready" ]]; then
  printf 'workload-tls: ready marker exists, skipping generation\n'
  exit 0
fi

# Backend server identity.  The SAN covers the compose DNS name and the
# loopback address used by the container health check.  Subject DNs are kept
# to a single RDN (CN) because receivers authorize mTLS subjects with a
# comma-separated allowlist.
"$keytool" -genkeypair -alias backend-server -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=backend" \
  -ext "SAN=dns:backend,dns:localhost,ip:127.0.0.1" \
  -storetype PKCS12 -keystore "$out/backend-server.p12" -storepass "$storepass" -keypass "$storepass"

# Course workload identity: the renewable credential Course presents to
# Learning's internal endpoint.  Renewal keeps the subject (the authorization
# key configured on the receiver) while rotating the key material.
"$keytool" -genkeypair -alias course-client -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=course-service" \
  -storetype PKCS12 -keystore "$out/course-client.p12" -storepass "$storepass" -keypass "$storepass"

# Course server identity: the TLS listener that terminates inbound internal
# v2 requests.  The SAN covers the compose DNS name and the loopback address
# used by the container health check; the subject is Course's own workload
# name (same single-RDN rule as the other receivers).
"$keytool" -genkeypair -alias course-server -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=course-service" \
  -ext "SAN=dns:course-service,dns:localhost,ip:127.0.0.1" \
  -storetype PKCS12 -keystore "$out/course-server.p12" -storepass "$storepass" -keypass "$storepass"

"$keytool" -exportcert -alias backend-server -file "$out/backend-server.cer" \
  -keystore "$out/backend-server.p12" -storepass "$storepass"
"$keytool" -exportcert -alias course-client -file "$out/course-client.cer" \
  -keystore "$out/course-client.p12" -storepass "$storepass"

# Course trusts the backend certificate both as the Learning server on the
# outbound hop and as the backend client identity on Course's own TLS
# listener; the backend trusts only the Course client certificate for the
# Learning internal boundary.
"$keytool" -importcert -noprompt -alias backend-server -file "$out/backend-server.cer" \
  -storetype PKCS12 -keystore "$out/course-truststore.p12" -storepass "$storepass"
"$keytool" -importcert -noprompt -alias course-client -file "$out/course-client.cer" \
  -storetype PKCS12 -keystore "$out/backend-truststore.p12" -storepass "$storepass"

rm -f "$out/backend-server.cer" "$out/course-client.cer"
touch "$out/ready"
printf 'workload-tls: generated dev workload credentials in %s\n' "$out"
