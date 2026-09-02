#!/usr/bin/env bash
# Attach the existing narrowed Docker socket proxy only for Issue #320's
# after-ready browser gate. It is a test-execution dependency rather than a
# tenth platform workload and is removed before control returns to #318.

set -Eeuo pipefail

project_name="${E2E_THREE_SERVICE_PROJECT:?E2E_THREE_SERVICE_PROJECT is required}"
artifact_dir="${E2E_ARTIFACT_DIR:?E2E_ARTIFACT_DIR is required}"
if (($# == 0)); then
  printf 'run-business-e2e-three-service-sandbox: command is required\n' >&2
  exit 2
fi

network_ids=()
while IFS= read -r network_id; do
  [[ -n "$network_id" ]] && network_ids+=("$network_id")
done < <(docker network ls --quiet --filter "label=com.docker.compose.project=$project_name")
if ((${#network_ids[@]} != 1)); then
  printf 'run-business-e2e-three-service-sandbox: expected one private Compose network for %s, found %s\n' \
    "$project_name" "${#network_ids[@]}" >&2
  exit 1
fi

proxy_name="${project_name}-sandbox-$$"
proxy_log="$artifact_dir/sandbox-proxy.log"
cleanup() {
  local prior_status=$?
  trap - EXIT
  docker logs "$proxy_name" > "$proxy_log" 2>&1 || true
  if ! docker rm -f "$proxy_name" >/dev/null 2>&1; then
    printf 'run-business-e2e-three-service-sandbox: failed to remove %s\n' "$proxy_name" >&2
    if ((prior_status == 0)); then prior_status=1; fi
  fi
  exit "$prior_status"
}
trap cleanup EXIT

docker pull tecnativa/docker-socket-proxy:0.1.2 > "$artifact_dir/sandbox-proxy-pull.log" 2>&1
docker run --detach --name "$proxy_name" \
  --label "io.onlinejudge.issue320.project=$project_name" \
  --network "${network_ids[0]}" \
  --network-alias assessment-sandbox-docker-proxy \
  -e CONTAINERS=1 -e IMAGES=1 -e POST=1 -e ALLOW_START=1 -e ALLOW_RESTARTS=1 \
  -e NETWORKS=0 -e VOLUMES=0 -e EXEC=0 -e BUILD=0 \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  tecnativa/docker-socket-proxy:0.1.2 > "$artifact_dir/sandbox-proxy-container-id.txt"

if [[ "$(docker inspect --format '{{.State.Running}}' "$proxy_name")" != "true" ]]; then
  printf 'run-business-e2e-three-service-sandbox: proxy did not remain running\n' >&2
  exit 1
fi

"$@"
