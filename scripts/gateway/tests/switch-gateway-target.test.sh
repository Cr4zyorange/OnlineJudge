#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
switcher="$repo_root/scripts/gateway/switch-gateway-target.sh"
fixture_root="$(mktemp -d)"
runtime_dir="$fixture_root/runtime"
fake_bin="$fixture_root/bin"
log="$fixture_root/commands.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$runtime_dir" "$fake_bin"
cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$GATEWAY_TEST_LOG"
EOF
chmod +x "$fake_bin/docker"

cat > "$fixture_root/verify.sh" <<'EOF'
#!/usr/bin/env bash
if [[ "${GATEWAY_SMOKE_FAIL:-0}" == 1 && ! -f "$GATEWAY_TEST_VERIFY_MARKER" ]]; then
  touch "$GATEWAY_TEST_VERIFY_MARKER"
  exit 1
fi
EOF
chmod +x "$fixture_root/verify.sh"

PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode compose --service auth --target auth-service:8081 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"

grep -Fqx 'AUTH_UPSTREAM=auth-service:8081' "$runtime_dir/targets.env"
grep -Fq 'docker compose' "$log"

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" GATEWAY_SMOKE_FAIL=1 \
  GATEWAY_TEST_VERIFY_MARKER="$fixture_root/verify-marker" \
  "$switcher" --mode compose --service auth --target backend:8080 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"
status=$?
set -e

[[ "$status" -eq 1 ]] || { printf 'expected verified rollback exit 1, got %s\n' "$status" >&2; exit 1; }

grep -Fqx 'AUTH_UPSTREAM=auth-service:8081' "$runtime_dir/targets.env"
printf 'switch-gateway-target.test: PASS\n'
