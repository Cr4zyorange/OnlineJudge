package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SandboxBackedLabEvaluator implements Evaluator {
    private static final String SANDBOX_UNAVAILABLE_MESSAGE = "评测沙箱未接入，当前环境禁止直接执行学生代码";

    @Override
    public EvaluationResult evaluate(EvaluationTask task) {
        // LAB/HWK share the Evaluator abstraction, but the actual untrusted code execution
        // must happen behind a sandbox boundary rather than inside the backend host process.
        return new EvaluationResult(
                task.taskId(),
                EvaluationStatus.SYSTEM_ERROR,
                BigDecimal.ZERO,
                SANDBOX_UNAVAILABLE_MESSAGE,
                List.of(),
                LocalDateTime.now()
        );
    }
}
