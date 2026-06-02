package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.common.evaluation.SandboxExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SandboxBackedLabEvaluator implements Evaluator {
    private final SandboxExecutor sandboxExecutor;

    public SandboxBackedLabEvaluator(SandboxExecutor sandboxExecutor) {
        this.sandboxExecutor = sandboxExecutor;
    }

    @Override
    public EvaluationResult evaluate(EvaluationTask task) {
        var result = sandboxExecutor.execute(task);
        if (result.status() != EvaluationStatus.ACCEPTED) {
            return new EvaluationResult(
                    task.taskId(),
                    result.status(),
                    BigDecimal.ZERO,
                    result.message(),
                    List.of(result.actualOutput() == null ? "" : result.actualOutput()),
                    LocalDateTime.now()
            );
        }
        String actualOutput = normalize(result.actualOutput());
        String expectedOutput = normalize(task.options().get("expectedOutput"));
        EvaluationStatus status = actualOutput.equals(expectedOutput)
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
        String message = status == EvaluationStatus.ACCEPTED
                ? "通过"
                : "期望输出 %s，实际输出 %s".formatted(
                        expectedOutput,
                        actualOutput.isBlank() ? "<空>" : actualOutput
                );
        return new EvaluationResult(
                task.taskId(),
                status,
                status == EvaluationStatus.ACCEPTED ? BigDecimal.ONE : BigDecimal.ZERO,
                message,
                List.of(actualOutput),
                LocalDateTime.now()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
    }
}
