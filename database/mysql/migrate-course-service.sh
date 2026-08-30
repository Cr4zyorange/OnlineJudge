#!/usr/bin/env bash
set -euo pipefail

# Course's Compose/operations migration job.  It owns only oj_course and its
# runtime account; the five-domain #341 controller remains responsible for a
# quiescent cross-schema data cutover.  This runner is safe for fresh startup,
# resume after a failed DDL step, and an already-migrated #341 target.
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
migration_dir="${COURSE_MIGRATIONS_DIR:-$repo_root/database/migrations/course}"
host="${COURSE_DATABASE_HOST:-mysql}"
port="${COURSE_DATABASE_PORT:-3306}"
database="${COURSE_DATABASE_NAME:-oj_course}"
account="${COURSE_DATABASE_USER:-oj_course_rw}"
root_password="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
account_password="${COURSE_DATABASE_PASSWORD:?COURSE_DATABASE_PASSWORD is required}"

fail() {
  printf 'course-migrations: %s\n' "$*" >&2
  exit 1
}

[[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || fail 'COURSE_DATABASE_NAME is not a safe identifier'
[[ "$account" =~ ^[A-Za-z0-9_]{1,32}$ ]] || fail 'COURSE_DATABASE_USER is not a safe identifier'
[[ "$port" =~ ^[0-9]{1,5}$ ]] || fail 'COURSE_DATABASE_PORT is not numeric'
[[ -d "$migration_dir" ]] || fail "migration directory does not exist: $migration_dir"

mysql_root=(mysql --protocol=TCP --host="$host" --port="$port" --user=root --batch --skip-column-names --raw)

sql_literal() {
  printf "'"
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
  printf "'"
}

root_sql() {
  MYSQL_PWD="$root_password" "${mysql_root[@]}" --execute "$1"
}

database_sql() {
  MYSQL_PWD="$root_password" "${mysql_root[@]}" --database="$database" --execute "$1"
}

database_file() {
  MYSQL_PWD="$root_password" "${mysql_root[@]}" --database="$database" < "$1"
}

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

quoted_account="$(sql_literal "$account")"
quoted_password="$(sql_literal "$account_password")"

root_sql "CREATE DATABASE IF NOT EXISTS \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
root_sql "CREATE USER IF NOT EXISTS $quoted_account@'%' IDENTIFIED BY $quoted_password"
root_sql "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$database\`.* TO $quoted_account@'%'"
database_sql "CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(255) NOT NULL PRIMARY KEY,
  checksum_sha256 CHAR(64) NOT NULL,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"

for migration in \
  V20260831_01__course_service_schema.sql \
  V20260831_02__course_security_version_inbox.sql \
  V20260831_03__course_runtime_version_columns.sql; do
  path="$migration_dir/$migration"
  [[ -f "$path" ]] || fail "missing migration $path"
  expected_checksum="$(checksum "$path")"
  quoted_version="$(sql_literal "$migration")"
  actual_checksum="$(database_sql "SELECT checksum_sha256 FROM schema_migrations WHERE version = $quoted_version" || true)"
  if [[ -n "$actual_checksum" ]]; then
    [[ "$actual_checksum" == "$expected_checksum" ]] || fail "checksum drift for $migration"
    printf 'course-migrations: skip %s checksum=%s\n' "$migration" "$expected_checksum"
    continue
  fi
  database_file "$path"
  database_sql "INSERT INTO schema_migrations (version, checksum_sha256) VALUES ($quoted_version, $(sql_literal "$expected_checksum"))"
  printf 'course-migrations: applied %s checksum=%s\n' "$migration" "$expected_checksum"
done

printf 'course-migrations: PASS schema=%s account=%s migrations=3\n' "$database" "$account"
