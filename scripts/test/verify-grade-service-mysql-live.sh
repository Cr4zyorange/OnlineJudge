#!/usr/bin/env bash
set -euo pipefail

# AC-339-01: prove the packaged Grade service starts with its production
# defaults after versioned migrations are applied to a fresh MySQL database.
# Deliberately do not set SPRING_SQL_INIT_MODE: application.yml must keep the
# H2 bootstrap out of the MySQL runtime by itself.
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
run_id="$(date +%s)-$$"
mysql_name="oj339-grade-mysql-$run_id"
mysql_root_password="oj339-live-root"
grade_password="oj339-live-grade"
database_name="oj_grade"
http_port="${OJ339_GRADE_HTTP_PORT:-18084}"
evidence_dir="$repo_root/ci-artifacts/issue339-grade-mysql-live-$run_id"
app_log="$evidence_dir/grade-service.log"
mysql_log="$evidence_dir/mysql.log"
app_pid=""
mkdir -p "$evidence_dir"

cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" >/dev/null 2>&1; then
    kill "$app_pid" >/dev/null 2>&1 || true
    wait "$app_pid" >/dev/null 2>&1 || true
  fi
  docker logs "$mysql_name" >"$mysql_log" 2>&1 || true
  docker rm -f "$mysql_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { echo "grade-mysql-live: docker is required" >&2; exit 69; }
docker info >/dev/null 2>&1 || { echo "grade-mysql-live: Docker daemon is unavailable" >&2; exit 69; }
command -v curl >/dev/null 2>&1 || { echo "grade-mysql-live: curl is required" >&2; exit 69; }

docker run --detach --rm --name "$mysql_name" \
  --env MYSQL_ROOT_PASSWORD="$mysql_root_password" \
  --env MYSQL_DATABASE="$database_name" \
  --publish 127.0.0.1::3306 \
  mysql:8.4 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci >/dev/null

for attempt in $(seq 1 90); do
  if docker exec "$mysql_name" mysql \
      --protocol=tcp --host=127.0.0.1 --user=root --password="$mysql_root_password" \
      --execute='SELECT 1' >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 90 ]]; then
    echo "grade-mysql-live: MySQL 8.4 did not become ready" >&2
    exit 1
  fi
  sleep 1
done

mysql_file() {
  docker exec -i "$mysql_name" mysql \
    --protocol=tcp --host=127.0.0.1 --user=root --password="$mysql_root_password" "$database_name" < "$1"
}

mysql_file "$repo_root/database/migrations/grade/V20260901_01__grade_service_schema.sql"
mysql_file "$repo_root/database/migrations/grade/V20260901_02__complete_grade_runtime.sql"
mysql_file "$repo_root/database/migrations/grade/V20260902_03__drop_legacy_grade_source_projection_status.sql"
docker exec "$mysql_name" mysql \
  --protocol=tcp --host=127.0.0.1 --user=root --password="$mysql_root_password" \
  --execute="CREATE USER 'oj_grade_rw'@'%' IDENTIFIED BY '$grade_password'; GRANT SELECT, INSERT, UPDATE, DELETE ON oj_grade.* TO 'oj_grade_rw'@'%'; FLUSH PRIVILEGES;"

table_count="$(docker exec "$mysql_name" mysql --batch --skip-column-names \
  --protocol=tcp --host=127.0.0.1 --user=root --password="$mysql_root_password" "$database_name" \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();")"
[[ "$table_count" == "18" ]] || {
  echo "grade-mysql-live: expected 18 migrated tables, got $table_count" >&2
  exit 1
}

mysql_port="$(docker port "$mysql_name" 3306/tcp | sed -n '1s/.*://p')"
[[ -n "$mysql_port" ]] || { echo "grade-mysql-live: cannot resolve MySQL host port" >&2; exit 1; }
jar="$repo_root/services/grade/target/onlinejudge-grade-service-0.1.0-SNAPSHOT.jar"
[[ -f "$jar" ]] || { echo "grade-mysql-live: missing packaged JAR $jar" >&2; exit 1; }

GRADE_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$mysql_port/$database_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
GRADE_DATABASE_USER=oj_grade_rw \
GRADE_DATABASE_PASSWORD="$grade_password" \
GRADE_DATABASE_DRIVER=com.mysql.cj.jdbc.Driver \
GRADE_HTTP_PORT="$http_port" \
GRADE_RABBIT_ENABLED=false \
java -jar "$jar" >"$app_log" 2>&1 &
app_pid=$!

for attempt in $(seq 1 90); do
  if ! kill -0 "$app_pid" >/dev/null 2>&1; then
    echo "grade-mysql-live: Grade exited before readiness" >&2
    tail -100 "$app_log" >&2 || true
    exit 1
  fi
  if curl --fail --silent "http://127.0.0.1:$http_port/actuator/health/readiness" \
      >"$evidence_dir/actuator-readiness.json"; then
    break
  fi
  if [[ "$attempt" -eq 90 ]]; then
    echo "grade-mysql-live: actuator readiness timed out" >&2
    tail -100 "$app_log" >&2 || true
    exit 1
  fi
  sleep 1
done

curl --fail --silent "http://127.0.0.1:$http_port/health/ready" \
  >"$evidence_dir/service-readiness.json"
grep -Fq '"status":"UP"' "$evidence_dir/actuator-readiness.json"
grep -Fq '"status":"UP"' "$evidence_dir/service-readiness.json"

printf 'grade-mysql-live: PASS mysql=8.4 migrations=V01+V02+V03 tables=%s user=oj_grade_rw readiness=2/2 evidence=%s\n' \
  "$table_count" "$evidence_dir"
