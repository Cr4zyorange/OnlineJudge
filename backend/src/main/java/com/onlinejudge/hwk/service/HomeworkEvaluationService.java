package com.onlinejudge.hwk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.SandboxExecutor;
import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeworkEvaluationService {
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkEvaluationRepository evaluationRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final SandboxExecutor sandboxExecutor;
    private final ObjectMapper objectMapper;

    public HomeworkEvaluationService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            HomeworkEvaluationRepository evaluationRepository,
            CoursePermissionClient coursePermissionClient,
            ObjectProvider<SandboxExecutor> sandboxExecutorProvider,
            ObjectMapper objectMapper
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.sandboxExecutor = sandboxExecutorProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HomeworkEvaluation evaluate(Homework homework, HomeworkSubmission submission) {
        if (homework.type() == HomeworkType.FILE) {
            return null;
        }
        EvaluationOutcome outcome = homework.type() == HomeworkType.OBJECTIVE
                ? evaluateObjective(homework, submission)
                : evaluateCode(homework, submission);
        LocalDateTime now = LocalDateTime.now();
        HomeworkEvaluation evaluation = evaluationRepository.save(new HomeworkEvaluation(
                0L,
                homework.id(),
                submission.id(),
                homework.type().name(),
                outcome.status(),
                outcome.score(),
                homework.totalScore(),
                outcome.passedCount(),
                outcome.totalCount(),
                toJson(outcome.caseResults()),
                outcome.message(),
                outcome.startedAt(),
                now,
                now,
                now
        ));
        submissionRepository.updateEvaluation(
                submission.id(),
                outcome.status(),
                outcome.score(),
                outcome.status() == HomeworkEvaluationStatus.ACCEPTED ? outcome.score() : null
        );
        return evaluation;
    }

    public HomeworkEvaluation getForSubmission(long submissionId, CurrentUser currentUser) {
        HomeworkSubmission submission = findSubmission(submissionId);
        Homework homework = findHomework(submission.homeworkId());
        if (submission.studentId() == currentUser.id() && coursePermissionClient.isCourseMember(homework.courseId(), currentUser.id())) {
            return findEvaluation(submissionId);
        }
        requireManagePermission(homework.courseId(), currentUser.id());
        return findEvaluation(submissionId);
    }

    @Transactional
    public HomeworkEvaluation reevaluate(long submissionId, CurrentUser currentUser) {
        HomeworkSubmission submission = findSubmission(submissionId);
        Homework homework = findHomework(submission.homeworkId());
        requireManagePermission(homework.courseId(), currentUser.id());
        return evaluate(homework, submission);
    }

    private EvaluationOutcome evaluateObjective(Homework homework, HomeworkSubmission submission) {
        LocalDateTime startedAt = LocalDateTime.now();
        BigDecimal score = BigDecimal.ZERO;
        int passed = 0;
        List<Map<String, Object>> caseResults = new ArrayList<>();
        JsonNode answers = parseJson(submission.answerJson(), "客观题提交答案格式不合法");
        for (HomeworkQuestion question : homework.questions()) {
            JsonNode expected = parseJson(question.answerJson(), "客观题标准答案格式不合法");
            JsonNode actual = answers.get(String.valueOf(question.id()));
            if (actual == null) {
                actual = answers.get(String.valueOf(question.sortOrder()));
            }
            boolean accepted = expected.equals(actual);
            if (accepted) {
                score = score.add(question.score());
                passed++;
            }
            caseResults.add(Map.of(
                    "questionId", question.id(),
                    "sortOrder", question.sortOrder(),
                    "accepted", accepted,
                    "score", accepted ? question.score() : BigDecimal.ZERO
            ));
        }
        HomeworkEvaluationStatus status = passed == homework.questions().size()
                ? HomeworkEvaluationStatus.ACCEPTED
                : HomeworkEvaluationStatus.WRONG_ANSWER;
        return new EvaluationOutcome(status, score, passed, homework.questions().size(), caseResults, "客观题自动评分完成", startedAt);
    }

    private EvaluationOutcome evaluateCode(Homework homework, HomeworkSubmission submission) {
        LocalDateTime startedAt = LocalDateTime.now();
        if (sandboxExecutor != null) {
            EvaluationResult result = sandboxExecutor.execute(new EvaluationTask(
                    "HWK-" + submission.id() + "-" + System.nanoTime(),
                    "HWK",
                    homework.courseId(),
                    homework.id(),
                    submission.id(),
                    submission.studentId(),
                    submission.language(),
                    submission.answerText(),
                    Map.of("testCases", toJson(homework.testCases())),
                    startedAt
            ));
            HomeworkEvaluationStatus status = toHomeworkStatus(result.status());
            List<Map<String, Object>> caseResults = new ArrayList<>();
            for (String caseResult : result.caseResults()) {
                caseResults.add(Map.of("result", caseResult));
            }
            return new EvaluationOutcome(
                    status,
                    result.score(),
                    (int) result.caseResults().stream().filter(value -> value.contains("PASS")).count(),
                    homework.testCases().size(),
                    caseResults,
                    result.message(),
                    startedAt
            );
        }
        return evaluatePlainOutput(homework, submission, startedAt);
    }

    private EvaluationOutcome evaluatePlainOutput(Homework homework, HomeworkSubmission submission, LocalDateTime startedAt) {
        if (!"OUTPUT".equalsIgnoreCase(submission.language()) && !"PLAIN_OUTPUT".equalsIgnoreCase(submission.language())) {
            return new EvaluationOutcome(
                    HomeworkEvaluationStatus.SYSTEM_ERROR,
                    BigDecimal.ZERO,
                    0,
                    homework.testCases().size(),
                    List.of(Map.of("error", "代码执行器未配置")),
                    "代码执行器未配置，无法执行代码评测",
                    startedAt
            );
        }
        String[] outputs = submission.answerText() == null ? new String[0] : submission.answerText().split("\\R---\\R", -1);
        BigDecimal score = BigDecimal.ZERO;
        int passed = 0;
        List<Map<String, Object>> caseResults = new ArrayList<>();
        for (int index = 0; index < homework.testCases().size(); index++) {
            HomeworkTestCase testCase = homework.testCases().get(index);
            String actual = index < outputs.length ? outputs[index].trim() : "";
            boolean accepted = testCase.expectedOutput().trim().equals(actual);
            if (accepted) {
                score = score.add(homework.totalScore().multiply(testCase.scoreWeight()).divide(new BigDecimal("100.00")));
                passed++;
            }
            caseResults.add(Map.of(
                    "caseIndex", index + 1,
                    "accepted", accepted,
                    "expected", testCase.hidden() ? "<hidden>" : testCase.expectedOutput(),
                    "actual", actual
            ));
        }
        HomeworkEvaluationStatus status = passed == homework.testCases().size()
                ? HomeworkEvaluationStatus.ACCEPTED
                : HomeworkEvaluationStatus.WRONG_ANSWER;
        return new EvaluationOutcome(status, score, passed, homework.testCases().size(), caseResults, "代码 IO 比对评测完成", startedAt);
    }

    private HomeworkSubmission findSubmission(long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ApiException("ERR-HWK-07", "提交记录不存在", HttpStatus.NOT_FOUND));
    }

    private Homework findHomework(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new ApiException("ERR-HWK-04", "作业不存在", HttpStatus.NOT_FOUND));
    }

    private HomeworkEvaluation findEvaluation(long submissionId) {
        return evaluationRepository.findLatestBySubmissionId(submissionId)
                .orElseThrow(() -> new ApiException("ERR-HWK-08", "评测结果不存在", HttpStatus.NOT_FOUND));
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new ApiException("ERR-HWK-01", "无课程作业管理权限", HttpStatus.FORBIDDEN);
        }
    }

    private JsonNode parseJson(String json, String errorMessage) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ApiException("ERR-HWK-03", errorMessage, HttpStatus.BAD_REQUEST);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException("ERR-HWK-08", "评测结果序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private HomeworkEvaluationStatus toHomeworkStatus(EvaluationStatus status) {
        return switch (status) {
            case PENDING -> HomeworkEvaluationStatus.PENDING;
            case RUNNING -> HomeworkEvaluationStatus.RUNNING;
            case ACCEPTED -> HomeworkEvaluationStatus.ACCEPTED;
            case WRONG_ANSWER -> HomeworkEvaluationStatus.WRONG_ANSWER;
            case COMPILE_ERROR -> HomeworkEvaluationStatus.COMPILE_ERROR;
            case RUNTIME_ERROR -> HomeworkEvaluationStatus.RUNTIME_ERROR;
            case TIME_LIMIT_EXCEEDED -> HomeworkEvaluationStatus.TIME_LIMIT_EXCEEDED;
            case SYSTEM_ERROR -> HomeworkEvaluationStatus.SYSTEM_ERROR;
        };
    }

    private record EvaluationOutcome(
            HomeworkEvaluationStatus status,
            BigDecimal score,
            int passedCount,
            int totalCount,
            List<Map<String, Object>> caseResults,
            String message,
            LocalDateTime startedAt
    ) {
    }
}
