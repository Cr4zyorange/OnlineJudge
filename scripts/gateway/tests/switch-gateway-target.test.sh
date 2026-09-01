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

cat > "$fake_bin/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'kubectl %s\n' "$*" >> "$GATEWAY_TEST_LOG"
if [[ " $* " == *" create configmap gateway-config "* ]]; then
  printf 'apiVersion: v1\nkind: ConfigMap\n'
  exit 0
fi
if [[ " $* " == *" apply -f - "* ]]; then
  cat >/dev/null
  exit 0
fi
if [[ " $* " == *" port-forward svc/gateway "* ]]; then
  trap 'exit 0' INT TERM
  while :; do sleep 1; done
fi
EOF
chmod +x "$fake_bin/kubectl"

cat > "$fixture_root/verify.sh" <<'EOF'
#!/usr/bin/env bash
printf 'verify base=%s smoke=%s\n' "${GATEWAY_BASE:-}" "${GATEWAY_SMOKE_PATH:-}" >> "$GATEWAY_TEST_LOG"
if [[ "${GATEWAY_SMOKE_FAIL:-0}" == 1 && ! -f "$GATEWAY_TEST_VERIFY_MARKER" ]]; then
  touch "$GATEWAY_TEST_VERIFY_MARKER"
  exit 1
fi
EOF
chmod +x "$fixture_root/verify.sh"

cat > "$runtime_dir/targets.env" <<'EOF'
IDENTITY_UPSTREAM=identity-service:8081
COURSE_UPSTREAM=course-service:8082
ASSESSMENT_UPSTREAM=assessment-api:8083
GRADE_UPSTREAM=grade-service:8084
EOF

PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode compose --service course --target course-canary:9082 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"

grep -Fqx 'IDENTITY_UPSTREAM=identity-service:8081' "$runtime_dir/targets.env"
grep -Fqx 'COURSE_UPSTREAM=course-canary:9082' "$runtime_dir/targets.env"
grep -Fqx 'ASSESSMENT_UPSTREAM=assessment-api:8083' "$runtime_dir/targets.env"
grep -Fqx 'GRADE_UPSTREAM=grade-service:8084' "$runtime_dir/targets.env"
if grep -Fq 'LEARNING_UPSTREAM=' "$runtime_dir/targets.env"; then
  printf 'switch state still contains the retired Learning target\n' >&2
  exit 1
fi
grep -Fq 'docker compose' "$log"

PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode kind --service assessment --target assessment-canary:9083 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"

grep -Fq 'kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci create configmap gateway-config' "$log"
grep -Fq 'kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci rollout restart deployment/gateway' "$log"
grep -Fq 'kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci rollout status deployment/gateway --timeout=120s' "$log"
grep -Fq 'kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci port-forward svc/gateway 18090:8080' "$log"
grep -Fqx 'verify base=http://127.0.0.1:18090 smoke=/api/v1/homeworks' "$log"
cp "$runtime_dir/targets.env" "$fixture_root/expected-after-rollback.env"

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" GATEWAY_SMOKE_FAIL=1 \
  GATEWAY_TEST_VERIFY_MARKER="$fixture_root/verify-marker" \
  "$switcher" --mode compose --service grade --target grade-canary:9084 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh"
status=$?
set -e

[[ "$status" -eq 1 ]] || { printf 'expected verified rollback exit 1, got %s\n' "$status" >&2; exit 1; }

cmp "$runtime_dir/targets.env" "$fixture_root/expected-after-rollback.env"

cat > "$runtime_dir/targets.env" <<'EOF'
IDENTITY_UPSTREAM=identity-service:8081
COURSE_UPSTREAM=course-service:8082
ASSESSMENT_UPSTREAM=assessment-api:8083
GRADE_UPSTREAM=grade-service:8084
LEARNING_UPSTREAM=learning-service:8085
EOF

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode compose --service identity --target identity-canary:9081 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh" \
  >"$fixture_root/five-target.stdout" 2>"$fixture_root/five-target.stderr"
five_target_status=$?
set -e

[[ "$five_target_status" -eq 64 ]] || { printf 'expected retired five-target rejection exit 64, got %s\n' "$five_target_status" >&2; exit 1; }
grep -Fq 'LEARNING_UPSTREAM is retired; Learning is owned by Course' "$fixture_root/five-target.stderr"

set +e
PATH="$fake_bin:$PATH" GATEWAY_TEST_LOG="$log" \
  "$switcher" --mode compose --service learning --target learning-canary:9085 \
  --runtime-dir "$runtime_dir" --verify-command "$fixture_root/verify.sh" \
  >"$fixture_root/learning.stdout" 2>"$fixture_root/learning.stderr"
learning_status=$?
set -e

[[ "$learning_status" -eq 64 ]] || { printf 'expected Learning service rejection exit 64, got %s\n' "$learning_status" >&2; exit 1; }
grep -Fq 'service must be identity, course, assessment, or grade' "$fixture_root/learning.stderr"
printf 'switch-gateway-target.test: PASS\n'
