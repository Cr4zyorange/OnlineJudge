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
        return new EvaluationResult(
                task.taskId(),
                result.status(),
                result.status() == EvaluationStatus.ACCEPTED ? BigDecimal.ONE : BigDecimal.ZERO,
                result.message(),
                List.of(result.actualOutput()),
                LocalDateTime.now()
        );
    }
}
