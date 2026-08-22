#!/usr/bin/env sh

set -eu

if [ "$#" -ne 1 ]; then
    echo "usage: $0 <versioned-migration.sql>" >&2
    exit 64
fi

migration_name=$1
case "$migration_name" in
    ""|*/*|*..*)
        echo "migration must be a .sql filename from database/migrations" >&2
        exit 64
        ;;
esac
case "$migration_name" in
    *.sql) ;;
    *)
        echo "migration must be a .sql filename from database/migrations" >&2
        exit 64
        ;;
esac

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
migration_path="$repository_root/database/migrations/$migration_name"
compose_path="$repository_root/deploy/docker/compose.yml"

if [ ! -f "$migration_path" ]; then
    echo "migration not found: $migration_path" >&2
    exit 66
fi

docker compose -f "$compose_path" exec -T mysql sh -c \
    'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=socket --user="$MYSQL_USER" "$MYSQL_DATABASE"' \
    < "$migration_path"

echo "applied migration: $migration_name"
