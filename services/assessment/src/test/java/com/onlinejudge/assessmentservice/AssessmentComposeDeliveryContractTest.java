package com.onlinejudge.assessmentservice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** API and worker may restart independently, so submission bytes need one mounted durable boundary. */
class AssessmentComposeDeliveryContractTest {
    @Test
    void apiAndWorkerMountTheSameNonRootSubmissionVolume() throws Exception {
        Path repository = Path.of("..", "..").toAbsolutePath().normalize();
        String compose = Files.readString(repository.resolve("deploy/docker/compose.assessment.yml"));
        String cachedCompose = Files.readString(repository.resolve("deploy/docker/compose.assessment.cached-runtime.yml"));
        String primaryDockerfile = Files.readString(repository.resolve("services/assessment/Dockerfile"));
        String cachedDockerfile = Files.readString(repository.resolve("services/assessment/Dockerfile.cached-runtime"));
        String migration = Files.readString(repository.resolve("database/migrations/assessment/20260831_01_create_assessment_service_tables.sql"));
        String dlqUpgradeMigration = Files.readString(repository.resolve("database/migrations/assessment/20260831_03_identity_security_version_dead_letter.sql"));
        String runtimeProperties = Files.readString(repository.resolve("services/assessment/src/main/resources/application-compose.properties"));

        assertThat(compose).contains("ASSESSMENT_STORAGE_ROOT: /var/lib/onlinejudge-assessment");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_COMMAND: ${ASSESSMENT_SANDBOX_COMMAND:?ASSESSMENT_SANDBOX_COMMAND is required}");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY: ${ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY:-PT0S}");
        assertThat(compose).contains("ASSESSMENT_RABBIT_ENABLED: \"true\"", "ASSESSMENT_RABBIT_HOST: rabbitmq");
        String api = compose.substring(compose.indexOf("  assessment-api:"), compose.indexOf("  assessment-worker:"));
        String worker = compose.substring(compose.indexOf("  assessment-worker:"), compose.indexOf("\nvolumes:"));
        assertThat(api).contains("ASSESSMENT_RABBIT_ENABLED: \"false\"", "ASSESSMENT_RABBIT_RELAY_ENABLED: \"false\"", "healthcheck:")
                .doesNotContain("rabbitmq: {condition: service_healthy}");
        assertThat(api).contains("assessment-migrations: {condition: service_completed_successfully}");
        assertThat(api).contains("ASSESSMENT_RABBIT_EXCHANGE: onlinejudge.events.v2");
        assertThat(worker).contains("ASSESSMENT_RABBIT_ENABLED: \"true\"", "ASSESSMENT_RABBIT_RELAY_ENABLED: \"true\"", "ASSESSMENT_RABBIT_EXCHANGE: onlinejudge.events.v2", "healthcheck:", "test -f /tmp/assessment-worker-ready", "retries: 3");
        assertThat(Files.readString(repository.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/worker/AssessmentWorkerReadiness.java")))
                .contains("factory.setConnectionTimeout(1_000)", "Files.deleteIfExists(MARKER)");
        assertThat(Files.readString(repository.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/RabbitOutboxRelay.java")))
                .contains("factory.setConnectionTimeout(1_000)");
        assertThat(compose).contains("ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE: ${ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE:?ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE is required}");
        assertThat(compose).contains("ASSESSMENT_IDENTITY_JWKS_URI: ${ASSESSMENT_IDENTITY_JWKS_URI:?ASSESSMENT_IDENTITY_JWKS_URI is required}");
        assertThat(compose).contains("ASSESSMENT_WORKER_LEASE: ${ASSESSMENT_WORKER_LEASE:-PT30S}", "ASSESSMENT_WORKER_HEARTBEAT_INTERVAL: ${ASSESSMENT_WORKER_HEARTBEAT_INTERVAL:-PT5S}");
        assertThat(compose).contains("ASSESSMENT_WORKER_MAX_ATTEMPTS: ${ASSESSMENT_WORKER_MAX_ATTEMPTS:-3}", "ASSESSMENT_WORKER_RETRY_BACKOFF: ${ASSESSMENT_WORKER_RETRY_BACKOFF:-PT5S}");
        assertThat(worker).contains("rabbitmq: {condition: service_healthy}");
        String broker = compose.substring(compose.indexOf("  rabbitmq:"), compose.indexOf("  assessment-storage-init:"));
        assertThat(broker).contains("RABBITMQ_DEFAULT_USER: ${ASSESSMENT_RABBIT_USERNAME:?ASSESSMENT_RABBIT_USERNAME is required}", "RABBITMQ_DEFAULT_PASS: ${ASSESSMENT_RABBIT_PASSWORD:?ASSESSMENT_RABBIT_PASSWORD is required}")
                .doesNotContain("guest");
        assertThat(worker).contains("ASSESSMENT_RABBIT_USERNAME: ${ASSESSMENT_RABBIT_USERNAME:?ASSESSMENT_RABBIT_USERNAME is required}", "ASSESSMENT_RABBIT_PASSWORD: ${ASSESSMENT_RABBIT_PASSWORD:?ASSESSMENT_RABBIT_PASSWORD is required}")
                .doesNotContain("guest");
        assertThat(worker).contains("assessment-migrations: {condition: service_completed_successfully}");
        assertThat(compose).contains("assessment-runtime-account-init:", "export MYSQL_PWD=\"$$MYSQL_ROOT_PASSWORD\"", "REVOKE ALL PRIVILEGES, GRANT OPTION", "GRANT SELECT, INSERT, UPDATE, DELETE ON oj_assessment.*", "assessment-migrations:", "MIGRATION_DATABASE_USER: root", "migrate-service.sh", "MIGRATION_ROOT: /workspace/migrations");
        assertThat(runtimeProperties).contains("spring.sql.init.mode=never");
        assertThat(compose).contains("assessment-storage-init: {condition: service_completed_successfully}", "user: \"0:0\"", "chown -R 10003:10003 /var/lib/onlinejudge-assessment");
        assertThat(count(compose, "assessment-files:/var/lib/onlinejudge-assessment")).isEqualTo(3);
        assertThat(compose).contains("volumes:\n  assessment-files:");
        assertThat(primaryDockerfile).contains("/var/lib/onlinejudge-assessment");
        assertThat(cachedDockerfile).contains("/var/lib/onlinejudge-assessment", "USER 10003:10003", "getent group 10003");
        assertThat(migration).contains("assessment_deferred_course_member_event");
        assertThat(dlqUpgradeMigration).contains("CREATE TABLE IF NOT EXISTS assessment_identity_security_version_dead_letter");
        assertThat(cachedCompose).contains("assessment-worker:${GIT_SHA:?GIT_SHA is required}");
    }

    private int count(String source, String value) {
        return source.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
