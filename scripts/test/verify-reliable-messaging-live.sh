#!/usr/bin/env sh

# #337 disposable RabbitMQ proof. It intentionally owns only the temporary
# broker it creates and emits event/correlation IDs in the raw Maven log.
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
container_name="oj-reliability-337-$$"

cleanup() {
    docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 69; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; exit 69; }

docker run --detach --name "$container_name" \
    --publish 127.0.0.1::5672 \
    rabbitmq:4.1-management >/dev/null

attempt=0
# The official 4.1 image creates its Erlang cookie mode 0400 for the
# `rabbitmq` user.  `docker exec` defaults to root, which cannot read that
# cookie and therefore makes a healthy broker look unavailable.
until docker exec --user rabbitmq "$container_name" rabbitmq-diagnostics -q ping >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then
        docker logs "$container_name" >&2 || true
        echo "RabbitMQ did not become ready" >&2
        exit 1
    fi
    sleep 1
done

endpoint=$(docker port "$container_name" 5672/tcp | head -n 1)
rabbit_port=${endpoint##*:}
case "$rabbit_port" in
    ''|*[!0-9]*) echo "unable to resolve disposable RabbitMQ port: $endpoint" >&2; exit 1 ;;
esac

run_live_test() {
    expected=$1
    ONLINEJUDGE_LIVE_RABBITMQ=true \
        mvn -f "$repository_root/backend/pom.xml" \
        -Dtest=RabbitMqConfirmedPublisherLiveTest \
        -Doj.rabbit.host=127.0.0.1 \
        -Doj.rabbit.port="$rabbit_port" \
        -Doj.rabbit.expected="$expected" test
}

echo "reliable-messaging-live: broker ready container=$container_name port=$rabbit_port"
run_live_test available

docker pause "$container_name" >/dev/null
echo "reliable-messaging-live: injected broker pause"
run_live_test unavailable

docker unpause "$container_name" >/dev/null
echo "reliable-messaging-live: broker recovered"
run_live_test available

echo "reliable-messaging-live: PASS confirmed=2 unavailable=1"
