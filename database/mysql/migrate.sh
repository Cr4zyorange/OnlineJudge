#!/usr/bin/env sh

set -eu

adapter=local
baseline_through=
target_migration=
compose_file=
container_name=
namespace=default
pod_name=
seed_enabled=false

usage() {
    cat >&2 <<'USAGE'
usage: migrate.sh [--adapter local|compose|docker|kubectl]
                  [--baseline-through <migration.sql>]
                  [--target <migration.sql>]
                  [--seed]
                  [--compose-file <path>] [--container <name>]
                  [--namespace <name>] [--pod <name>]

The default local adapter requires MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE,
MYSQL_USER and MYSQL_PASSWORD. The other adapters use the same variables that
are already injected into the target mysql container or pod.
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --adapter)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            adapter=$2
            shift 2
            ;;
        --baseline-through)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            baseline_through=$2
            shift 2
            ;;
        --target)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            target_migration=$2
            shift 2
            ;;
        --seed)
            seed_enabled=true
            shift
            ;;
        --compose-file)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            compose_file=$2
            shift 2
            ;;
        --container)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            container_name=$2
            shift 2
            ;;
        --namespace)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            namespace=$2
            shift 2
            ;;
        --pod)
            [ "$#" -ge 2 ] || { usage; exit 64; }
            pod_name=$2
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

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
manifest_path="$repository_root/database/migrations/manifest.txt"
migration_dir="$repository_root/database/migrations"

[ -f "$manifest_path" ] || { echo "migration manifest not found: $manifest_path" >&2; exit 66; }

if [ -z "$compose_file" ]; then
    compose_file="$repository_root/deploy/docker/compose.yml"
fi

case "$adapter" in
    local)
        : "${MYSQL_HOST:?MYSQL_HOST is required for the local adapter}"
        : "${MYSQL_PORT:?MYSQL_PORT is required for the local adapter}"
        : "${MYSQL_DATABASE:?MYSQL_DATABASE is required for the local adapter}"
        : "${MYSQL_USER:?MYSQL_USER is required for the local adapter}"
        : "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required for the local adapter}"
        command -v mysql >/dev/null 2>&1 || { echo "mysql client is required" >&2; exit 69; }
        ;;
    compose)
        command -v docker >/dev/null 2>&1 || { echo "docker is required for the compose adapter" >&2; exit 69; }
        [ -f "$compose_file" ] || { echo "compose file not found: $compose_file" >&2; exit 66; }
        ;;
    docker)
        command -v docker >/dev/null 2>&1 || { echo "docker is required for the docker adapter" >&2; exit 69; }
        [ -n "$container_name" ] || { echo "--container is required for the docker adapter" >&2; exit 64; }
        ;;
    kubectl)
        command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required for the kubectl adapter" >&2; exit 69; }
        [ -n "$pod_name" ] || { echo "--pod is required for the kubectl adapter" >&2; exit 64; }
        ;;
    *)
        echo "unsupported adapter: $adapter" >&2
        exit 64
        ;;
esac

run_mysql() {
    case "$adapter" in
        local)
            MYSQL_PWD=$MYSQL_PASSWORD mysql \
                --protocol=tcp \
                --host="$MYSQL_HOST" \
                --port="$MYSQL_PORT" \
                --user="$MYSQL_USER" \
                --database="$MYSQL_DATABASE" \
                --batch --skip-column-names --raw
            ;;
        compose)
            docker compose -f "$compose_file" exec -T mysql sh -c \
                'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=socket --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --raw'
            ;;
        docker)
            docker exec -i "$container_name" sh -c \
                'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=socket --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --raw'
            ;;
        kubectl)
            kubectl --namespace "$namespace" exec -i "$pod_name" -- sh -c \
                'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=socket --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --raw'
            ;;
    esac
}

checksum_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    else
        echo "a SHA-256 tool (sha256sum, shasum, or openssl) is required" >&2
        exit 69
    fi
}

sql_escape() {
    printf '%s' "$1" | sed "s/'/''/g"
}

