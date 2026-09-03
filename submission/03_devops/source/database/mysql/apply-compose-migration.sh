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

echo "apply-compose-migration.sh is a compatibility wrapper; migration history is managed by migrate.sh" >&2
exec "$script_dir/migrate.sh" --adapter compose --target "$migration_name"
