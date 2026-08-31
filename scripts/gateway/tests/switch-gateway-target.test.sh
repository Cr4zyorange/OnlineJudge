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
  "$switcher" --mode compose --service grade --target grade-canary:9084 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"

grep -Fqx 'IDENTITY_UPSTREAM=identity-service:8081' "$runtime_dir/targets.env"
grep -Fqx 'COURSE_UPSTREAM=course-service:8082' "$runtime_dir/targets.env"
grep -Fqx 'ASSESSMENT_UPSTREAM=assessment-api:8083' "$runtime_dir/targets.env"
grep -Fqx 'GRADE_UPSTREAM=grade-canary:9084' "$runtime_dir/targets.env"
grep -Fqx 'LEARNING_UPSTREAM=learning-service:8085' "$runtime_dir/targets.env"
grep -Fq 'docker compose' "$log"
cp "$runtime_dir/targets.env" "$fixture_root/expected-after-rollback.env"

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" GATEWAY_SMOKE_FAIL=1 \
  GATEWAY_TEST_VERIFY_MARKER="$fixture_root/verify-marker" \
  "$switcher" --mode compose --service learning --target learning-canary:9085 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"
status=$?
set -e

[[ "$status" -eq 1 ]] || { printf 'expected verified rollback exit 1, got %s\n' "$status" >&2; exit 1; }

cmp "$runtime_dir/targets.env" "$fixture_root/expected-after-rollback.env"

cat > "$runtime_dir/targets.env" <<'EOF'
AUTH_UPSTREAM=auth-service:8081
CRS_UPSTREAM=crs-service:8082
ASSESSMENT_UPSTREAM=assessment-service:8083
LEARNING_GRADE_UPSTREAM=learning-grade-service:8084
EOF

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode compose --service identity --target identity-canary:9081 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh" \
  >"$fixture_root/legacy.stdout" 2>"$fixture_root/legacy.stderr"
legacy_status=$?
set -e

[[ "$legacy_status" -eq 64 ]] || { printf 'expected legacy target rejection exit 64, got %s\n' "$legacy_status" >&2; exit 1; }
grep -Fq 'legacy four-service target file is not supported' "$fixture_root/legacy.stderr"
printf 'switch-gateway-target.test: PASS\n'
