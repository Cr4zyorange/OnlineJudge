#!/usr/bin/env bash

# The Course image is tied to a source SHA and Compose verifies that its
# embedded application JAR equals the cleanly packaged local artifact.  A
# timestamped archive would make that provenance check depend on build order.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
course_dir="$checkout/services/course"
jar="$course_dir/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar"

fail() {
  printf 'course-reproducible-build: %s\n' "$1" >&2
  exit 1
}

command -v mvn >/dev/null 2>&1 || fail "mvn is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"
[[ -f "$course_dir/pom.xml" ]] || fail "missing Course pom.xml"

build_and_hash() {
  (cd "$course_dir" && mvn -B -ntp clean package -DskipTests >/dev/null)
  [[ -f "$jar" ]] || fail "Course clean package did not produce its executable JAR"
  sha256sum "$jar" | awk '{print $1}'
}

first_sha="$(build_and_hash)"
second_sha="$(build_and_hash)"
[[ "$first_sha" == "$second_sha" ]] ||
  fail "two clean Course packages differed: first=$first_sha second=$second_sha"

printf 'course-reproducible-build: PASS sha256=%s\n' "$first_sha"
