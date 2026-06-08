package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.SandboxExecutionResult;
import com.onlinejudge.common.evaluation.SandboxExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "onlinejudge.evaluation.sandbox", name = "mode", havingValue = "fake")
public class FakeSandboxExecutor implements SandboxExecutor {
    private final long delayMs;

    public FakeSandboxExecutor(@Value("${onlinejudge.evaluation.fake.delay-ms:0}") long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public SandboxExecutionResult execute(EvaluationTask task) {
        delayIfConfigured();
        if (!"python".equalsIgnoreCase(task.language())) {
            return new SandboxExecutionResult(
                    EvaluationStatus.SYSTEM_ERROR,
                    "",
                    "当前假沙箱仅支持 Python 语言",
                    null,
                    null,
                    null,
                    null
            );
        }

        String source = task.sourceCode() == null ? "" : task.sourceCode();
        String stdin = task.options().getOrDefault("stdin", "");
        String expectedOutput = normalize(task.options().get("expectedOutput"));
        int timeLimitMs = parseInt(task.options().get("timeLimitMs"), 1000);

        if (source.strip().equals("print(")) {
            return new SandboxExecutionResult(EvaluationStatus.COMPILE_ERROR, "", "编译失败", 1, 1024, "syntax error", null);
        }
        if (source.contains("raise RuntimeError")) {
            return new SandboxExecutionResult(EvaluationStatus.RUNTIME_ERROR, "", "运行时异常", 1, 1024, null, "runtime error");
        }
        if (source.contains("while True:")) {
            return new SandboxExecutionResult(EvaluationStatus.TIME_LIMIT_EXCEEDED, "", "程序运行超时", timeLimitMs + 1, 1024, null, "timeout");
        }
        if (source.contains("#FAKE_SANDBOX_SYSTEM_ERROR")) {
            return new SandboxExecutionResult(EvaluationStatus.SYSTEM_ERROR, "", "假沙箱内部错误", 1, 1024, null, "fake sandbox error");
        }
        if (source.contains("#FAKE_SANDBOX_COMPILE_ERROR")) {
            return new SandboxExecutionResult(EvaluationStatus.COMPILE_ERROR, "", "编译失败", 1, 1024, "syntax error", null);
        }
        if (source.contains("#FAKE_SANDBOX_RUNTIME_ERROR")) {
            return new SandboxExecutionResult(EvaluationStatus.RUNTIME_ERROR, "", "运行时异常", 1, 1024, null, "runtime error");
        }
        if (source.contains("#FAKE_SANDBOX_TIMEOUT=")) {
            int declaredCost = parseDirectiveValue(source, "#FAKE_SANDBOX_TIMEOUT=", timeLimitMs + 1);
            if (timeLimitMs < declaredCost) {
                return new SandboxExecutionResult(EvaluationStatus.TIME_LIMIT_EXCEEDED, "", "程序运行超时", declaredCost, 1024, null, "timeout");
            }
        }

        String actualOutput = resolveActualOutput(source, stdin);
        EvaluationStatus status = normalize(actualOutput).equals(expectedOutput)
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
        String message = status == EvaluationStatus.ACCEPTED
                ? "通过"
                : "期望输出 %s，实际输出 %s".formatted(expectedOutput, actualOutput.isBlank() ? "<空>" : normalize(actualOutput));
        return new SandboxExecutionResult(status, actualOutput, message, Math.min(timeLimitMs, 10), 1024, null, null);
    }

    private String resolveActualOutput(String source, String stdin) {
        if (source.contains("#FAKE_SANDBOX_WRONG_ANSWER")) {
            return directiveValue(source, "#FAKE_SANDBOX_WRONG_ANSWER=", "wrong-answer");
        }
        if (source.contains("#FAKE_SANDBOX_OUTPUT=")) {
            return directiveValue(source, "#FAKE_SANDBOX_OUTPUT=", "");
        }
        if (source.contains("first, second = map(int, input().split())")) {
            String[] numbers = stdin.trim().split("\\s+");
            if (numbers.length >= 2) {
                return "sum:" + (Integer.parseInt(numbers[0]) + Integer.parseInt(numbers[1]));
            }
        }
        if (source.contains("left, right = map(int, input().split())")) {
            String[] numbers = stdin.trim().split("\\s+");
            if (numbers.length >= 2) {
                return Integer.toString(Integer.parseInt(numbers[0]) + Integer.parseInt(numbers[1]));
            }
        }
        if (source.contains("print(input().strip())")) {
            return stdin.strip();
        }
        if (source.contains("if value == \"case-a\":")) {
            return "case-a".equals(stdin.strip()) ? "answer-a" : "wrong-b";
        }
        return stdin.strip();
    }

    private String directiveValue(String source, String prefix, String fallback) {
        int start = source.indexOf(prefix);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + prefix.length();
        int valueEnd = source.indexOf('\n', valueStart);
        if (valueEnd < 0) {
            valueEnd = source.length();
        }
        return source.substring(valueStart, valueEnd).trim();
    }

    private int parseDirectiveValue(String source, String prefix, int fallback) {
        try {
            return Integer.parseInt(directiveValue(source, prefix, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
    }

    private void delayIfConfigured() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
