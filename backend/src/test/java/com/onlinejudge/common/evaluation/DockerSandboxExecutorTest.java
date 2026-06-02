package com.onlinejudge.common.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DockerSandboxExecutorTest {
    @Test
    void dockerCommandUsesContainerIsolationAndResourceLimits() {
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                "docker",
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        var command = executor.buildDockerRunCommand(
                "oj-lab-test",
                Path.of("C:/tmp/oj-lab-test"),
                65536,
                "python",
                "-c",
                "import pathlib; source = pathlib.Path('Main.py').read_text(encoding='utf-8'); compile(source, 'Main.py', 'exec')"
        );

        assertThat(command).contains("docker", "run", "--rm", "--network", "none");
        assertThat(command).contains("--memory", "65536k", "--cpus", "1.0", "--pids-limit", "64");
        assertThat(command).contains("--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges");
        assertThat(command).contains("--tmpfs", "/tmp:rw,nosuid,size=16m");
        assertThat(command).contains("-w", "/work", "python:3.12-alpine", "python", "-c");
        assertThat(command).anyMatch(argument -> argument.contains("compile(source, 'Main.py', 'exec')"));
        assertThat(command).anyMatch(argument -> argument.endsWith(":/work:ro"));
    }

    @Test
    void dockerExecutorRunsPythonInContainerWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("OJ_DOCKER_SANDBOX_TEST")));
        Assumptions.assumeTrue(dockerDaemonAvailable());

        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                "docker",
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        SandboxExecutionResult result = executor.execute(new EvaluationTask(
                "docker-smoke",
                "LAB",
                1L,
                1L,
                1L,
                1L,
                "python",
                "left, right = map(int, input().split())\nprint(left + right)",
                Map.of("stdin", "2 3", "timeLimitMs", "3000", "memoryLimitKb", "65536"),
                LocalDateTime.now()
        ));

        assertThat(result.status()).isEqualTo(EvaluationStatus.ACCEPTED);
        assertThat(result.actualOutput().trim()).isEqualTo("5");
    }

    private boolean dockerDaemonAvailable() throws Exception {
        Process process = new ProcessBuilder("docker", "info").start();
        return process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
    }
}
