package com.onlinejudge.assessmentservice.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Requires the disposable socket proxy started by verify-assessment-docker-sandbox.sh. */
@EnabledIfSystemProperty(named = "assessment.docker-sandbox.test", matches = "true")
class DockerSandboxClientIntegrationTest {
    @Test
    void executesUntrustedPythonWithTheDockerSandboxApi() {
        DockerSandboxClient sandbox = new DockerSandboxClient(System.getProperty("assessment.docker-sandbox.api"), "python:3.12-alpine");

        DockerSandboxClient.Result result = sandbox.evaluate("python", "print(input())".getBytes(StandardCharsets.UTF_8), "isolated\n", 2_000, 65_536);

        assertThat(result.status()).as(result.output()).isNull();
        assertThat(result.output()).isEqualTo("isolated\n");
    }

    @Test
    void killsAndReportsAProgramThatExceedsItsTimeLimit() {
        DockerSandboxClient sandbox = new DockerSandboxClient(System.getProperty("assessment.docker-sandbox.api"), "python:3.12-alpine");

        DockerSandboxClient.Result result = sandbox.evaluate("python", "while True: pass".getBytes(StandardCharsets.UTF_8), "", 150, 65_536);

        assertThat(result.status()).isEqualTo("SANDBOX_TIMEOUT");
    }
}
