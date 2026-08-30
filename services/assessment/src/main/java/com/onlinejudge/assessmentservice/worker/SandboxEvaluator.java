package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Invokes the configured external sandbox runner only against a durable submission object. */
@Component
public class SandboxEvaluator {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final String command;
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
    private SandboxEvaluator(JdbcTemplate jdbc, Path root, String command, Duration timeout, Duration preExecutionDelay) {
        this.jdbc = jdbc; this.root = root.toAbsolutePath().normalize(); this.command = command; this.timeout = timeout; this.preExecutionDelay = preExecutionDelay;
    }

    public AssessmentWorker.EvaluationOutcome evaluate(EvaluationTask task) {
        if (jdbc == null) return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNAVAILABLE");
        String key = jdbc.queryForObject("SELECT content_ref FROM assessment_submission WHERE id = ?", String.class, task.submissionId());
        return evaluate(key);
    }

    public AssessmentWorker.EvaluationOutcome evaluate(String storageKey) {
        if (command == null || command.isBlank()) return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNCONFIGURED");
        try {
            if (!preExecutionDelay.isZero()) Thread.sleep(preExecutionDelay.toMillis());
            Path input = root.resolve(storageKey).normalize();
            if (!input.startsWith(root) || !Files.isRegularFile(input)) return AssessmentWorker.EvaluationOutcome.failed("SUBMISSION_FILE_MISSING");
            List<String> invocation = new ArrayList<>(List.of(command.trim().split("\\s+")));
            invocation.add(input.toString());
            Process process = new ProcessBuilder(invocation).redirectErrorStream(true).start();
            if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_TIMEOUT");
            }
            return process.exitValue() == 0 ? AssessmentWorker.EvaluationOutcome.successful("ACCEPTED")
                    : AssessmentWorker.EvaluationOutcome.failed("SANDBOX_EXIT_" + process.exitValue());
        } catch (Exception rejected) {
            return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_ERROR");
        }
    }
}
