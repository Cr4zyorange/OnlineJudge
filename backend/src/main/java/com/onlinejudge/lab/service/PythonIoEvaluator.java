package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class PythonIoEvaluator implements Evaluator {
    @Override
    public EvaluationResult evaluate(EvaluationTask task) {
        String testcaseInput = task.options().getOrDefault("stdin", "");
        String expectedOutput = normalize(task.options().get("expectedOutput"));
        LocalDateTime finishedAt = LocalDateTime.now();
        if (!"python".equalsIgnoreCase(task.language())) {
            return new EvaluationResult(
                    task.taskId(),
                    EvaluationStatus.SYSTEM_ERROR,
                    BigDecimal.ZERO,
                    "当前首版仅支持 Python 自动评测",
                    List.of(),
                    finishedAt
            );
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("lab-eval-", ".py");
            Files.writeString(tempFile, task.sourceCode(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(resolvePythonCommand(tempFile)).start();
            process.getOutputStream().write(testcaseInput.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new EvaluationResult(task.taskId(), EvaluationStatus.TIME_LIMIT_EXCEEDED, BigDecimal.ZERO, "程序运行超时", List.of(), LocalDateTime.now());
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new EvaluationResult(task.taskId(), EvaluationStatus.RUNTIME_ERROR, BigDecimal.ZERO, stderr.isBlank() ? "程序运行失败" : stderr.trim(), List.of(), LocalDateTime.now());
            }
            String actualOutput = normalize(stdout);
            boolean matched = actualOutput.equals(expectedOutput);
            return new EvaluationResult(
                    task.taskId(),
                    matched ? EvaluationStatus.ACCEPTED : EvaluationStatus.WRONG_ANSWER,
                    matched ? BigDecimal.ONE : BigDecimal.ZERO,
                    matched ? "通过" : "期望输出 %s，实际输出 %s".formatted(expectedOutput, actualOutput.isBlank() ? "<空>" : actualOutput),
                    List.of(actualOutput),
                    LocalDateTime.now()
            );
        } catch (IOException exception) {
            return new EvaluationResult(task.taskId(), EvaluationStatus.SYSTEM_ERROR, BigDecimal.ZERO, "评测执行失败: " + exception.getMessage(), List.of(), LocalDateTime.now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new EvaluationResult(task.taskId(), EvaluationStatus.SYSTEM_ERROR, BigDecimal.ZERO, "评测被中断", List.of(), LocalDateTime.now());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temp cleanup failure should not mask evaluation result.
                }
            }
        }
    }

    private List<String> resolvePythonCommand(Path tempFile) {
        List<List<String>> candidates = List.of(
                List.of("py", "-3", tempFile.toString()),
                List.of("python", tempFile.toString()),
                List.of("python3", tempFile.toString())
        );
        for (List<String> candidate : candidates) {
            try {
                Process probe = new ProcessBuilder(candidate.get(0), candidate.size() > 1 ? candidate.get(1) : "--version")
                        .redirectErrorStream(true)
                        .start();
                probe.waitFor(3, TimeUnit.SECONDS);
                return new ArrayList<>(candidate);
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        return new ArrayList<>(List.of("python", tempFile.toString()));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
    }
}
