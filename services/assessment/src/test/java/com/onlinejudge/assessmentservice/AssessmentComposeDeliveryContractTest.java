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

        assertThat(compose).contains("ASSESSMENT_STORAGE_ROOT: /var/lib/onlinejudge-assessment");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_COMMAND: ${ASSESSMENT_SANDBOX_COMMAND:?ASSESSMENT_SANDBOX_COMMAND is required}");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY: ${ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY:-PT0S}");
        assertThat(compose).contains("ASSESSMENT_RABBIT_ENABLED: \"true\"", "ASSESSMENT_RABBIT_HOST: rabbitmq");
        assertThat(compose).contains("ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE: ${ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE:?ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE is required}");
        assertThat(compose).contains("ASSESSMENT_IDENTITY_JWKS_URI: ${ASSESSMENT_IDENTITY_JWKS_URI:?ASSESSMENT_IDENTITY_JWKS_URI is required}");
        assertThat(compose).contains("ASSESSMENT_WORKER_LEASE: ${ASSESSMENT_WORKER_LEASE:-PT30S}", "ASSESSMENT_WORKER_HEARTBEAT_INTERVAL: ${ASSESSMENT_WORKER_HEARTBEAT_INTERVAL:-PT5S}");
        assertThat(compose).contains("rabbitmq: {condition: service_healthy}");
        assertThat(count(compose, "assessment-files:/var/lib/onlinejudge-assessment")).isEqualTo(2);
        assertThat(compose).contains("volumes:\n  assessment-files:");
        assertThat(primaryDockerfile).contains("/var/lib/onlinejudge-assessment");
        assertThat(cachedDockerfile).contains("/var/lib/onlinejudge-assessment", "USER 10003:10003");
        assertThat(migration).contains("assessment_deferred_course_member_event");
        assertThat(cachedCompose).contains("assessment-worker:${GIT_SHA:?GIT_SHA is required}");
    }

    private int count(String source, String value) {
        return source.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
