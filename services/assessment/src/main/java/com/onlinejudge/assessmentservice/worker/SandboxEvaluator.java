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

/** Evaluates LAB and HWK code only through the isolated Docker sandbox boundary. */
@Component
public class SandboxEvaluator {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final DockerSandboxClient docker;

    @Autowired
    public SandboxEvaluator(JdbcTemplate jdbc, @Value("${assessment.storage.root:./var/assessment-files}") String root,
            @Value("${assessment.sandbox.docker-api-uri:}") String dockerApiUri,
            @Value("${assessment.sandbox.image:python:3.12-alpine}") String pythonImage,
            @Value("${assessment.sandbox.java-image:eclipse-temurin:21-jdk-alpine}") String javaImage,
            @Value("${assessment.sandbox.cpp-image:gcc:14.2.0}") String cppImage) {
        this(jdbc, Path.of(root), new DockerSandboxClient(dockerApiUri, pythonImage, javaImage, cppImage));
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
        try {
            Path source = source(storageKey);
            DockerSandboxClient.Result result = docker.evaluate(languageFor(storageKey), Files.readAllBytes(source), "", 60_000, 262_144);
            return result.status() == null ? AssessmentWorker.EvaluationOutcome.successful("ACCEPTED")
                    : AssessmentWorker.EvaluationOutcome.failed(result.status());
        } catch (Exception rejected) {
            return AssessmentWorker.EvaluationOutcome.failed("SYSTEM_ERROR");
        }
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
        try {
            Path source = source(storageKey);
            String language = jdbc.queryForObject("SELECT language FROM assessment_lab_submission WHERE submission_id = ?", String.class, submissionId);
            DockerSandboxClient.Result result = docker.evaluate(language, Files.readAllBytes(source), inputText, limits.timeLimitMs(), limits.memoryLimitKb());
            return new ProcessResult(result.output(), result.status());
        } catch (Exception rejected) {
            return new ProcessResult(null, "SYSTEM_ERROR");
        }
    }

    private Path source(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("submission storage key is missing");
        Path source = root.resolve(storageKey).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) throw new IllegalArgumentException("submission source file is missing");
        return source;
    }

    private static String languageFor(String storageKey) {
        String filename = storageKey == null ? "" : storageKey.toLowerCase(java.util.Locale.ROOT);
        if (filename.endsWith(".py")) return "python";
        if (filename.endsWith(".java")) return "java";
        if (filename.endsWith(".cpp") || filename.endsWith(".cc") || filename.endsWith(".cxx")) return "cpp";
        return "";
    }

    private static String normalize(String value) { return value == null ? "" : value.replace("\r\n", "\n").trim(); }
    private record LabCase(long id, String input, String expectedOutput, BigDecimal scoreWeight) { }
    private record LabLimits(int timeLimitMs, int memoryLimitKb) { }
    private record ProcessResult(String output, String status) { }
}
