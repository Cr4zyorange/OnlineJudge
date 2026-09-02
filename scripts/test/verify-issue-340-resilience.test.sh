#!/usr/bin/env bash

# RED/GREEN contract for the executable #340 resilience acceptance matrix.
# This test intentionally runs without Docker so the matrix shape and evidence
# hygiene are reviewable before a real disposable environment is available.
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
runner="$repo_root/scripts/test/verify-issue-340-resilience.sh"
matrix="$repo_root/scripts/test/issue-340-resilience-matrix.json"

fail() {
  printf 'verify-issue-340-resilience.test: FAIL: %s\n' "$*" >&2
  exit 1
}

[[ -f "$runner" ]] || fail "missing runner: ${runner#$repo_root/}"
[[ -f "$matrix" ]] || fail "missing matrix: ${matrix#$repo_root/}"
command -v python3 >/dev/null 2>&1 || fail 'python3 is required'

python3 - "$matrix" <<'PY'
import json
import sys
from pathlib import Path

matrix_path = Path(sys.argv[1])
matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
assert matrix["issue"] == 340, matrix
scenarios = matrix["scenarios"]
expected = {
    "course-delay",
    "assessment-api-down",
    "worker-kill",
    "grade-down",
    "rabbitmq-down",
    "identity-down",
    "duplicate-gap-dlq",
}
assert {item["id"] for item in scenarios} == expected, scenarios
assert len(scenarios) == len(expected), scenarios
for item in scenarios:
    assert set(item["assertions"]) == {"before", "during", "recovery"}, item
    assert item["command"], item
    assert item["evidence"], item
covered = {ac for item in scenarios for ac in item["acs"]}
assert covered == {"AC-340-01", "AC-340-02", "AC-340-03", "AC-340-04", "AC-340-05"}, covered
grade_down = next(item for item in scenarios if item["id"] == "grade-down")
assert {
    "sourceGradeEventId",
    "sourceGradeRevision",
    "sourceGradeOutboxState",
    "gradeInboxStatus",
    "gradeProjectionCount",
    "duplicateDecision",
    "recoverySeconds",
} <= set(grade_down["evidence"]), grade_down
identity_down = next(item for item in scenarios if item["id"] == "identity-down")
assert "protectedReadStatus" in identity_down["evidence"], identity_down
PY

grep -Fq -- '--contract-only' "$runner" || fail 'runner must expose --contract-only'
grep -Fq -- '--bootstrap-318' "$runner" || fail 'runner must expose --bootstrap-318'
grep -Fq -- '--env-file' "$runner" || fail 'runner must accept the disposable runtime env-file'
grep -Fq -- '--keep-runtime-env' "$repo_root/scripts/platform/run_disposable_environment.sh" \
  || fail 'disposable environment must support a temporary retained env-file'
grep -Fq -- '--runtime-env-path' "$repo_root/scripts/platform/run_disposable_environment.sh" \
  || fail 'disposable environment must accept an explicit env-file path'
grep -Fq -- 'RESILIENCE_MATRIX_PASS issue=#340' "$runner" || fail 'runner must print the structured PASS marker'
grep -Fq -- 'taskId' "$runner" || fail 'runner must preserve task identity in evidence'
grep -Fq -- 'eventId' "$runner" || fail 'runner must preserve event identity in evidence'
grep -Fq -- 'revision' "$runner" || fail 'runner must preserve revision evidence'
grep -Fq -- 'outbox' "$runner" || fail 'runner must record outbox evidence'
grep -Fq -- 'inbox' "$runner" || fail 'runner must record inbox evidence'
grep -Fq -- 'DLQ' "$runner" || fail 'runner must record DLQ evidence'
grep -Fq -- 'ASSESSMENT_DATABASE_PASSWORD' "$runner" || fail 'runner must redact database secrets'
grep -Fq -- '/api/v1/courses/1/grade-items' "$runner" \
  || fail 'assessment outage must probe an independent Grade read route'
grep -Fq -- 'service_outage assessment-api-down' "$runner" \
  || fail 'runner must include the assessment outage scenario'
grep -Fq -- 'query-login-meta' "$runner" || fail 'runner must store only safe login metadata'
if grep -Fq -- '--output "$output_dir/query-login.json"' "$runner"; then
  fail 'runner must never upload the raw login response containing a bearer token'
fi
grep -Fq -- 'grade_down_source_fixture' "$runner" \
  || fail 'runner must seed an Assessment source fact/outbox during Grade outage'
grep -Fq -- 'sourceGradeOutboxStateBefore=' "$runner" \
  || fail 'runner must record the source outbox state before relay recovery'
grep -Fq -- 'source_outbox_state_before" == PENDING' "$runner" \
  || fail 'runner must assert the source outbox is pending before relay recovery'
python3 - "$runner" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text(encoding="utf-8")
fixture = text[text.index("grade_down_source_fixture()") : text.index("identity_dir=")]
grade_start = fixture.index('"${compose[@]}" start grade-service')
worker_start = fixture.index('"${compose[@]}" start assessment-worker')
assert grade_start < worker_start, "Grade consumer must be ready before Assessment relay starts"
PY
grep -Fq -- 'duplicateDecision' "$runner" \
  || fail 'runner must prove duplicate delivery is a no-op'
grep -Fq -- 'cached-jwt-query' "$runner" \
  || fail 'runner must exercise a protected read with the pre-obtained JWT'
grep -Fq -- 'OJ314_RAW_LOG_PATH' "$repo_root/scripts/test/verify-issue-314-recovery-disposable.sh" \
  || fail 'disposable recovery must expose a retained raw log path'
grep -Fq -- 'OJ314_KEEP_RAW_LOG' "$repo_root/scripts/test/verify-issue-314-recovery-disposable.sh" \
  || fail 'disposable recovery must retain raw assertions for the resilience report'
grep -Fq -- 'stale completion' "$repo_root/scripts/test/verify-issue-314-recovery-disposable.sh" \
  || fail 'disposable recovery must execute stale completion fencing'
if grep -Eq 'printf .*PASSWORD|printf .*TOKEN|printf .*SECRET|echo .*PASSWORD' "$runner"; then
  fail 'runner must never print secret values'
fi

evidence_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-issue-340-contract.XXXXXX")"
cleanup() { rm -rf -- "$evidence_dir"; }
trap cleanup EXIT INT TERM

"$runner" --contract-only --output-dir "$evidence_dir" >"$evidence_dir/stdout" 2>"$evidence_dir/stderr" \
  || { cat "$evidence_dir/stderr" >&2; fail 'contract-only matrix execution failed'; }
grep -Fq 'RESILIENCE_MATRIX_PASS issue=#340 scenarios=7 passed=7' "$evidence_dir/stdout" \
  || { cat "$evidence_dir/stdout" >&2; fail 'structured PASS marker missing'; }
[[ -s "$evidence_dir/report.json" ]] || fail 'contract-only report.json was not written'
python3 - "$evidence_dir/report.json" <<'PY'
import json
import sys
report = json.load(open(sys.argv[1], encoding="utf-8"))
assert report["issue"] == 340, report
assert report["status"] == "PASS", report
assert report["scenarioCount"] == 7, report
assert report["passed"] == 7, report
assert report["redacted"] is True, report
for item in report["scenarios"]:
    assert item["status"] == "PASS", item
    assert item["before"] and item["during"] and item["recovery"], item
PY

printf 'verify-issue-340-resilience.test: PASS\n'
