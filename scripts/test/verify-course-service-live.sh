#!/usr/bin/env bash
set -euo pipefail

# #312 disposable acceptance: start from the supported Compose DB-CRS schema,
# prove the old same-name CREATE-only migration is insufficient, then evolve
# those facts in-place before exercising Course API/outbox/security behavior
# against MySQL 8.4 and RabbitMQ 4.1 (never H2 or fixture-only events).
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
run_id="$(date +%s)-$$"
mysql_name="oj312-course-mysql-$run_id"
rabbit_name="oj312-course-rabbit-$run_id"
mysql_password="oj312-live-root"
rabbit_user="oj_course_events"
rabbit_password="oj312-live-rabbit-password"
database_name="course_live"
evidence_dir="$repo_root/ci-artifacts/issue312-course-live-$run_id"
mkdir -p "$evidence_dir"

cleanup() {
  docker logs "$mysql_name" >"$evidence_dir/mysql.log" 2>&1 || true
  docker logs "$rabbit_name" >"$evidence_dir/rabbit.log" 2>&1 || true
  docker rm -f "$mysql_name" "$rabbit_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for_mysql() {
  local attempt
  for attempt in $(seq 1 30); do
    # The image briefly starts an initialization-only Unix-socket server.  A
    # TCP query proves the final server (the one exposed to Course) is ready.
    if docker exec "$mysql_name" mysql -h127.0.0.1 -uroot -p"$mysql_password" -e 'SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "MySQL 8.4 did not become ready" >&2
  return 1
}

wait_for_rabbit() {
  local attempt
  for attempt in $(seq 1 30); do
    if docker exec "$rabbit_name" rabbitmqctl await_startup >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "RabbitMQ 4.1 did not become ready" >&2
  return 1
}

mysql_file() {
  docker exec -i "$mysql_name" mysql -uroot -p"$mysql_password" "$database_name" < "$1"
}

apply_course_migrations() {
  mysql_file "$repo_root/database/migrations/course/V20260831_01__course_service_schema.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_02__course_security_version_inbox.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_03__course_runtime_version_columns.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_04__course_outbox_fencing.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_05__course_file_delete_journal.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_06__course_chapter_active_order_uniqueness.sql"
}

verify_upgrade_red_green_and_repeat() {
  # This is the reviewed production failure shape: the supported Compose
  # baseline already owns DB-CRS-01/02/03/05.  A same-name CREATE IF NOT
  # EXISTS emits only MySQL notes, so no Course runtime columns appear.
  mysql_file "$repo_root/database/mysql/compose-schema.sql"
  docker exec "$mysql_name" mysql -uroot -p"$mysql_password" "$database_name" -e "
    INSERT INTO crs_course (id, course_name, teacher_id, enrollment_mode, status) VALUES (941, 'upgrade course', 9410, 'PUBLIC', 'ACTIVE');
    INSERT INTO crs_course_member (id, course_id, user_id, role, join_method, join_status) VALUES (941, 941, 9410, 'TEACHER', 'CREATED', 'ACTIVE');
    INSERT INTO crs_chapter (id, course_id, chapter_name, sort_order) VALUES (941, 941, 'existing chapter', 1);
    INSERT INTO crs_resource (id, course_id, chapter_id, resource_name, resource_type, storage_key, original_filename, content_type, file_size, upload_user_id) VALUES (941, 941, 941, 'existing resource', 'DOCUMENT', 'legacy/941', 'legacy.pdf', 'application/pdf', 12, 9410);
    INSERT INTO crs_announcement (id, course_id, title, content, publisher_id) VALUES (941, 941, 'existing announcement', 'preserve this fact', 9410);
  "

  mysql_file "$repo_root/database/migrations/course/V20260831_01__course_service_schema.sql"
  mysql_file "$repo_root/database/migrations/course/V20260831_02__course_security_version_inbox.sql"
  if docker exec "$mysql_name" mysql -uroot -p"$mysql_password" "$database_name" \
      -e "SELECT course_name, status, roster_version FROM crs_course WHERE id = 941" \
      >"$evidence_dir/upgrade-red-before-v3.log" 2>&1; then
    echo "upgrade RED mutation unexpectedly resolved without V20260831_03" >&2
    return 1
  fi
  grep -Fq 'Unknown column' "$evidence_dir/upgrade-red-before-v3.log"

  mysql_file "$repo_root/database/migrations/course/V20260831_03__course_runtime_version_columns.sql"
  docker exec "$mysql_name" mysql -N -uroot -p"$mysql_password" "$database_name" -e "
    SELECT CONCAT('upgraded-course=', course_name, ':', roster_version) FROM crs_course WHERE id = 941;
    SELECT CONCAT('upgraded-member=', user_id, ':', member_version) FROM crs_course_member WHERE course_id = 941;
    SELECT CONCAT('upgraded-resource=', resource_name, ':', version, ':', download_count, ':', COALESCE(external_url, 'NULL')) FROM crs_resource WHERE id = 941;
    SELECT CONCAT('preserved-announcement=', title, ':', content) FROM crs_announcement WHERE id = 941;
  " | tee "$evidence_dir/upgrade-green.txt"
  grep -Fqx 'upgraded-course=upgrade course:0' "$evidence_dir/upgrade-green.txt"
  grep -Fqx 'upgraded-member=9410:1' "$evidence_dir/upgrade-green.txt"
  grep -Fqx 'upgraded-resource=existing resource:1:0:NULL' "$evidence_dir/upgrade-green.txt"
  grep -Fqx 'preserved-announcement=existing announcement:preserve this fact' "$evidence_dir/upgrade-green.txt"

  # The cutover rollback is routing traffic back to the legacy reader.  V3 is
  # additive, so it preserves all DB-CRS columns and facts; no down-DDL may
  # discard newly written Course data.  Reapplying the full migration set
  # proves a resumed cutover is safe and does not rewrite the preserved fact.
  docker exec "$mysql_name" mysql -N -uroot -p"$mysql_password" "$database_name" -e "
    SELECT CONCAT('rollback-legacy-read=', course_name, ':', join_status, ':', chapter_name, ':', resource_name)
      FROM crs_course c JOIN crs_course_member m ON m.course_id = c.id
       JOIN crs_chapter ch ON ch.course_id = c.id JOIN crs_resource r ON r.course_id = c.id
     WHERE c.id = 941;
  " | tee "$evidence_dir/rollback-readback.txt"
  grep -Fqx 'rollback-legacy-read=upgrade course:ACTIVE:existing chapter:existing resource' "$evidence_dir/rollback-readback.txt"
  apply_course_migrations
  docker exec "$mysql_name" mysql -N -uroot -p"$mysql_password" "$database_name" -e "
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND (
       (table_name = 'crs_course' AND column_name = 'roster_version') OR
       (table_name = 'crs_course_member' AND column_name = 'member_version') OR
       (table_name = 'crs_resource' AND column_name IN ('external_url', 'version', 'download_count'))
     );
  " | tee "$evidence_dir/remigrate-column-count.txt"
  grep -Fqx '5' "$evidence_dir/remigrate-column-count.txt"
}

