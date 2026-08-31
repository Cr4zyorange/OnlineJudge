#!/usr/bin/env bash
set -euo pipefail

proxy_name="oj-assessment-sandbox-$$"
cleanup() {
  docker rm -f "$proxy_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker pull python:3.12-alpine >/dev/null
docker pull tecnativa/docker-socket-proxy:0.1.2 >/dev/null
docker run -d --rm --name "$proxy_name" \
  -e CONTAINERS=1 -e IMAGES=1 -e POST=1 -e ALLOW_START=1 -e ALLOW_RESTARTS=1 \
  -e NETWORKS=0 -e VOLUMES=0 -e EXEC=0 -e BUILD=0 \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  -p 127.0.0.1::2375 tecnativa/docker-socket-proxy:0.1.2 >/dev/null

proxy_port="$(docker port "$proxy_name" 2375/tcp | awk -F: 'NR == 1 { print $NF }')"
test -n "$proxy_port"
for _ in $(seq 1 30); do
  if curl --fail --silent "http://127.0.0.1:${proxy_port}/_ping" >/dev/null; then break; fi
  sleep 1
done
curl --fail --silent "http://127.0.0.1:${proxy_port}/_ping" >/dev/null

mvn -B -ntp -f services/assessment/pom.xml \
  -Dtest=DockerSandboxClientIntegrationTest,LabWorkflowContractTest#workerExecutesPersistedLabCodeAgainstTestcasesAndStoresCaseResults,LabWorkflowContractTest#sandboxUsesEachLabTimeLimitInsteadOfGlobalTimeout \
  -Dassessment.docker-sandbox.test=true \
  -Dassessment.docker-sandbox.api="http://127.0.0.1:${proxy_port}" test
