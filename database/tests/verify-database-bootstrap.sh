#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
fresh_container="oj-db-287-fresh-$$"
upgrade_container="oj-db-287-upgrade-$$"
database_name=onlinejudge
database_user=onlinejudge
database_password='issue287-ephemeral-app'
root_password='issue287-ephemeral-root'

cleanup() {
    docker rm -f "$fresh_container" "$upgrade_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 69; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; exit 69; }

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        repository_mount=$(cd "$repository_root" && pwd -W)
        ;;
    *)
        repository_mount=$repository_root
        ;;
esac

start_mysql() {
    container=$1
    shift
    docker run --detach --name "$container" \
        --env MYSQL_DATABASE="$database_name" \
        --env MYSQL_USER="$database_user" \
        --env MYSQL_PASSWORD="$database_password" \
        --env MYSQL_ROOT_PASSWORD="$root_password" \
        "$@" \
        mysql:8.4 \
        --character-set-server=utf8mb4 \
        --collation-server=utf8mb4_unicode_ci >/dev/null

    attempts=0
    until docker logs "$container" 2>&1 | grep -Fq 'MySQL init process done. Ready for start up.'; do
        attempts=$((attempts + 1))
        if [ "$attempts" -ge 120 ]; then
            echo "mysql:8.4 initialization did not finish: $container" >&2
            docker logs "$container" >&2 || true
            exit 1
        fi
        if [ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)" != true ]; then
            echo "mysql:8.4 exited during startup: $container" >&2
            docker logs "$container" >&2 || true
            exit 1
        fi
        sleep 1
    done

    attempts=0
    until docker exec "$container" sh -c \
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysqladmin ping --protocol=socket --user=root --silent' >/dev/null 2>&1; do
        attempts=$((attempts + 1))
        if [ "$attempts" -ge 60 ]; then
            echo "mysql:8.4 final server did not become ready: $container" >&2
            docker logs "$container" >&2 || true
            exit 1
        fi
        sleep 1
    done
}

run_sql_file() {
    container=$1
    sql_file=$2
    docker exec -i "$container" sh -c \
        'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=socket --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --show-warnings' \
        < "$sql_file"
}

echo "[fresh] start empty mysql:8.4 with the Compose schema and DEV/CI seed"
start_mysql "$fresh_container" \
    --mount "type=bind,src=$repository_mount/database/mysql/compose-schema.sql,dst=/docker-entrypoint-initdb.d/01-schema.sql,readonly" \
    --mount "type=bind,src=$repository_mount/database/seeds/dev-ci.sql,dst=/docker-entrypoint-initdb.d/02-dev-ci-seed.sql,readonly"
"$repository_root/database/mysql/migrate.sh" --adapter docker --container "$fresh_container"
run_sql_file "$fresh_container" "$repository_root/database/tests/assert-latest.sql"

echo "[upgrade] create the retained-volume baseline and apply pending migrations"
start_mysql "$upgrade_container" \
    --mount "type=bind,src=$repository_mount/database/mysql/compose-schema.sql,dst=/docker-entrypoint-initdb.d/01-schema.sql,readonly"
run_sql_file "$upgrade_container" "$repository_root/database/tests/prepare-upgrade-baseline.sql"
"$repository_root/database/mysql/migrate.sh" \
    --adapter docker \
    --container "$upgrade_container" \
    --baseline-through 20260822_03_create_hwk_submission_attachment.sql
run_sql_file "$upgrade_container" "$repository_root/database/seeds/dev-ci.sql"
run_sql_file "$upgrade_container" "$repository_root/database/tests/assert-latest.sql"

echo "[upgrade] repeat migration and seed to prove deterministic no-op/idempotent behavior"
"$repository_root/database/mysql/migrate.sh" --adapter docker --container "$upgrade_container"
run_sql_file "$upgrade_container" "$repository_root/database/seeds/dev-ci.sql"
run_sql_file "$upgrade_container" "$repository_root/database/tests/assert-latest.sql"

echo "database bootstrap verification passed: fresh=1 upgrade=1 repeat=1"
