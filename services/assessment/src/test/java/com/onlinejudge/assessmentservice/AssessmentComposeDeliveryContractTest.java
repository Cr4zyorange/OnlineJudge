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
        String primaryDockerfile = Files.readString(repository.resolve("services/assessment/Dockerfile"));
        String cachedDockerfile = Files.readString(repository.resolve("services/assessment/Dockerfile.cached-runtime"));

        assertThat(compose).contains("ASSESSMENT_STORAGE_ROOT: /var/lib/onlinejudge-assessment");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_COMMAND: ${ASSESSMENT_SANDBOX_COMMAND:?ASSESSMENT_SANDBOX_COMMAND is required}");
        assertThat(compose).contains("ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY: ${ASSESSMENT_SANDBOX_PRE_EXECUTION_DELAY:-PT0S}");
        assertThat(count(compose, "assessment-files:/var/lib/onlinejudge-assessment")).isEqualTo(2);
        assertThat(compose).contains("volumes:\n  assessment-files:");
        assertThat(primaryDockerfile).contains("/var/lib/onlinejudge-assessment");
        assertThat(cachedDockerfile).contains("/var/lib/onlinejudge-assessment", "USER 10003:10003");
    }

    private int count(String source, String value) {
        return source.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
