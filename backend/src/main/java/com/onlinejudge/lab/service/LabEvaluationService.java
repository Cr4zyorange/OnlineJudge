package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LabEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(LabEvaluationService.class);

    private final Evaluator evaluator;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabEvaluationRepository labEvaluationRepository;
    private final LabEvaluationResultRepository labEvaluationResultRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public LabEvaluationService(
            Evaluator evaluator,
            LabSubmissionRepository labSubmissionRepository,
            LabEvaluationRepository labEvaluationRepository,
            LabEvaluationResultRepository labEvaluationResultRepository,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.evaluator = evaluator;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labEvaluationRepository = labEvaluationRepository;
        this.labEvaluationResultRepository = labEvaluationResultRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Async("labEvaluationExecutor")
    @Transactional
    public void evaluateSubmissionAsync(LabExperiment experiment, LabSubmission submission, String sourceCode) {
        evaluateSubmission(experiment, submission, sourceCode);
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
        LabTestcase currentTestcase = null;
        try {
            for (LabTestcase testcase : experiment.testcases()) {
                currentTestcase = testcase;
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
                currentTestcase = null;
            }
            return finalizeEvaluation(experiment, submission, startedAt, caseResults);
        } catch (RuntimeException exception) {
            LocalDateTime failedAt = LocalDateTime.now();
            String failureMessage = resolveFailureMessage(exception);
            log.error("LAB evaluation failed for submission {}", submission.id(), exception);
            if (currentTestcase != null) {
                caseResults.add(new LabEvaluationCaseResult(
                        0L,
                        submission.id(),
                        currentTestcase.id(),
                        currentTestcase.orderNum(),
                        currentTestcase.isPublic(),
                        EvaluationStatus.SYSTEM_ERROR,
                        false,
                        0,
                        currentTestcase.input(),
                        currentTestcase.expectedOutput(),
                        "",
                        failureMessage,
                        failedAt,
                        failedAt,
                        failedAt
                ));
            }
            labEvaluationResultRepository.replaceSubmissionResults(submission.id(), caseResults);
            int passedCases = (int) caseResults.stream().filter(LabEvaluationCaseResult::passed).count();
            int totalCases = experiment.testcases().size();
            int autoScore = caseResults.stream().mapToInt(LabEvaluationCaseResult::score).sum();
            upsertEvaluation(
                    submission,
                    EvaluationStatus.SYSTEM_ERROR,
                    autoScore,
                    passedCases,
                    totalCases,
                    resolveEvaluationMessage(EvaluationStatus.SYSTEM_ERROR, passedCases),
                    null,
                    failureMessage,
                    startedAt,
                    failedAt
            );
            LabSubmission updated = labSubmissionRepository.update(
                    submission.withEvaluationResult(EvaluationStatus.SYSTEM_ERROR, autoScore, submission.finalScore(), failedAt)
            );
            publishCompletionEvent(
                    experiment,
                    submission,
                    EvaluationStatus.SYSTEM_ERROR,
                    passedCases,
                    resolveEvaluationMessage(EvaluationStatus.SYSTEM_ERROR, passedCases)
            );
            return updated;
        }
    }

    private LabSubmission finalizeEvaluation(
            LabExperiment experiment,
            LabSubmission submission,
            LocalDateTime startedAt,
            List<LabEvaluationCaseResult> caseResults
    ) {
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
        LabSubmission updated = labSubmissionRepository.update(
                submission.withEvaluationResult(finalStatus, autoScore, submission.finalScore(), finishedAt)
        );
        publishCompletionEvent(
                experiment,
                submission,
                finalStatus,
                passedCases,
                resolveEvaluationMessage(finalStatus, passedCases)
        );
        return updated;
    }

    private void publishCompletionEvent(
            LabExperiment experiment,
            LabSubmission submission,
            EvaluationStatus status,
            int passedCases,
            String message
    ) {
        notificationEventPublisher.publish(new NotificationEvent(
                "LAB_EVALUATION_" + submission.id(),
                "LAB_EVALUATION_COMPLETED",
                experiment.courseId(),
                List.of(submission.studentId()),
                "实验评测完成",
                "实验评测完成（" + status + "）：" + message
                        + "（通过 " + passedCases + " 个用例）",
                "LAB",
                experiment.id(),
                "/courses/" + experiment.courseId() + "/labs/" + experiment.id(),
                LocalDateTime.now()
        ));
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

    private String resolveFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "评测器内部异常: " + exception.getClass().getSimpleName();
        }
        return message;
    }
}
