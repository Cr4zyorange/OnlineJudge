package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Invokes the configured external sandbox runner only against a durable submission object. */
@Component
public class SandboxEvaluator {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final List<String> command;
    private final Duration timeout;
    private final Duration preExecutionDelay;

    @Autowired
    public SandboxEvaluator(JdbcTemplate jdbc, @Value("${assessment.storage.root:./var/assessment-files}") String root,
            @Value("${assessment.sandbox.command:}") String command,
            @Value("${assessment.sandbox.timeout:PT30S}") Duration timeout,
            @Value("${assessment.sandbox.pre-execution-delay:PT0S}") Duration preExecutionDelay) {
        this(jdbc, Path.of(root), command, timeout, preExecutionDelay);
    }

    public SandboxEvaluator(Path root, String command, Duration timeout) { this(null, root, command, timeout, Duration.ZERO); }
    public SandboxEvaluator(Path root, List<String> command, Duration timeout) { this(null, root, command, timeout, Duration.ZERO); }
    private SandboxEvaluator(JdbcTemplate jdbc, Path root, String command, Duration timeout, Duration preExecutionDelay) {
        this(jdbc, root, command == null || command.isBlank() ? List.of() : List.of(command.trim().split("\\s+")), timeout, preExecutionDelay);
    }

    public SandboxEvaluator(JdbcTemplate jdbc, Path root, List<String> command, Duration timeout) {
        this(jdbc, root, command, timeout, Duration.ZERO);
    }

    private SandboxEvaluator(JdbcTemplate jdbc, Path root, List<String> command, Duration timeout, Duration preExecutionDelay) {
        this.jdbc = jdbc; this.root = root.toAbsolutePath().normalize(); this.command = List.copyOf(command); this.timeout = timeout; this.preExecutionDelay = preExecutionDelay;
    }

    public AssessmentWorker.EvaluationOutcome evaluate(EvaluationTask task) {
        if (jdbc == null) return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNAVAILABLE");
        String key = jdbc.queryForObject("SELECT content_ref FROM assessment_submission WHERE id = ?", String.class, task.submissionId());
        if ("HWK".equals(task.sourceType())) return evaluateHomework(task.sourceId(), key);
        return evaluate(key);
    }

    private AssessmentWorker.EvaluationOutcome evaluateHomework(String homeworkId, String storageKey) {
        List<Testcase> testcases = jdbc.query("""
                SELECT input_text, expected_output, score_weight
                FROM assessment_homework_testcase
                WHERE homework_id = ?
                ORDER BY sort_order, id
                """, (rs, rowNum) -> new Testcase(rs.getString("input_text"), rs.getString("expected_output"),
                        rs.getBigDecimal("score_weight")), Long.parseLong(homeworkId));
        if (testcases.isEmpty()) return AssessmentWorker.EvaluationOutcome.failed("TESTCASE_MISSING");

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal fullScore = BigDecimal.ZERO;
        boolean allAccepted = true;
        for (Testcase testcase : testcases) {
            fullScore = fullScore.add(testcase.scoreWeight());
            Execution execution = execute(storageKey, testcase.input());
            if (!execution.successful()) return new AssessmentWorker.EvaluationOutcome(false, execution.status(), BigDecimal.ZERO, fullScore);
            if (normalize(execution.output()).equals(normalize(testcase.expectedOutput()))) {
                earned = earned.add(testcase.scoreWeight());
            } else {
                allAccepted = false;
            }
        }
        return new AssessmentWorker.EvaluationOutcome(true, allAccepted ? "ACCEPTED" : "WRONG_ANSWER", earned, fullScore);
    }

    public AssessmentWorker.EvaluationOutcome evaluate(String storageKey) {
        Execution execution = execute(storageKey, "");
        return execution.successful() ? AssessmentWorker.EvaluationOutcome.successful("ACCEPTED")
                : AssessmentWorker.EvaluationOutcome.failed(execution.status());
    }

    private Execution execute(String storageKey, String standardInput) {
        if (command.isEmpty()) return Execution.failed("SANDBOX_UNCONFIGURED");
        try {
            if (!preExecutionDelay.isZero()) Thread.sleep(preExecutionDelay.toMillis());
            Path input = root.resolve(storageKey).normalize();
            if (!input.startsWith(root) || !Files.isRegularFile(input)) return Execution.failed("SUBMISSION_FILE_MISSING");
            List<String> invocation = new ArrayList<>(command);
            invocation.add(input.toString());
            Process process = new ProcessBuilder(invocation).redirectErrorStream(true).start();
            try (var output = process.getOutputStream()) {
                output.write(standardInput.getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
                var reading = reader.submit(() -> process.getInputStream().transferTo(captured));
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    reading.cancel(true);
                    return Execution.failed("SANDBOX_TIMEOUT");
                }
                reading.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            }
            return process.exitValue() == 0
                    ? new Execution(true, "ACCEPTED", captured.toString(StandardCharsets.UTF_8))
                    : Execution.failed("SANDBOX_EXIT_" + process.exitValue());
        } catch (Exception rejected) {
            return Execution.failed("SANDBOX_ERROR");
        }
    }

    private static String normalize(String output) {
        return output == null ? "" : output.replace("\r\n", "\n").stripTrailing();
    }

    private record Testcase(String input, String expectedOutput, BigDecimal scoreWeight) {}
    private record Execution(boolean successful, String status, String output) {
        private static Execution failed(String status) { return new Execution(false, status, ""); }
    }
}
