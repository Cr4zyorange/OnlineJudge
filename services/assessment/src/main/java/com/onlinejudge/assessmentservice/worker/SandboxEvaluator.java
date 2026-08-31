package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Evaluates LAB code only through the isolated Docker sandbox boundary. */
@Component
public class SandboxEvaluator {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final DockerSandboxClient docker;

    @Autowired
    public SandboxEvaluator(JdbcTemplate jdbc, @Value("${assessment.storage.root:./var/assessment-files}") String root,
            @Value("${assessment.sandbox.docker-api-uri:}") String dockerApiUri,
            @Value("${assessment.sandbox.image:python:3.12-alpine}") String dockerImage) {
        this(jdbc, Path.of(root), new DockerSandboxClient(dockerApiUri, dockerImage));
    }

    public SandboxEvaluator(Path root) { this(null, root, new DockerSandboxClient("", "")); }
    private SandboxEvaluator(JdbcTemplate jdbc, Path root, DockerSandboxClient docker) {
        this.jdbc = jdbc; this.root = root.toAbsolutePath().normalize(); this.docker = docker;
    }

    public AssessmentWorker.EvaluationOutcome evaluate(EvaluationTask task) {
        if (jdbc == null) return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNAVAILABLE");
        String key = jdbc.queryForObject("SELECT content_ref FROM assessment_submission WHERE id = ?", String.class, task.submissionId());
        if ("LAB".equals(task.sourceType())) return evaluateLab(task, key);
        return evaluate(key);
    }

    public AssessmentWorker.EvaluationOutcome evaluate(String storageKey) {
        return AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNCONFIGURED");
    }

    private AssessmentWorker.EvaluationOutcome evaluateLab(EvaluationTask task, String storageKey) {
        LabLimits limits = jdbc.query("SELECT time_limit_ms, memory_limit_kb FROM assessment_lab_experiment WHERE id = ?",
                (rs, ignored) -> new LabLimits(rs.getInt("time_limit_ms"), rs.getInt("memory_limit_kb")), Long.parseLong(task.sourceId()))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("LAB does not exist"));
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
            ProcessResult process = executeInDocker(task.submissionId(), storageKey, testcase.input(), limits);
            if (process.status() != null) return new AssessmentWorker.EvaluationOutcome(false, process.status(), awarded, fullScore, results);
            boolean passed = normalize(process.output()).equals(normalize(testcase.expectedOutput()));
            BigDecimal score = passed ? testcase.scoreWeight() : BigDecimal.ZERO;
            awarded = awarded.add(score);
            results.add(new AssessmentWorker.LabCaseResult(testcase.id(), passed, score, process.output(),
                    passed ? "accepted" : "output does not match expected result"));
        }
        return new AssessmentWorker.EvaluationOutcome(true, awarded.compareTo(fullScore) == 0 ? "ACCEPTED" : "WRONG_ANSWER", awarded, fullScore, results);
    }

    private ProcessResult executeInDocker(String submissionId, String storageKey, String inputText, LabLimits limits) {
        if (!docker.configured()) return new ProcessResult(null, "SANDBOX_UNCONFIGURED");
        try {
            Path source = root.resolve(storageKey).normalize();
            if (!source.startsWith(root) || !Files.isRegularFile(source)) return new ProcessResult(null, "SUBMISSION_FILE_MISSING");
            String language = jdbc.queryForObject("SELECT language FROM assessment_lab_submission WHERE submission_id = ?", String.class, submissionId);
            DockerSandboxClient.Result result = docker.evaluate(language, Files.readAllBytes(source), inputText, limits.timeLimitMs(), limits.memoryLimitKb());
            return new ProcessResult(result.output(), result.status());
        } catch (Exception rejected) {
            return new ProcessResult(null, "SANDBOX_ERROR");
        }
    }

    private static String normalize(String value) { return value == null ? "" : value.replace("\r\n", "\n").trim(); }
    private record LabCase(long id, String input, String expectedOutput, BigDecimal scoreWeight) { }
    private record LabLimits(int timeLimitMs, int memoryLimitKb) { }
    private record ProcessResult(String output, String status) { }
}