mysql_id="$(docker run -d --rm --name "$mysql_name" -e MYSQL_ROOT_PASSWORD="$mysql_password" \
  -e MYSQL_DATABASE="$database_name" -p 127.0.0.1::3306 mysql:8.4)"
rabbit_id="$(docker run -d --rm --name "$rabbit_name" -e RABBITMQ_DEFAULT_USER="$rabbit_user" \
  -e RABBITMQ_DEFAULT_PASS="$rabbit_password" -p 127.0.0.1::5672 rabbitmq:4.1-management)"
wait_for_mysql
wait_for_rabbit

mysql_port="$(docker port "$mysql_name" 3306/tcp | sed -n '1s/.*://p')"
rabbit_port="$(docker port "$rabbit_name" 5672/tcp | sed -n '1s/.*://p')"

verify_upgrade_red_green_and_repeat

{
  printf 'mysql_container=%s image=mysql:8.4 port=%s\n' "$mysql_id" "$mysql_port"
  docker exec "$mysql_name" mysql -N -uroot -p"$mysql_password" "$database_name" -e 'SELECT VERSION()'
  printf 'rabbit_container=%s image=rabbitmq:4.1-management port=%s\n' "$rabbit_id" "$rabbit_port"
  docker exec "$rabbit_name" rabbitmqctl version
  docker exec "$mysql_name" mysql -N -uroot -p"$mysql_password" "$database_name" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('crs_course','crs_course_member','crs_chapter','crs_resource','crs_announcement','course_event_outbox','course_membership_reconciliation_checkpoint','event_inbox')"
} | tee "$evidence_dir/runtime.txt"

java_home="${OJ312_JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
[[ -x "$java_home/bin/java" ]] || { echo "OJ312_JAVA_HOME must point to Java 21" >&2; exit 1; }

COURSE_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$mysql_port/$database_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
COURSE_DATABASE_USER=root \
COURSE_DATABASE_PASSWORD="$mysql_password" \
COURSE_DATABASE_DRIVER=com.mysql.cj.jdbc.Driver \
SPRING_SQL_INIT_MODE=never \
RABBITMQ_USER="$rabbit_user" \
RABBITMQ_PASSWORD="$rabbit_password" \
JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
mvn -B -ntp -f "$repo_root/services/course/pom.xml" \
  -Dcourse.test.rabbit=true \
  -Dcourse.test.mysql=true \
  -Dcourse.test.rabbit.port="$rabbit_port" \
  -Dtest=CourseServiceContractTest,CourseSecurityVersionProjectionTest,CourseOutboxRelayRecoveryTest,IdentitySecurityVersionRabbitConsumerTest,CourseOutboxLeaseTest,CourseGeneratedKeyMySqlConcurrencyTest,CourseCapacityConcurrencyMySqlTest,JwksCacheTest \
  test | tee "$evidence_dir/course-service-live.log"

test_total="$(bash "$repo_root/scripts/test/course-surefire-summary.sh" "$evidence_dir/course-service-live.log")"
printf 'issue312-course-live: PASS mysql=8.4 rabbit=4.1 upgrade=RED-GREEN-rollback-remigrate tables=8 tests=%s evidence=%s\n' "$test_total" "$evidence_dir"
