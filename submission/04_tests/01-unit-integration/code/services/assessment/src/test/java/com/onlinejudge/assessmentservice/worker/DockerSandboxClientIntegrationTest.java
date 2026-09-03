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
        DockerSandboxClient sandbox = sandbox();

        DockerSandboxClient.Result result = sandbox.evaluate("python", "print(input())".getBytes(StandardCharsets.UTF_8), "isolated\n", 2_000, 65_536);

        assertThat(result.status()).as(result.output()).isNull();
        assertThat(result.output()).isEqualTo("isolated\n");
    }

    @Test
    void killsAndReportsAProgramThatExceedsItsTimeLimit() {
        DockerSandboxClient sandbox = sandbox();

        DockerSandboxClient.Result result = sandbox.evaluate("python", "while True: pass".getBytes(StandardCharsets.UTF_8), "", 150, 65_536);

        assertThat(result.status()).isEqualTo("TIME_LIMIT_EXCEEDED");
    }

    @Test
    void executesJavaAndCppUsingTheirConfiguredDockerRuntimes() {
        DockerSandboxClient sandbox = sandbox();

        DockerSandboxClient.Result java = sandbox.evaluate("java", "public class Main { public static void main(String[] args) { System.out.println(\"java\"); } }".getBytes(StandardCharsets.UTF_8), "", 4_000, 131_072);
        DockerSandboxClient.Result cpp = sandbox.evaluate("cpp", "#include <iostream>\nint main() { std::cout << \"cpp\\n\"; }".getBytes(StandardCharsets.UTF_8), "", 4_000, 131_072);

        assertThat(java.status()).as(java.output()).isNull();
        assertThat(java.output()).isEqualTo("java\n");
        assertThat(cpp.status()).as(cpp.output()).isNull();
        assertThat(cpp.output()).isEqualTo("cpp\n");
    }

    @Test
    void mapsCompileRuntimeAndSandboxFailuresToPublicEvaluationStatuses() {
        DockerSandboxClient sandbox = sandbox();

        DockerSandboxClient.Result compile = sandbox.evaluate("python", "def broken(:\n".getBytes(StandardCharsets.UTF_8), "", 2_000, 65_536);
        DockerSandboxClient.Result runtime = sandbox.evaluate("python", "raise RuntimeError('boom')".getBytes(StandardCharsets.UTF_8), "", 2_000, 65_536);
        DockerSandboxClient.Result unavailable = new DockerSandboxClient("", "python:3.12-alpine", "eclipse-temurin:21-jdk-alpine", "gcc:14.2.0")
                .evaluate("python", "print(1)".getBytes(StandardCharsets.UTF_8), "", 2_000, 65_536);

        assertThat(compile.status()).isEqualTo("COMPILE_ERROR");
        assertThat(runtime.status()).isEqualTo("RUNTIME_ERROR");
        assertThat(unavailable.status()).isEqualTo("SYSTEM_ERROR");
    }

    private static DockerSandboxClient sandbox() {
        return new DockerSandboxClient(System.getProperty("assessment.docker-sandbox.api"), "python:3.12-alpine", "eclipse-temurin:21-jdk-alpine", "gcc:14.2.0");
    }
}
