#!/usr/bin/env sh

# Apply one service-owned migration stream with a credential that is distinct
# from the runtime application's DML-only account.  This is deliberately a
# small POSIX-shell runner so the same artifact can be used by a deployment Job
# and a disposable MySQL acceptance test.
set -eu

usage() {
    echo "usage: migrate-service.sh --schema identity|course|assessment|grade" >&2
}

schema=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --schema)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            schema=$2
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "unknown argument: $1" >&2
            usage
            exit 64
            ;;
    esac
done

case "$schema" in
    identity|course|assessment|grade) ;;
    *)
        echo "--schema must be one of identity, course, assessment, grade" >&2
        exit 64
        ;;
esac

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:?MYSQL_PORT is required}"
: "${MIGRATION_DATABASE_NAME:?MIGRATION_DATABASE_NAME is required}"
: "${MIGRATION_DATABASE_USER:?MIGRATION_DATABASE_USER is required}"
: "${MIGRATION_DATABASE_PASSWORD:?MIGRATION_DATABASE_PASSWORD is required}"

command -v mysql >/dev/null 2>&1 || {
    echo "mysql client is required" >&2
    exit 69
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
migration_root=${MIGRATION_ROOT:-"$script_dir/../migrations"}
migration_dir="$migration_root/$schema"
[ -d "$migration_dir" ] || {
    echo "migration directory not found: $migration_dir" >&2
    exit 66
}

run_mysql() {
    MYSQL_PWD=$MIGRATION_DATABASE_PASSWORD mysql \
        --protocol=tcp \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MIGRATION_DATABASE_USER" \
        --database="$MIGRATION_DATABASE_NAME" \
        --batch --skip-column-names --raw
}

checksum_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "a SHA-256 utility is required" >&2
        exit 69
    fi
}

sql_escape() {
    printf '%s' "$1" | sed "s/'/''/g"
}

run_mysql <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version)
);
SQL

# #341's copied legacy schemas retain the older migration ledger shape, where
# installed_type is mandatory.  A D7 service Job must be able to checkpoint a
# Course upgrade from that live shape as well as a freshly-created service
# schema; application boot DDL is not an acceptable fallback.
legacy_ledger=$(run_mysql <<'SQL'
SELECT COUNT(*)
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'schema_migrations'
   AND column_name = 'installed_type';
SQL
)

set -- "$migration_dir"/*.sql
if [ ! -f "$1" ]; then
    echo "migrate-service: schema=$schema no migration files"
    exit 0
fi

applied=0
for migration_path in "$@"; do
    version=$(basename "$migration_path")
    checksum=$(checksum_file "$migration_path")
    escaped_version=$(sql_escape "$version")
    installed_checksum=$(run_mysql <<SQL
SELECT checksum_sha256
  FROM schema_migrations
 WHERE version = '$escaped_version';
SQL
)
    if [ -n "$installed_checksum" ]; then
        if [ "$installed_checksum" != "$checksum" ]; then
            echo "migration checksum mismatch: $version" >&2
            exit 65
        fi
        echo "migrate-service: schema=$schema version=$version already-applied"
        continue
    fi

    if ! run_mysql < "$migration_path"; then
        echo "migration failed: $version" >&2
        exit 1
    fi
    if [ "$legacy_ledger" = "1" ]; then
        run_mysql <<SQL
INSERT INTO schema_migrations
    (version, checksum_sha256, installed_type, execution_ms, success)
VALUES ('$escaped_version', '$checksum', 'SERVICE', 0, 1);
SQL
    else
        run_mysql <<SQL
INSERT INTO schema_migrations (version, checksum_sha256)
VALUES ('$escaped_version', '$checksum');
SQL
    fi
    applied=$((applied + 1))
    echo "migrate-service: schema=$schema version=$version applied"
done

echo "migrate-service: PASS schema=$schema applied=$applied"
