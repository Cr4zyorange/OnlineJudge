package com.onlinejudge.common.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DockerSandboxExecutorTest {
    private static final String DOCKER_API_UNAVAILABLE_ERROR =
            "failed to connect to the docker API at unix:///var/run/docker.sock; "
                    + "check if the path is correct and if the daemon is running: "
                    + "dial unix /var/run/docker.sock: connect: no such file or directory";

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

    @Test
    void dockerExecutorClassifiesRealAcceptanceFailureLimitsAndCleanupWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("OJ_DOCKER_SANDBOX_TEST")));
        Assumptions.assumeTrue(dockerDaemonAvailable());

        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                "docker",
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        assertThat(executeRealCase(executor, "accepted", "print('ok')", 30_000, 65_536).status())
                .isEqualTo(EvaluationStatus.ACCEPTED);
        assertThat(executeRealCase(executor, "compile", "print(", 30_000, 65_536).status())
                .isEqualTo(EvaluationStatus.COMPILE_ERROR);
        assertThat(executeRealCase(executor, "runtime", "raise RuntimeError('issue-265')", 30_000, 65_536).status())
                .isEqualTo(EvaluationStatus.RUNTIME_ERROR);
        assertThat(executeRealCase(executor, "timeout", "while True: pass", 2_000, 65_536).status())
                .isEqualTo(EvaluationStatus.TIME_LIMIT_EXCEEDED);
        assertThat(executeRealCase(
                executor,
                "memory",
                "chunks = []\nwhile True:\n    chunks.append(bytearray(1024 * 1024))",
                30_000,
                65_536
        ).status()).isEqualTo(EvaluationStatus.RUNTIME_ERROR);

        assertNoSandboxContainersRemain();
    }

    @Test
    void unavailableDockerDaemonIsReportedAsSystemError(@TempDir Path tempDir) throws Exception {
        Path dockerCommand = createDaemonUnavailableCommand(tempDir);
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                dockerCommand.toString(),
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        SandboxExecutionResult result = executor.execute(new EvaluationTask(
                "daemon-unavailable",
                "LAB",
                1L,
                1L,
                1L,
                1L,
                "python",
                "print('valid python')",
                Map.of("stdin", "", "timeLimitMs", "3000", "memoryLimitKb", "65536"),
                LocalDateTime.now()
        ));

        assertThat(result.status()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
        assertThat(result.message()).isEqualTo("Docker 沙箱不可用");
        assertThat(result.runLog()).contains("failed to connect to the docker API");
    }

    @Test
    void unavailableDockerDaemonDuringRunIsReportedAsSystemError(@TempDir Path tempDir) throws Exception {
        Path dockerCommand = createRunPhaseDaemonUnavailableCommand(tempDir);
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                dockerCommand.toString(),
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        SandboxExecutionResult result = executor.execute(new EvaluationTask(
                "daemon-unavailable-during-run",
                "LAB",
                1L,
                1L,
                1L,
                1L,
                "python",
                "print('valid python')",
                Map.of("stdin", "", "timeLimitMs", "3000", "memoryLimitKb", "65536"),
                LocalDateTime.now()
        ));

        assertThat(result.status()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
        assertThat(result.message()).isEqualTo("Docker 沙箱不可用");
        assertThat(result.runLog()).contains("failed to connect to the docker API");
    }

    @Test
    void dockerApiRelayDisconnectDuringRealRunIsReportedAsSystemErrorAndLeavesNoSandboxContainers(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("OJ_DOCKER_SANDBOX_TEST")));
        Assumptions.assumeTrue(dockerDaemonAvailable());

        try (DockerApiRelay relay = startDockerApiRelay()) {
            DockerSandboxExecutor executor = new DockerSandboxExecutor(
                    relay.createDisconnectingDockerCommand(tempDir).toString(),
                    "python:3.12-alpine",
                    1.0,
                    64,
                    "16m"
            );

            SandboxExecutionResult result = executeRealCase(
                    executor,
                    "relay-disconnect",
                    "print('valid python')",
                    30_000,
                    65_536
            );

            assertThat(result.status()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
            assertThat(result.message()).isEqualTo("Docker 沙箱不可用");
            assertThat(result.runLog()).contains("error during connect");
            assertNoSandboxContainersRemain();
        }
    }

    @Test
    void ordinaryCompilerFailureRemainsCompileError(@TempDir Path tempDir) throws Exception {
        Path dockerCommand = createFailingDockerCommand(
                tempDir,
                "syntax-error.cmd",
                "SyntaxError: invalid syntax"
        );
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                dockerCommand.toString(),
                "python:3.12-alpine",
                1.0,
                64,
                "16m"
        );

        SandboxExecutionResult result = executor.execute(new EvaluationTask(
                "syntax-error",
                "LAB",
                1L,
                1L,
                1L,
                1L,
                "python",
                "print('valid python')",
                Map.of("stdin", "", "timeLimitMs", "3000", "memoryLimitKb", "65536"),
                LocalDateTime.now()
        ));

        assertThat(result.status()).isEqualTo(EvaluationStatus.COMPILE_ERROR);
        assertThat(result.compileLog()).contains("SyntaxError: invalid syntax");
    }

    private boolean dockerDaemonAvailable() throws Exception {
        Process process = new ProcessBuilder("docker", "info").start();
        return process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
    }

    private SandboxExecutionResult executeRealCase(
            DockerSandboxExecutor executor,
            String caseName,
            String source,
            int timeLimitMs,
            int memoryLimitKb
    ) {
        return executor.execute(new EvaluationTask(
                "issue-265-" + caseName,
                "LAB",
                1L,
                1L,
                1L,
                1L,
                "python",
                source,
                Map.of("stdin", "", "timeLimitMs", Integer.toString(timeLimitMs), "memoryLimitKb", Integer.toString(memoryLimitKb)),
                LocalDateTime.now()
        ));
    }

    private void assertNoSandboxContainersRemain() throws Exception {
        Process process = new ProcessBuilder(
                "docker", "ps", "-a", "--format", "{{.Names}}", "--filter", "name=oj-lab-"
        ).start();
        String names = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(names.trim()).isEmpty();
    }

    private Path createDaemonUnavailableCommand(Path tempDir) throws Exception {
        return createFailingDockerCommand(
                tempDir,
                "docker-daemon-unavailable.cmd",
                DOCKER_API_UNAVAILABLE_ERROR
        );
    }

    private Path createRunPhaseDaemonUnavailableCommand(Path tempDir) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path command = tempDir.resolve(windows ? "run-phase-daemon-unavailable.cmd" : "run-phase-daemon-unavailable.sh");
        String errorMessage = DOCKER_API_UNAVAILABLE_ERROR;
        String script = windows
                ? "@echo off\r\nif exist \"%~dp0compile-succeeded\" goto daemonUnavailable\r\necho compiled>\"%~dp0compile-succeeded\"\r\nexit /b 0\r\n:daemonUnavailable\r\necho "
                + errorMessage + " 1>&2\r\nexit /b 1\r\n"
                : "#!/bin/sh\nscript_dir=$(dirname \"$0\")\nif [ -f \"$script_dir/compile-succeeded\" ]; then\n  echo '"
                + errorMessage + "' >&2\n  exit 1\nfi\ntouch \"$script_dir/compile-succeeded\"\nexit 0\n";
        Files.writeString(command, script, StandardCharsets.UTF_8);
        if (!windows) {
            Files.setPosixFilePermissions(command, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        }
        return command;
    }

    private Path createFailingDockerCommand(Path tempDir, String fileName, String errorMessage) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String normalizedFileName = windows ? fileName : fileName.replace(".cmd", ".sh");
        Path command = tempDir.resolve(normalizedFileName);
        String script = windows
                ? "@echo off\r\necho " + errorMessage + " 1>&2\r\nexit /b 1\r\n"
                : "#!/bin/sh\necho '" + errorMessage + "' >&2\nexit 1\n";
        Files.writeString(command, script, StandardCharsets.UTF_8);
        if (!windows) {
            Files.setPosixFilePermissions(command, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        }
        return command;
    }

    private DockerApiRelay startDockerApiRelay() throws Exception {
        return DockerApiRelay.start();
    }

    private record ProcessOutput(int exitCode, String output) {
    }

    private static final class DockerApiRelay implements AutoCloseable {
        private static final String IMAGE = "alpine/socat:1.8.0.3";

        private final String containerName;
        private final String endpoint;

        private DockerApiRelay(String containerName, String endpoint) {
            this.containerName = containerName;
            this.endpoint = endpoint;
        }

        static DockerApiRelay start() throws Exception {
            String containerName = "oj265-docker-relay-" + UUID.randomUUID().toString().substring(0, 12);
            ProcessOutput started = runCommand(
                    "docker", "run", "--detach", "--rm", "--name", containerName,
                    "--read-only",
                    "--tmpfs", "/tmp:rw,nosuid,size=1m",
                    "-v", "/var/run/docker.sock:/var/run/docker.sock",
                    "-p", "127.0.0.1::2375",
                    IMAGE,
                    "TCP-LISTEN:2375,fork,reuseaddr",
                    "UNIX-CONNECT:/var/run/docker.sock"
            );
            if (started.exitCode() != 0) {
                throw new IllegalStateException("could not start Docker API relay: " + started.output());
            }

            try {
                ProcessOutput port = runCommand("docker", "port", containerName, "2375/tcp");
                if (port.exitCode() != 0) {
                    throw new IllegalStateException("could not resolve Docker API relay port: " + port.output());
                }
                String endpoint = port.output().lines()
                        .map(String::trim)
                        .filter(value -> value.startsWith("127.0.0.1:"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Docker API relay did not publish a loopback port: " + port.output()));
                DockerApiRelay relay = new DockerApiRelay(containerName, endpoint);
                relay.awaitReady();
                return relay;
            } catch (Exception exception) {
                runCommand("docker", "rm", "-f", containerName);
                throw exception;
            }
        }

        Path createDisconnectingDockerCommand(Path tempDir) throws Exception {
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            Path command = tempDir.resolve(windows ? "relay-disconnect.cmd" : "relay-disconnect.sh");
            Path compileMarker = tempDir.resolve("relay-compile-completed");
            String script = windows
                    ? "@echo off\r\n"
                    + "setlocal\r\n"
                    + "if exist \"%~dp0" + compileMarker.getFileName() + "\" goto disconnect\r\n"
                    + "docker --host tcp://" + endpoint + " %*\r\n"
                    + "set \"exit_code=%ERRORLEVEL%\"\r\n"
                    + "if not \"%exit_code%\"==\"0\" exit /b %exit_code%\r\n"
                    + "type nul > \"%~dp0" + compileMarker.getFileName() + "\"\r\n"
                    + "exit /b 0\r\n"
                    + ":disconnect\r\n"
                    + "docker rm -f " + containerName + " >nul 2>&1\r\n"
                    + "docker --host tcp://" + endpoint + " %*\r\n"
                    + "exit /b %ERRORLEVEL%\r\n"
                    : "#!/bin/sh\n"
                    + "marker=\"$(dirname \"$0\")/" + compileMarker.getFileName() + "\"\n"
                    + "if [ -f \"$marker\" ]; then\n"
                    + "  docker rm -f \"" + containerName + "\" >/dev/null 2>&1\n"
                    + "  exec docker --host tcp://" + endpoint + " \"$@\"\n"
                    + "fi\n"
                    + "docker --host tcp://" + endpoint + " \"$@\"\n"
                    + "exit_code=$?\n"
                    + "if [ \"$exit_code\" -eq 0 ]; then touch \"$marker\"; fi\n"
                    + "exit \"$exit_code\"\n";
            Files.writeString(command, script, StandardCharsets.UTF_8);
            if (!windows) {
                Files.setPosixFilePermissions(command, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));
            }
            return command;
        }

        private void awaitReady() throws Exception {
            ProcessOutput lastAttempt = null;
            for (int attempt = 0; attempt < 10; attempt++) {
                lastAttempt = runCommand("docker", "--host", "tcp://" + endpoint, "version", "--format", "{{.Server.Version}}");
                if (lastAttempt.exitCode() == 0) {
                    return;
                }
                Thread.sleep(250);
            }
            throw new IllegalStateException("Docker API relay did not become ready: " + lastAttempt.output());
        }

        @Override
        public void close() throws Exception {
            runCommand("docker", "rm", "-f", containerName);
        }

        private static ProcessOutput runCommand(String... command) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("timed out running command: " + String.join(" ", command));
            }
            return new ProcessOutput(
                    process.exitValue(),
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            );
        }
    }
}
