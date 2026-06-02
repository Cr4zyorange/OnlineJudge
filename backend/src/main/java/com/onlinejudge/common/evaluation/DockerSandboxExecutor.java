package com.onlinejudge.common.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "onlinejudge.evaluation.sandbox", name = "mode", havingValue = "docker", matchIfMissing = true)
public class DockerSandboxExecutor implements SandboxExecutor {
    private static final String SOURCE_FILE_NAME = "Main.py";
    private static final String PYTHON_SYNTAX_CHECK = "import pathlib; source = pathlib.Path('Main.py').read_text(encoding='utf-8'); compile(source, 'Main.py', 'exec')";
    private static final int DEFAULT_TIME_LIMIT_MS = 60_000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 262_144;

    private final String dockerCommand;
    private final String pythonImage;
    private final double cpuLimit;
    private final int pidsLimit;
    private final String tmpfsSize;

    public DockerSandboxExecutor(
            @Value("${onlinejudge.evaluation.docker.command:docker}") String dockerCommand,
            @Value("${onlinejudge.evaluation.docker.python-image:python:3.12-alpine}") String pythonImage,
            @Value("${onlinejudge.evaluation.docker.cpu-limit:1.0}") double cpuLimit,
            @Value("${onlinejudge.evaluation.docker.pids-limit:64}") int pidsLimit,
            @Value("${onlinejudge.evaluation.docker.tmpfs-size:16m}") String tmpfsSize
    ) {
        this.dockerCommand = dockerCommand;
        this.pythonImage = pythonImage;
        this.cpuLimit = cpuLimit;
        this.pidsLimit = pidsLimit;
        this.tmpfsSize = tmpfsSize;
    }

    @Override
    public SandboxExecutionResult execute(EvaluationTask task) {
        if (!"python".equalsIgnoreCase(task.language())) {
            return systemError("当前 Docker 沙箱仅支持 Python 语言", null);
        }

        int timeLimitMs = parseInt(task.options().get("timeLimitMs"), DEFAULT_TIME_LIMIT_MS);
        int memoryLimitKb = parseInt(task.options().get("memoryLimitKb"), DEFAULT_MEMORY_LIMIT_KB);
        String stdin = task.options().getOrDefault("stdin", "");
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("oj-lab-");
            Files.writeString(workDir.resolve(SOURCE_FILE_NAME), task.sourceCode() == null ? "" : task.sourceCode(), StandardCharsets.UTF_8);

            ProcessResult compileResult = runContainer(
                    containerName(task.taskId(), "compile"),
                    workDir,
                    memoryLimitKb,
                    "",
                    Math.max(1_000, Math.min(timeLimitMs, 10_000)),
                    "python",
                    "-c",
                    PYTHON_SYNTAX_CHECK
            );
            if (compileResult.timedOut()) {
                return new SandboxExecutionResult(
                        EvaluationStatus.TIME_LIMIT_EXCEEDED,
                        "",
                        "程序运行超时",
                        compileResult.timeUsedMs(),
                        memoryLimitKb,
                        compileResult.stderr(),
                        compileResult.stderr()
                );
            }
            if (compileResult.exitCode() != 0) {
                return new SandboxExecutionResult(
                        EvaluationStatus.COMPILE_ERROR,
                        "",
                        "编译失败",
                        compileResult.timeUsedMs(),
                        memoryLimitKb,
                        compileResult.stderr(),
                        null
                );
            }

            ProcessResult runResult = runContainer(
                    containerName(task.taskId(), "run"),
                    workDir,
                    memoryLimitKb,
                    stdin,
                    timeLimitMs,
                    "python",
                    SOURCE_FILE_NAME
            );
            if (runResult.timedOut()) {
                return new SandboxExecutionResult(
                        EvaluationStatus.TIME_LIMIT_EXCEEDED,
                        runResult.stdout(),
                        "程序运行超时",
                        runResult.timeUsedMs(),
                        memoryLimitKb,
                        null,
                        runResult.stderr()
                );
            }
            if (runResult.exitCode() != 0) {
                return new SandboxExecutionResult(
                        EvaluationStatus.RUNTIME_ERROR,
                        runResult.stdout(),
                        "运行时异常",
                        runResult.timeUsedMs(),
                        memoryLimitKb,
                        null,
                        runResult.stderr()
                );
            }
            return new SandboxExecutionResult(
                    EvaluationStatus.ACCEPTED,
                    runResult.stdout(),
                    "执行完成",
                    runResult.timeUsedMs(),
                    memoryLimitKb,
                    null,
                    runResult.stderr()
            );
        } catch (IOException exception) {
            return systemError("Docker 沙箱不可用", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return systemError("评测任务被中断", exception.getMessage());
        } finally {
            deleteQuietly(workDir);
        }
    }

    List<String> buildDockerRunCommand(
            String containerName,
            Path workDir,
            int memoryLimitKb,
            String... containerCommand
    ) {
        List<String> command = new ArrayList<>();
        command.add(dockerCommand);
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);
        command.add("-i");
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(memoryLimitKb + "k");
        command.add("--cpus");
        command.add(Double.toString(cpuLimit));
        command.add("--pids-limit");
        command.add(Integer.toString(pidsLimit));
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,nosuid,size=" + tmpfsSize);
        command.add("--cap-drop");
        command.add("ALL");
        command.add("--security-opt");
        command.add("no-new-privileges");
        command.add("-v");
        command.add(workDir.toAbsolutePath() + ":/work:ro");
        command.add("-w");
        command.add("/work");
        command.add(pythonImage);
        command.addAll(List.of(containerCommand));
        return command;
    }

    private ProcessResult runContainer(
            String containerName,
            Path workDir,
            int memoryLimitKb,
            String stdin,
            int timeLimitMs,
            String... containerCommand
    ) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        Process process = new ProcessBuilder(buildDockerRunCommand(containerName, workDir, memoryLimitKb, containerCommand))
                .start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
        int timeUsedMs = Math.toIntExact(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        if (!finished) {
            process.destroyForcibly();
            forceRemoveContainer(containerName);
            return new ProcessResult(-1, getNow(stdout), getNow(stderr), true, timeUsedMs);
        }
        return new ProcessResult(process.exitValue(), stdout.join(), stderr.join(), false, timeUsedMs);
    }

    private void forceRemoveContainer(String containerName) {
        try {
            new ProcessBuilder(dockerCommand, "rm", "-f", containerName).start().waitFor(2, TimeUnit.SECONDS);
        } catch (IOException exception) {
            // Cleanup is best effort; the run result has already been classified.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String getNow(CompletableFuture<String> output) {
        return output.isDone() ? output.join() : "";
    }

    private String readAll(java.io.InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private SandboxExecutionResult systemError(String message, String log) {
        return new SandboxExecutionResult(EvaluationStatus.SYSTEM_ERROR, "", message, null, null, null, log);
    }

    private String containerName(String taskId, String phase) {
        String safeTaskId = taskId == null ? "task" : taskId.replaceAll("[^a-zA-Z0-9_.-]", "-");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String raw = "oj-lab-" + phase + "-" + safeTaskId + "-" + suffix;
        return raw.length() <= 63 ? raw : raw.substring(0, 63);
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void deleteQuietly(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary source directories are deleted best effort.
                }
            });
        } catch (IOException ignored) {
            // Temporary source directories are deleted best effort.
        }
    }

    private record ProcessResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            int timeUsedMs
    ) {
    }
}
