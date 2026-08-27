#!/usr/bin/env bash

# Precise cleanup for the issue #288 baseline. Default scope: exactly the CI
# namespace (the kind cluster and loaded images are kept for reruns).
# Pass --cluster to also delete the named kind cluster. Nothing outside
# namespace onlinejudge-ci / cluster onlinejudge-ci is ever touched.
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd kubectl

delete_cluster=no
case "${1:-}" in
  "")
    ;;
  --cluster)
    delete_cluster=yes
    ;;
  *)
    kindlib_fail "unknown option: $1 (only --cluster is supported)"
    ;;
esac

kindlib_note "cleanup scope: namespace '$K8S_NAMESPACE' only"
if kindlib_kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1; then
  kindlib_note "deleting namespace '$K8S_NAMESPACE' (bounded wait, timeout $CLEANUP_TIMEOUT_S)"
  kindlib_kubectl delete namespace "$K8S_NAMESPACE" --wait=true --timeout="$CLEANUP_TIMEOUT_S"
else
  kindlib_note "namespace '$K8S_NAMESPACE' already absent"
fi

if kindlib_kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1; then
  kindlib_fail "namespace '$K8S_NAMESPACE' still present after bounded delete"
fi
kindlib_note "namespace '$K8S_NAMESPACE' removed"

if [[ "$delete_cluster" == "yes" ]]; then
  kindlib_require_cmd kind
  kindlib_note "deleting kind cluster '$KIND_CLUSTER_NAME'"
  kind delete cluster --name "$KIND_CLUSTER_NAME"
else
  kindlib_note "kind cluster '$KIND_CLUSTER_NAME' kept (pass --cluster to remove it too)"
fi

kindlib_note "cleanup complete"
