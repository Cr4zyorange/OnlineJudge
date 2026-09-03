#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
runner="$repo_root/scripts/test/verify-grade-service-mysql-live.sh"

[[ -f "$runner" ]] || {
  echo "grade-mysql-live-contract: missing runner $runner" >&2
  exit 1
}

if grep -Fq -- '--protocol=socket' "$runner"; then
  echo "grade-mysql-live-contract: socket protocol can report the MySQL bootstrap server as ready" >&2
  exit 1
fi

python3 - "$repo_root/database/migrations/grade/V20260902_03__drop_legacy_grade_source_projection_status.sql" <<'PY'
from pathlib import Path
import sys

migration = Path(sys.argv[1])
assert migration.is_file(), "the legacy status column needs a forward-only Grade migration"
text = migration.read_text(encoding="utf-8")
assert "DROP COLUMN status" in text, text
PY

awk '
  function verify_invocation() {
    if (command !~ /(^|[[:space:]])mysql([[:space:]]|$)/ || command !~ /--user=root/) {
      return
    }

    root_invocations++
    if (command !~ /--protocol=tcp/ || command !~ /--host=127[.]0[.]0[.]1/) {
      printf "grade-mysql-live-contract: root/admin/migration query must use TCP loopback:\n%s\n", command > "/dev/stderr"
      failures++
    }
  }

  /docker exec/ {
    command = $0
    capturing = 1
    if ($0 !~ /\\$/) {
      verify_invocation()
      capturing = 0
    }
    next
  }

  capturing {
    command = command "\n" $0
    if ($0 !~ /\\$/) {
      verify_invocation()
      capturing = 0
    }
  }

  END {
    if (root_invocations != 5) {
      printf "grade-mysql-live-contract: expected 5 root/admin/migration queries, found %d\n", root_invocations > "/dev/stderr"
      failures++
    }
    exit failures != 0
  }
' "$runner"

echo "grade-mysql-live-contract: PASS root/admin/migration queries=5 protocol=tcp host=127.0.0.1"
