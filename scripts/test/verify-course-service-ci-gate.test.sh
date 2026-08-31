#!/usr/bin/env bash

# The backend gate is the formal CI entry point.  It must compile and test the
# independently deployable Course service, not merely the legacy backend.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
gate="$repo_root/scripts/ci/backend-verify.sh"
scratch="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-course-ci-gate.XXXXXX")"
cleanup() { rm -rf "$scratch"; }
trap cleanup EXIT INT TERM

mkdir -p "$scratch/scripts/ci" "$scratch/backend/target/surefire-reports" \
  "$scratch/services/assessment" "$scratch/services/course"
cp "$gate" "$scratch/scripts/ci/backend-verify.sh"
# The shared gate now also validates the independently deployed Assessment
# service.  Keep this mutation fixture structurally complete so the deliberate
# Course compiler failure remains the first failure and proves Course is still
# mandatory, rather than failing on an unrelated prerequisite.
touch "$scratch/backend/pom.xml" "$scratch/services/assessment/pom.xml" "$scratch/services/course/pom.xml"
mkdir "$scratch/fake-bin"

cat > "$scratch/fake-bin/java" <<'EOF'
#!/usr/bin/env bash
printf 'openjdk version "21.0.7"\n' >&2
EOF
cat > "$scratch/fake-bin/mvn" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "-version" ]]; then
  printf 'Apache Maven 3.9.11\n'
  exit 0
fi
if [[ "$PWD" == */services/course ]]; then
  printf 'intentional Course service compiler failure\n' >&2
  exit 97
fi
mkdir -p target/surefire-reports
touch target/surefire-reports/fake.xml
EOF
chmod +x "$scratch/fake-bin/java" "$scratch/fake-bin/mvn"

set +e
PATH="$scratch/fake-bin:$PATH" OJ_CI_JAVA_MAJOR=21 OJ_CI_MAVEN_MIN_VERSION=3.9.0 \
  bash "$scratch/scripts/ci/backend-verify.sh" "$scratch" >"$scratch/output.log" 2>&1
status=$?
set -e

if [[ $status -eq 0 ]]; then
  printf 'course-ci-gate mutation: FAIL: broken Course compile was not invoked by backend-verify\n' >&2
  cat "$scratch/output.log" >&2
  exit 1
fi
grep -Fq 'intentional Course service compiler failure' "$scratch/output.log" || {
  printf 'course-ci-gate mutation: FAIL: gate failed without reaching Course compilation\n' >&2
  cat "$scratch/output.log" >&2
  exit 1
}
printf 'course-ci-gate mutation: PASS (Course compile is a required backend gate command)\n'
