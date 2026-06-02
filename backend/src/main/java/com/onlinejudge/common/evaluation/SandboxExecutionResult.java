package com.onlinejudge.common.evaluation;

public record SandboxExecutionResult(
        EvaluationStatus status,
        String actualOutput,
        String message,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        String compileLog,
        String runLog
) {
}
