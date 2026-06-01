package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.lab.domain.LabEvaluation;
import com.onlinejudge.lab.domain.LabEvaluationCaseResult;
import com.onlinejudge.lab.domain.LabEvaluationRepository;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabTestcase;
import com.onlinejudge.lab.domain.LabEvaluationResultRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LabEvaluationService {
    private final Evaluator evaluator;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabEvaluationRepository labEvaluationRepository;
    private final LabEvaluationResultRepository labEvaluationResultRepository;

    public LabEvaluationService(
            Evaluator evaluator,
            LabSubmissionRepository labSubmissionRepository,
            LabEvaluationRepository labEvaluationRepository,
            LabEvaluationResultRepository labEvaluationResultRepository
    ) {
        this.evaluator = evaluator;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labEvaluationRepository = labEvaluationRepository;
        this.labEvaluationResultRepository = labEvaluationResultRepository;
    }

    @Async("labEvaluationExecutor")
    @Transactional
    public void evaluateSubmissionAsync(LabExperiment experiment, LabSubmission submission, String sourceCode) {
        evaluateSubmission(experiment, submission, sourceCode);
    }

    @Transactional
    public LabSubmission evaluateSubmissionSync(LabExperiment experiment, LabSubmission submission, String sourceCode) {
        return evaluateSubmission(experiment, submission, sourceCode);
    }

    private LabSubmission evaluateSubmission(LabExperiment experiment, LabSubmission submission, String sourceCode) {
        LocalDateTime startedAt = LocalDateTime.now();
        LabEvaluation evaluation = upsertEvaluation(
                submission,
                EvaluationStatus.RUNNING,
                0,
                0,
                experiment.testcases().size(),
                "评测进行中",
                null,
                null,
                startedAt,
                null
        );
        labSubmissionRepository.update(submission.withEvaluationResult(EvaluationStatus.RUNNING, null, submission.finalScore(), startedAt));

        List<LabEvaluationCaseResult> caseResults = new ArrayList<>();
        for (LabTestcase testcase : experiment.testcases()) {
            var task = new EvaluationTask(
                    submission.id() + "-" + testcase.id(),
                    "LAB",
                    experiment.courseId(),
                    experiment.id(),
                    submission.id(),
                    submission.studentId(),
                    submission.language(),
                    sourceCode,
                    Map.of(
                            "stdin", testcase.input(),
                            "expectedOutput", testcase.expectedOutput(),
                            "timeLimitMs", Integer.toString(testcase.timeLimitMs()),
                            "memoryLimitKb", Integer.toString(testcase.memoryLimitKb())
                    ),
                    submission.submittedAt()
            );
            var result = evaluator.evaluate(task);
            boolean passed = result.status() == EvaluationStatus.ACCEPTED;
            caseResults.add(new LabEvaluationCaseResult(
                    0L,
                    submission.id(),
                    testcase.id(),
                    testcase.orderNum(),
                    testcase.isPublic(),
                    result.status(),
                    passed,
                    passed ? testcase.scoreWeight() : 0,
                    testcase.input(),
                    testcase.expectedOutput(),
                    firstCaseResult(result.caseResults()),
                    result.message(),
                    result.finishedAt(),
                    startedAt,
                    result.finishedAt()
            ));
        }

        labEvaluationResultRepository.replaceSubmissionResults(submission.id(), caseResults);

        int passedCases = (int) caseResults.stream().filter(LabEvaluationCaseResult::passed).count();
        int totalCases = caseResults.size();
        int autoScore = caseResults.stream().mapToInt(LabEvaluationCaseResult::score).sum();
        EvaluationStatus finalStatus = resolveFinalStatus(caseResults);
        LocalDateTime finishedAt = LocalDateTime.now();
        String runLog = caseResults.stream()
                .map(LabEvaluationCaseResult::message)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);

        upsertEvaluation(
                submission,
                finalStatus,
                autoScore,
                passedCases,
                totalCases,
                resolveEvaluationMessage(finalStatus, passedCases),
                findCompileLog(caseResults),
                runLog,
                startedAt,
                finishedAt
        );
        return labSubmissionRepository.update(submission.withEvaluationResult(finalStatus, autoScore, submission.finalScore(), finishedAt));
    }

    private LabEvaluation upsertEvaluation(
            LabSubmission submission,
            EvaluationStatus status,
            int score,
            int passedCases,
            int totalCases,
            String feedback,
            String compileLog,
            String runLog,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        LocalDateTime now = finishedAt == null ? startedAt : finishedAt;
        return labEvaluationRepository.findLatestBySubmissionId(submission.id())
                .map(existing -> labEvaluationRepository.update(new LabEvaluation(
                        existing.id(),
                        submission.id(),
                        status,
                        score,
                        passedCases,
                        totalCases,
                        finishedAt == null ? null : Math.toIntExact(Duration.between(startedAt, finishedAt).toMillis()),
                        null,
                        feedback,
                        compileLog,
                        runLog,
                        startedAt,
                        finishedAt,
                        existing.createdAt(),
                        now
                )))
                .orElseGet(() -> labEvaluationRepository.save(new LabEvaluation(
                        0L,
                        submission.id(),
                        status,
                        score,
                        passedCases,
                        totalCases,
                        finishedAt == null ? null : Math.toIntExact(Duration.between(startedAt, finishedAt).toMillis()),
                        null,
                        feedback,
                        compileLog,
                        runLog,
                        startedAt,
                        finishedAt,
                        startedAt,
                        now
                )));
    }

    private EvaluationStatus resolveFinalStatus(List<LabEvaluationCaseResult> caseResults) {
        if (caseResults.isEmpty()) {
            return EvaluationStatus.PENDING;
        }
        if (containsStatus(caseResults, EvaluationStatus.SYSTEM_ERROR)) {
            return EvaluationStatus.SYSTEM_ERROR;
        }
        if (containsStatus(caseResults, EvaluationStatus.COMPILE_ERROR)) {
            return EvaluationStatus.COMPILE_ERROR;
        }
        if (containsStatus(caseResults, EvaluationStatus.RUNTIME_ERROR)) {
            return EvaluationStatus.RUNTIME_ERROR;
        }
        if (containsStatus(caseResults, EvaluationStatus.TIME_LIMIT_EXCEEDED)) {
            return EvaluationStatus.TIME_LIMIT_EXCEEDED;
        }
        return caseResults.stream().allMatch(LabEvaluationCaseResult::passed)
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
    }

    private boolean containsStatus(List<LabEvaluationCaseResult> caseResults, EvaluationStatus target) {
        return caseResults.stream().anyMatch(result -> result.status() == target);
    }

    private String resolveEvaluationMessage(EvaluationStatus status, int passedCases) {
        return switch (status) {
            case ACCEPTED -> "全部用例通过";
            case WRONG_ANSWER -> passedCases == 0 ? "未通过任何用例" : "部分用例未通过";
            case COMPILE_ERROR -> "编译失败";
            case RUNTIME_ERROR -> "运行时异常";
            case TIME_LIMIT_EXCEEDED -> "程序运行超时";
            case SYSTEM_ERROR -> "评测失败";
            case RUNNING -> "评测进行中";
            case PENDING -> "等待评测";
            default -> status.name();
        };
    }

    private String findCompileLog(List<LabEvaluationCaseResult> caseResults) {
        return caseResults.stream()
                .filter(result -> result.status() == EvaluationStatus.COMPILE_ERROR)
                .map(LabEvaluationCaseResult::message)
                .findFirst()
                .orElse(null);
    }

    private String firstCaseResult(List<String> caseResults) {
        return caseResults.isEmpty() ? "" : caseResults.get(0);
    }
}
