#!/usr/bin/env bash

# Create (or reuse) the kind cluster used by the issue #288 deployment
# baseline. Idempotent: an existing cluster is reused, creation is bounded by
# kind's --wait, and connectivity is verified with a request timeout instead
# of any fixed sleep.
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd kind
kindlib_require_cmd kubectl

if kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER_NAME"; then
  kindlib_note "kind cluster '$KIND_CLUSTER_NAME' already exists; reusing it"
else
  kindlib_note "creating kind cluster '$KIND_CLUSTER_NAME'"
  kind create cluster --config "$repo_root/deploy/k8s/kind-cluster.yaml" --wait 180s
fi

kindlib_kubectl get nodes --request-timeout=30s >/dev/null
kindlib_note "cluster ready under context '$KUBECTL_CONTEXT'"
