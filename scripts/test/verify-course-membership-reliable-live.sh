#!/usr/bin/env sh

# #337 source-producer proof.  It starts disposable MySQL 8.4 and RabbitMQ
# 4.1, applies only the checked-out compose schema, then exercises the real
# Course command -> transactional outbox -> confirmed Rabbit -> Learning flow.
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
suffix="$$"
mysql_container="oj337_course_mysql_$suffix"
rabbit_container="oj337_course_rabbit_$suffix"
mysql_password="oj337_live_mysql_$suffix"

cleanup() {
    docker rm -f "$rabbit_container" "$mysql_container" >/dev/null 2>&1 || true
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
    --env MYSQL_DATABASE=onlinejudge \
    --env MYSQL_USER=onlinejudge \
    --env MYSQL_PASSWORD="$mysql_password" \
    --env MYSQL_ROOT_PASSWORD="$mysql_password" \
    --mount "type=bind,src=$repository_mount/database/mysql/compose-schema.sql,dst=/docker-entrypoint-initdb.d/01-schema.sql,readonly" \
    mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null

docker run --detach --rm --name "$rabbit_container" \
    --publish 127.0.0.1::5672 rabbitmq:4.1-management >/dev/null

mysql_port=$(docker port "$mysql_container" 3306/tcp | head -n 1)
mysql_port=${mysql_port##*:}
rabbit_port=$(docker port "$rabbit_container" 5672/tcp | head -n 1)
rabbit_port=${rabbit_port##*:}
case "$mysql_port:$rabbit_port" in
    *[!0-9:]*|:) echo "unable to resolve disposable ports" >&2; exit 1 ;;
esac

attempt=0
until docker exec "$mysql_container" sh -c "MYSQL_PWD='$mysql_password' mysqladmin --protocol=socket --user=root ping --silent" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 90 ] || { docker logs "$mysql_container" >&2; exit 1; }
    sleep 1
done

# Socket readiness precedes Docker's host-port relay by a short interval on
# some desktop daemons.  The Maven test uses TCP, so prove that exact path.
attempt=0
until MYSQL_PWD="$mysql_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" \
    --user=onlinejudge --database=onlinejudge --execute='SELECT 1' >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 45 ] || { docker logs "$mysql_container" >&2; exit 1; }
    sleep 1
done

attempt=0
until docker exec --user rabbitmq "$rabbit_container" rabbitmq-diagnostics -q ping >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 90 ] || { docker logs "$rabbit_container" >&2; exit 1; }
    sleep 1
done

echo "course-membership-live: mysql=$mysql_container:$mysql_port rabbit=$rabbit_container:$rabbit_port"
ONLINEJUDGE_LIVE_COURSE_ROSTER=true \
    mvn -f "$repository_root/backend/pom.xml" \
    -Dtest=CourseMembershipRabbitMySqlLiveTest \
    -Doj.mysql.host=127.0.0.1 \
    -Doj.mysql.port="$mysql_port" \
    -Doj.mysql.database=onlinejudge \
    -Doj.mysql.username=onlinejudge \
    -Doj.mysql.password="$mysql_password" \
    -Doj.rabbit.host=127.0.0.1 \
    -Doj.rabbit.port="$rabbit_port" test
echo "course-membership-live: PASS mysql=1 rabbit=1"
