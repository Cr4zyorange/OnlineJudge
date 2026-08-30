#!/usr/bin/env sh

# Disposable MySQL 8.4 proof for Identity service-token idempotency.  The
# JUnit regression starts two independently transaction-proxied service pods
# against this server, so H2 cannot hide InnoDB REPEATABLE-READ races.
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
suffix="$$"
mysql_container="oj311_identity_token_mysql_$suffix"
mysql_password="oj311_identity_token_$suffix"

cleanup() {
    docker rm -f "$mysql_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 69; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; exit 69; }
command -v mysql >/dev/null 2>&1 || { echo "mysql client is required" >&2; exit 69; }

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) repository_mount=$(cd "$repository_root" && pwd -W) ;;
    *) repository_mount=$repository_root ;;
esac

docker run --detach --rm --name "$mysql_container" \
    --publish 127.0.0.1::3306 \
    --env MYSQL_DATABASE=onlinejudge_identity_test \
    --env MYSQL_USER=onlinejudge_identity \
    --env MYSQL_PASSWORD="$mysql_password" \
    --env MYSQL_ROOT_PASSWORD="$mysql_password" \
    --mount "type=bind,src=$repository_mount/database/migrations/identity/DB-IDENTITY-02-service-token-idempotency.sql,dst=/docker-entrypoint-initdb.d/02-service-token-idempotency.sql,readonly" \
    mysql:8.4 --transaction-isolation=REPEATABLE-READ --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null

mysql_port=$(docker port "$mysql_container" 3306/tcp | head -n 1)
mysql_port=${mysql_port##*:}
case "$mysql_port" in
    *[!0-9]*|'') echo "unable to resolve disposable MySQL port" >&2; exit 1 ;;
esac

attempt=0
until docker exec "$mysql_container" sh -c "MYSQL_PWD='$mysql_password' mysqladmin --protocol=socket --user=root ping --silent" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 90 ] || { docker logs "$mysql_container" >&2; exit 1; }
    sleep 1
done

attempt=0
until MYSQL_PWD="$mysql_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" \
    --user=onlinejudge_identity --database=onlinejudge_identity_test --execute='SELECT @@transaction_isolation' >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 45 ] || { docker logs "$mysql_container" >&2; exit 1; }
    sleep 1
done

echo "identity-service-token-live: mysql=$mysql_container:$mysql_port isolation=REPEATABLE-READ"
ONLINEJUDGE_LIVE_IDENTITY_SERVICE_TOKENS=true \
    mvn -f "$repository_root/services/identity/pom.xml" \
    -Dtest=ServiceTokenMySqlConcurrencyLiveTest \
    -Doj.identity.mysql.url="jdbc:mysql://127.0.0.1:$mysql_port/onlinejudge_identity_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC" \
    -Doj.identity.mysql.username=onlinejudge_identity \
    -Doj.identity.mysql.password="$mysql_password" test
echo "identity-service-token-live: PASS same-payload=2 conflict-payload=409"
