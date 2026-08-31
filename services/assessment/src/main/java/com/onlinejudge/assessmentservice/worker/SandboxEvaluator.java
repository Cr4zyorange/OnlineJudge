package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Invokes the configured external sandbox runner only against a durable submission object. */
@Component
public class SandboxEvaluator {
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Path root;
    private final List<String> command;
    private final Duration timeout;
    private final Duration preExecutionDelay;
    private final int maxOutputBytes;

    @Autowired
    public SandboxEvaluator(JdbcTemplate jdbc, @Value("${assessment.storage.root:./var/assessment-files}") String root,
            @Value("${assessment.sandbox.command:}") String command,
            @Value("${assessment.sandbox.timeout:PT30S}") Duration timeout,
            @Value("${assessment.sandbox.pre-execution-delay:PT0S}") Duration preExecutionDelay,
            @Value("${assessment.sandbox.max-output-bytes:1048576}") int maxOutputBytes) {
        this(jdbc, Path.of(root), command, timeout, preExecutionDelay, maxOutputBytes);
    }

    public SandboxEvaluator(Path root, String command, Duration timeout) { this(null, root, command, timeout, Duration.ZERO, DEFAULT_MAX_OUTPUT_BYTES); }
    public SandboxEvaluator(Path root, List<String> command, Duration timeout) { this(null, root, command, timeout, Duration.ZERO, DEFAULT_MAX_OUTPUT_BYTES); }
    private SandboxEvaluator(JdbcTemplate jdbc, Path root, String command, Duration timeout, Duration preExecutionDelay,
            int maxOutputBytes) {
        this(jdbc, root, command == null || command.isBlank() ? List.of() : List.of(command.trim().split("\\s+")), timeout,
                preExecutionDelay, maxOutputBytes);
    }

    public SandboxEvaluator(JdbcTemplate jdbc, Path root, List<String> command, Duration timeout) {
        this(jdbc, root, command, timeout, Duration.ZERO, DEFAULT_MAX_OUTPUT_BYTES);
    }

    public SandboxEvaluator(JdbcTemplate jdbc, Path root, List<String> command, Duration timeout, int maxOutputBytes) {
        this(jdbc, root, command, timeout, Duration.ZERO, maxOutputBytes);
    }

    private SandboxEvaluator(JdbcTemplate jdbc, Path root, List<String> command, Duration timeout, Duration preExecutionDelay,
            int maxOutputBytes) {
        if (maxOutputBytes < 1) throw new IllegalArgumentException("sandbox output cap must be positive");
        this.jdbc = jdbc; this.root = root.toAbsolutePath().normalize(); this.command = List.copyOf(command);
        this.timeout = timeout; this.preExecutionDelay = preExecutionDelay; this.maxOutputBytes = maxOutputBytes;
    }

    public AssessmentWorker.EvaluationOutcome evaluate(EvaluationTask task) {
        if (jdbc == null) return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNAVAILABLE");
        String key = jdbc.queryForObject("SELECT content_ref FROM assessment_submission WHERE id = ?", String.class, task.submissionId());
        if ("HWK".equals(task.sourceType())) return evaluateHomework(task.sourceId(), key);
        if ("LAB".equals(task.sourceType())) return evaluateLab(task, key);
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
            AtomicBoolean outputExceeded = new AtomicBoolean();
            try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
                var reading = reader.submit(() -> copyBounded(process, captured, maxOutputBytes, outputExceeded));
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    reading.cancel(true);
                    return Execution.failed("SANDBOX_TIMEOUT");
                }
                reading.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            }
            if (outputExceeded.get()) return Execution.failed("OUTPUT_LIMIT_EXCEEDED");
            return process.exitValue() == 0
                    ? new Execution(true, "ACCEPTED", captured.toString(StandardCharsets.UTF_8))
                    : Execution.failed("SANDBOX_EXIT_" + process.exitValue());
        } catch (Exception rejected) {
            return Execution.failed("SANDBOX_ERROR");
        }
    }

    /** Reads at most {@code maxOutputBytes} of process output; overflowing kills the run to bound memory. */
    private static void copyBounded(Process process, ByteArrayOutputStream captured, int maxOutputBytes,
            AtomicBoolean exceeded) {
        byte[] buffer = new byte[8192];
        int total = 0;
        try (InputStream input = process.getInputStream()) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (total + count > maxOutputBytes) {
                    exceeded.set(true);
                    process.destroyForcibly();
                    return;
                }
                captured.write(buffer, 0, count);
                total += count;
            }
        } catch (IOException ignored) {
        }
    }

    private static String normalize(String output) {
        return output == null ? "" : output.replace("\r\n", "\n").stripTrailing();
    }

    private record Testcase(String input, String expectedOutput, BigDecimal scoreWeight) {}
    private record Execution(boolean successful, String status, String output) {
        private static Execution failed(String status) { return new Execution(false, status, ""); }
    }

    private AssessmentWorker.EvaluationOutcome evaluateLab(EvaluationTask task, String storageKey) {
        List<LabCase> cases = jdbc.query("""
                SELECT id, input_text, expected_output, score_weight
                  FROM assessment_lab_testcase WHERE lab_id = ? ORDER BY order_num, id
                """, (rs, ignored) -> new LabCase(rs.getLong("id"), rs.getString("input_text"),
                rs.getString("expected_output"), rs.getBigDecimal("score_weight")), Long.parseLong(task.sourceId()));
        if (cases.isEmpty()) return AssessmentWorker.EvaluationOutcome.failed("SYSTEM_ERROR");
        BigDecimal fullScore = cases.stream().map(LabCase::scoreWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fullScore.signum() <= 0) return AssessmentWorker.EvaluationOutcome.failed("SYSTEM_ERROR");
        List<AssessmentWorker.LabCaseResult> results = new ArrayList<>();
        BigDecimal awarded = BigDecimal.ZERO;
        for (LabCase testcase : cases) {
            Execution process = execute(storageKey, testcase.input());
            if (!process.successful()) return new AssessmentWorker.EvaluationOutcome(false, process.status(), awarded, fullScore, results);
            boolean passed = normalize(process.output()).equals(normalize(testcase.expectedOutput()));
            BigDecimal score = passed ? testcase.scoreWeight() : BigDecimal.ZERO;
            awarded = awarded.add(score);
            results.add(new AssessmentWorker.LabCaseResult(testcase.id(), passed, score, process.output(),
                    passed ? "accepted" : "output does not match expected result"));
        }
        return new AssessmentWorker.EvaluationOutcome(true, awarded.compareTo(fullScore) == 0 ? "ACCEPTED" : "WRONG_ANSWER", awarded, fullScore, results);
    }
    private record LabCase(long id, String input, String expectedOutput, BigDecimal scoreWeight) { }
}