manifest_entries=
seen_entries='|'
while IFS= read -r raw_line || [ -n "$raw_line" ]; do
    migration_name=$(printf '%s' "$raw_line" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    case "$migration_name" in
        ''|'#'*) continue ;;
        *.sql) ;;
        *) echo "invalid migration manifest entry: $migration_name" >&2; exit 65 ;;
    esac
    case "$migration_name" in
        */*|*..*) echo "unsafe migration manifest entry: $migration_name" >&2; exit 65 ;;
    esac
    [ -f "$migration_dir/$migration_name" ] || { echo "migration not found: $migration_name" >&2; exit 66; }
    case "$seen_entries" in
        *"|$migration_name|"*) echo "duplicate migration manifest entry: $migration_name" >&2; exit 65 ;;
    esac
    seen_entries="${seen_entries}${migration_name}|"
    manifest_entries="${manifest_entries}${migration_name}
"
done < "$manifest_path"

if [ -n "$target_migration" ]; then
    case "$seen_entries" in
        *"|$target_migration|"*) ;;
        *) echo "target migration is not present in manifest: $target_migration" >&2; exit 65 ;;
    esac
fi

history_table_count=$(run_mysql <<'SQL'
SELECT COUNT(*)
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_type = 'BASE TABLE'
   AND table_name = 'schema_migrations';
SQL
)
business_table_count=$(run_mysql <<'SQL'
SELECT COUNT(*)
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_type = 'BASE TABLE'
   AND table_name <> 'schema_migrations';
SQL
)

case "$history_table_count" in ''|*[!0-9]*) echo "invalid migration history table count: $history_table_count" >&2; exit 70 ;; esac
case "$business_table_count" in ''|*[!0-9]*) echo "invalid business table count: $business_table_count" >&2; exit 70 ;; esac

if [ "$history_table_count" -eq 0 ] && [ "$business_table_count" -eq 0 ]; then
    echo "initializing empty database from database/mysql/compose-schema.sql"
    if ! run_mysql < "$repository_root/database/mysql/compose-schema.sql"; then
        echo "failed migration: compose-schema.sql" >&2
        exit 1
    fi
else
    run_mysql <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    installed_rank BIGINT NOT NULL AUTO_INCREMENT,
    version VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    installed_type VARCHAR(16) NOT NULL,
    execution_ms BIGINT NOT NULL DEFAULT 0,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (installed_rank),
    UNIQUE KEY uk_schema_migrations_version (version)
);
SQL
fi

history_count=$(run_mysql <<'SQL'
SELECT COUNT(*) FROM schema_migrations;
SQL
)

case "$history_count" in ''|*[!0-9]*) echo "invalid schema_migrations count: $history_count" >&2; exit 70 ;; esac

if [ "$history_count" -eq 0 ] && [ "$business_table_count" -gt 0 ] && [ -z "$baseline_through" ]; then
    echo "existing schema has no migration history; rerun with an explicitly verified --baseline-through <migration.sql>" >&2
    exit 65
fi

if [ -n "$baseline_through" ]; then
    if [ "$history_count" -ne 0 ]; then
        echo "--baseline-through is only valid when schema_migrations is empty" >&2
        exit 65
    fi
    if ! baseline_entries=$(printf '%s' "$manifest_entries" | awk -v target="$baseline_through" '
        NF { print }
        $0 == target { found = 1; exit }
        END { if (!found) exit 1 }
    '); then
        echo "baseline migration is not present in manifest: $baseline_through" >&2
        exit 65
    fi
    printf '%s\n' "$baseline_entries" | while IFS= read -r migration_name; do
        [ -n "$migration_name" ] || continue
        migration_path="$migration_dir/$migration_name"
        checksum=$(checksum_file "$migration_path")
        escaped_name=$(sql_escape "$migration_name")
        run_mysql <<SQL
INSERT INTO schema_migrations
    (version, checksum_sha256, installed_type, execution_ms, success)
VALUES
    ('$escaped_name', '$checksum', 'BASELINE', 0, 1);
SQL
        echo "baselined migration: $migration_name"
    done
fi

printf '%s' "$manifest_entries" | while IFS= read -r migration_name; do
    [ -n "$migration_name" ] || continue
    migration_path="$migration_dir/$migration_name"
    checksum=$(checksum_file "$migration_path")
    escaped_name=$(sql_escape "$migration_name")
    installed_checksum=$(run_mysql <<SQL
SELECT checksum_sha256
  FROM schema_migrations
 WHERE version = '$escaped_name'
   AND success = 1;
SQL
)
    if [ -n "$installed_checksum" ]; then
        if [ "$installed_checksum" != "$checksum" ]; then
            echo "checksum mismatch for migration: $migration_name" >&2
            echo "database checksum: $installed_checksum" >&2
            echo "repository checksum: $checksum" >&2
            exit 65
        fi
        echo "skipped migration: $migration_name"
        [ "$migration_name" != "$target_migration" ] || exit 0
        continue
    fi

    started_at=$(date +%s)
    if ! run_mysql < "$migration_path"; then
        echo "failed migration: $migration_name" >&2
        exit 1
    fi
    finished_at=$(date +%s)
    execution_ms=$(( (finished_at - started_at) * 1000 ))
    run_mysql <<SQL
INSERT INTO schema_migrations
    (version, checksum_sha256, installed_type, execution_ms, success)
VALUES
    ('$escaped_name', '$checksum', 'MIGRATION', $execution_ms, 1);
SQL
    echo "applied migration: $migration_name"
    [ "$migration_name" != "$target_migration" ] || exit 0
done

if [ "$seed_enabled" = true ]; then
    echo "applying DEV/CI seed: database/seeds/dev-ci.sql"
    if ! run_mysql < "$repository_root/database/seeds/dev-ci.sql"; then
        echo "failed migration: database/seeds/dev-ci.sql" >&2
        exit 1
    fi
fi

echo "migration run complete"
