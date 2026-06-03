package com.onlinejudge.hwk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.CreateHomeworkSubmissionCommand;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationType;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewOperationType;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmissionSearchCriteria;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@Service
public class HomeworkSubmissionService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<HomeworkStatus> STUDENT_VISIBLE_STATUSES = List.of(
            HomeworkStatus.PUBLISHED,
            HomeworkStatus.CLOSED,
            HomeworkStatus.SCORE_PUBLISHED,
            HomeworkStatus.ARCHIVED
    );

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkEvaluationRepository evaluationRepository;
    private final HomeworkReviewLogRepository reviewLogRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final Evaluator evaluator;

    public HomeworkSubmissionService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            HomeworkEvaluationRepository evaluationRepository,
            HomeworkReviewLogRepository reviewLogRepository,
            CoursePermissionClient coursePermissionClient,
            Evaluator evaluator
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.evaluator = evaluator;
    }

    @Transactional
    public SubmittedHomeworkSubmission submit(long homeworkId, long studentId, CreateHomeworkSubmissionCommand command) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "homework not found", HttpStatus.NOT_FOUND));

        requireStudentCanSubmit(homework, studentId);
        CreateHomeworkSubmissionCommand normalized = normalize(command);
        validateContent(homework, normalized);

        LocalDateTime now = LocalDateTime.now();
        Optional<HomeworkSubmission> latestFinal = submissionRepository.findLatestFinalByHomeworkIdAndStudentId(homeworkId, studentId);
        if (latestFinal.isPresent() && !homework.allowResubmit()) {
            throw new HomeworkApiException("HWK_4006", "resubmit is not allowed", HttpStatus.CONFLICT);
        }
        latestFinal.ifPresent(existing -> submissionRepository.update(existing.markHistorical(now)));

        HomeworkSubmitStatus submitStatus = now.isAfter(homework.deadline()) ? HomeworkSubmitStatus.LATE : HomeworkSubmitStatus.SUBMITTED;
        ObjectiveScore objectiveScore = homework.type() == HomeworkType.OBJECTIVE
                ? calculateObjectiveScore(homework, normalized.answerJson())
                : null;
        EvaluationStatus evaluationStatus = evaluationStatus(homework, objectiveScore);
        HomeworkReviewStatus reviewStatus = reviewStatus(homework);
        Integer autoScore = objectiveScore == null ? null : objectiveScore.score();
        Integer finalScore = homework.type() == HomeworkType.OBJECTIVE ? autoScore : null;
        HomeworkSubmission submission = new HomeworkSubmission(
                0L,
                homeworkId,
                studentId,
                homework.type(),
                submissionAnswerText(homework.type(), normalized),
                normalized.answerJson(),
                normalized.fileIds(),
                normalized.language(),
                submitStatus,
                evaluationStatus,
                reviewStatus,
                autoScore,
                null,
                finalScore,
                null,
                latestFinal.map(item -> item.version() + 1).orElse(1),
                true,
                now,
                null,
                null,
                now,
                now,
                false
        );
        try {
            HomeworkSubmission saved = submissionRepository.save(submission);
            createInitialEvaluation(homework, saved, objectiveScore, now);
            return new SubmittedHomeworkSubmission(homework, saved);
        } catch (DataIntegrityViolationException exception) {
            throw new HomeworkApiException("HWK_4006", "submission version conflict, please retry", HttpStatus.CONFLICT);
        }
    }

    public SubmissionHistory listMine(long homeworkId, long studentId) {
        Homework homework = findExistingHomework(homeworkId);
        requireStudentCanViewHistory(homework, studentId);
        return new SubmissionHistory(homework, submissionRepository.findByHomeworkIdAndStudentId(homeworkId, studentId));
    }

    public PageResponse<HomeworkSubmission> listForManager(
            long homeworkId,
            long managerId,
            HomeworkSubmissionSearchCriteria criteria,
            int page,
            int size
    ) {
        Homework homework = findExistingHomework(homeworkId);
        requireManagePermission(homework.courseId(), managerId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return submissionRepository.findByHomeworkId(homeworkId, criteria, normalizedPage, normalizedSize);
    }

    public SubmissionDetail detail(long submissionId, long userId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "submission not found", HttpStatus.NOT_FOUND));
        Homework homework = findExistingHomework(submission.homeworkId());
        if (submission.studentId() == userId) {
            requireStudentCanViewHistory(homework, userId);
            return new SubmissionDetail(homework, submission, false);
        }
        requireManagePermission(homework.courseId(), userId);
        return new SubmissionDetail(homework, submission, true);
    }

    @Transactional
    public EvaluationDetail evaluationDetail(long submissionId, long userId) {
        SubmissionDetail detail = detail(submissionId, userId);
        if (!detail.managerView()) {
            requireEvaluationVisible(detail.homework());
        }
        HomeworkEvaluation evaluation = latestOrCreateEvaluation(detail.homework(), detail.submission(), userId);
        return new EvaluationDetail(detail.homework(), detail.submission(), evaluation, detail.managerView());
    }

    @Transactional
    public EvaluationDetail reevaluate(long submissionId, long managerId, String reason) {
        String normalizedReason = normalizeReason(reason);
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "submission not found", HttpStatus.NOT_FOUND));
        Homework homework = findExistingHomework(submission.homeworkId());
        requireManagePermission(homework.courseId(), managerId);
        if (homework.type() != HomeworkType.CODE && homework.type() != HomeworkType.OBJECTIVE) {
            throw new HomeworkApiException("HWK_4009", "homework type does not support automatic evaluation", HttpStatus.CONFLICT);
        }
        HomeworkEvaluation evaluation = homework.type() == HomeworkType.OBJECTIVE
                ? reevaluateObjective(homework, submission, managerId)
                : evaluateCodeSubmission(homework, submission, managerId, true, null);
        writeRejudgeLog(homework, submission, evaluation, managerId, normalizedReason);
        HomeworkSubmission updated = submissionRepository.findById(submissionId).orElse(submission);
        return new EvaluationDetail(homework, updated, evaluation, true);
    }

    public EvaluationDetail evaluationLogs(long evaluationId, long managerId) {
        HomeworkEvaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new HomeworkApiException("HWK_4009", "evaluation result not available", HttpStatus.NOT_FOUND));
        HomeworkSubmission submission = submissionRepository.findById(evaluation.submissionId())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "submission not found", HttpStatus.NOT_FOUND));
        Homework homework = findExistingHomework(submission.homeworkId());
        requireManagePermission(homework.courseId(), managerId);
        return new EvaluationDetail(homework, submission, evaluation, true);
    }

    public record SubmissionHistory(Homework homework, List<HomeworkSubmission> submissions) {
    }

    public record SubmittedHomeworkSubmission(Homework homework, HomeworkSubmission submission) {
    }

    public record SubmissionDetail(Homework homework, HomeworkSubmission submission, boolean managerView) {
    }

    public record EvaluationDetail(
            Homework homework,
            HomeworkSubmission submission,
            HomeworkEvaluation evaluation,
            boolean managerView
    ) {
    }

    private EvaluationStatus evaluationStatus(Homework homework, ObjectiveScore objectiveScore) {
        if (homework.type() == HomeworkType.CODE) {
            return EvaluationStatus.PENDING;
        }
        if (homework.type() == HomeworkType.OBJECTIVE) {
            return objectiveScore.score() >= homework.totalScore() ? EvaluationStatus.ACCEPTED : EvaluationStatus.WRONG_ANSWER;
        }
        return EvaluationStatus.NONE;
    }

    private HomeworkReviewStatus reviewStatus(Homework homework) {
        return switch (homework.type()) {
            case OBJECTIVE -> HomeworkReviewStatus.REVIEWED;
            case CODE -> HomeworkReviewStatus.NEED_REVIEW;
            case FILE, TEXT -> HomeworkReviewStatus.UNREVIEWED;
        };
    }

    private String submissionAnswerText(HomeworkType type, CreateHomeworkSubmissionCommand command) {
        return type == HomeworkType.CODE ? command.codeText() : command.answerText();
    }

    private ObjectiveScore calculateObjectiveScore(Homework homework, String answerJson) {
        JsonNode submitted = readJson(answerJson, "objective answer format is invalid");
        List<HomeworkQuestion> questions = homework.questions();
        int score = 0;
        int passed = 0;
        for (int index = 0; index < questions.size(); index += 1) {
            HomeworkQuestion question = questions.get(index);
            JsonNode expected = readJson(question.answerJson(), "objective answer key format is invalid");
            JsonNode actual = submittedAnswerForQuestion(submitted, question, index, questions.size());
            if (actual != null && sameAnswer(expected, actual)) {
                score += question.score();
                passed += 1;
            }
        }
        return new ObjectiveScore(Math.min(score, homework.totalScore()), passed, questions.size(), homework.totalScore());
    }

    private void createInitialEvaluation(
            Homework homework,
            HomeworkSubmission submission,
            ObjectiveScore objectiveScore,
            LocalDateTime now
    ) {
        if (homework.type() == HomeworkType.OBJECTIVE) {
            saveObjectiveEvaluation(homework, submission, objectiveScore, HomeworkEvaluationType.OBJECTIVE_AUTO, false, null, now);
            return;
        }
        if (homework.type() == HomeworkType.CODE) {
            evaluationRepository.save(new HomeworkEvaluation(
                    0L,
                    submission.id(),
                    homework.id(),
                    submission.studentId(),
                    HomeworkEvaluationType.CODE_JUDGE,
                    EvaluationStatus.PENDING,
                    0,
                    0,
                    homework.testCases().size(),
                    null,
                    null,
                    null,
                    "waiting for evaluation",
                    null,
                    null,
                    null,
                    false,
                    null,
                    now,
                    null,
                    now,
                    now
            ));
        }
    }

    private HomeworkEvaluation latestOrCreateEvaluation(Homework homework, HomeworkSubmission submission, long userId) {
        Optional<HomeworkEvaluation> latest = evaluationRepository.findLatestBySubmissionId(submission.id());
        if (homework.type() == HomeworkType.CODE
                && latest.map(HomeworkEvaluation::status).filter(this::isTerminalEvaluationStatus).isEmpty()) {
            return evaluateCodeSubmission(homework, submission, userId, false, latest.orElse(null));
        }
        if (latest.isPresent()) {
            return latest.get();
        }
        if (homework.type() == HomeworkType.OBJECTIVE) {
            return saveObjectiveEvaluation(
                    homework,
                    submission,
                    calculateObjectiveScore(homework, submission.answerJson()),
                    HomeworkEvaluationType.OBJECTIVE_AUTO,
                    false,
                    null,
                    LocalDateTime.now()
            );
        }
        throw new HomeworkApiException("HWK_4009", "evaluation result not available", HttpStatus.NOT_FOUND);
    }

    private HomeworkEvaluation reevaluateObjective(Homework homework, HomeworkSubmission submission, long managerId) {
        LocalDateTime now = LocalDateTime.now();
        ObjectiveScore score = calculateObjectiveScore(homework, submission.answerJson());
        HomeworkEvaluation evaluation = saveObjectiveEvaluation(
                homework,
                submission,
                score,
                HomeworkEvaluationType.REJUDGE,
                true,
                managerId,
                now
        );
        EvaluationStatus status = score.score() >= score.totalScore()
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
        submissionRepository.update(submission.withEvaluationResult(
                status,
                HomeworkReviewStatus.REVIEWED,
                score.score(),
                score.score(),
                now
        ));
        return evaluation;
    }

    private HomeworkEvaluation saveObjectiveEvaluation(
            Homework homework,
            HomeworkSubmission submission,
            ObjectiveScore score,
            HomeworkEvaluationType evaluationType,
            boolean reevaluation,
            Long triggeredBy,
            LocalDateTime now
    ) {
        EvaluationStatus status = score.score() >= score.totalScore()
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
        return evaluationRepository.save(new HomeworkEvaluation(
                0L,
                submission.id(),
                homework.id(),
                submission.studentId(),
                evaluationType,
                status,
                score.score(),
                score.passedCases(),
                score.totalCases(),
                0,
                null,
                null,
                "objective score %d, passed %d / %d".formatted(score.score(), score.passedCases(), score.totalCases()),
                null,
                null,
                null,
                reevaluation,
                triggeredBy,
                now,
                now,
                now,
                now
        ));
    }

    private HomeworkEvaluation evaluateCodeSubmission(
            Homework homework,
            HomeworkSubmission submission,
            long triggeredBy,
            boolean reevaluation,
            HomeworkEvaluation existing
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        List<CaseEvaluation> caseEvaluations = new ArrayList<>();
        for (HomeworkTestCase testCase : homework.testCases()) {
            EvaluationResult result = evaluator.evaluate(new EvaluationTask(
                    submission.id() + "-" + testCase.id() + (reevaluation ? "-retry" : ""),
                    "HWK",
                    homework.courseId(),
                    homework.id(),
                    submission.id(),
                    submission.studentId(),
                    submission.language(),
                    submission.answerText(),
                    Map.of(
                            "stdin", testCase.inputData(),
                            "expectedOutput", testCase.expectedOutput(),
                            "timeLimitMs", Integer.toString(testCase.timeLimitMs()),
                            "memoryLimitKb", Integer.toString(testCase.memoryLimitKb())
                    ),
                    submission.submittedAt()
            ));
            boolean passed = result.status() == EvaluationStatus.ACCEPTED;
            caseEvaluations.add(new CaseEvaluation(
                    result.status(),
                    passed,
                    passed ? testCase.scoreWeight() : 0,
                    result.message(),
                    result.caseResults().isEmpty() ? "" : result.caseResults().get(0)
            ));
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        EvaluationStatus finalStatus = resolveCodeEvaluationStatus(caseEvaluations);
        int score = caseEvaluations.stream().mapToInt(CaseEvaluation::score).sum();
        int passedCases = (int) caseEvaluations.stream().filter(CaseEvaluation::passed).count();
        String runLog = caseEvaluations.stream()
                .map(CaseEvaluation::caseOutput)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        String feedback = caseEvaluations.stream()
                .map(CaseEvaluation::message)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(resolveEvaluationMessage(finalStatus, passedCases, caseEvaluations.size()));
        HomeworkEvaluation completed = new HomeworkEvaluation(
                existing == null || reevaluation ? 0L : existing.id(),
                submission.id(),
                homework.id(),
                submission.studentId(),
                reevaluation ? HomeworkEvaluationType.REJUDGE
                        : existing == null ? HomeworkEvaluationType.CODE_JUDGE : existing.evaluationType(),
                finalStatus,
                score,
                passedCases,
                caseEvaluations.size(),
                Math.toIntExact(Duration.between(startedAt, finishedAt).toMillis()),
                null,
                isFailureStatus(finalStatus) ? feedback : null,
                feedback,
                null,
                findFirstMessage(caseEvaluations, EvaluationStatus.COMPILE_ERROR),
                runLog,
                reevaluation,
                reevaluation ? triggeredBy : null,
                startedAt,
                finishedAt,
                existing == null || reevaluation ? startedAt : existing.createdAt(),
                finishedAt
        );
        HomeworkEvaluation saved = existing == null || reevaluation
                ? evaluationRepository.save(completed)
                : evaluationRepository.update(completed);
        HomeworkReviewStatus reviewStatus = finalStatus == EvaluationStatus.ACCEPTED
                ? HomeworkReviewStatus.REVIEWED
                : HomeworkReviewStatus.NEED_REVIEW;
        Integer finalScore = finalStatus == EvaluationStatus.ACCEPTED ? score : null;
        submissionRepository.update(submission.withEvaluationResult(finalStatus, reviewStatus, score, finalScore, finishedAt));
        return saved;
    }

    private EvaluationStatus resolveCodeEvaluationStatus(List<CaseEvaluation> caseEvaluations) {
        if (caseEvaluations.isEmpty()) {
            return EvaluationStatus.SYSTEM_ERROR;
        }
        if (containsStatus(caseEvaluations, EvaluationStatus.SYSTEM_ERROR)) {
            return EvaluationStatus.SYSTEM_ERROR;
        }
        if (containsStatus(caseEvaluations, EvaluationStatus.COMPILE_ERROR)) {
            return EvaluationStatus.COMPILE_ERROR;
        }
        if (containsStatus(caseEvaluations, EvaluationStatus.RUNTIME_ERROR)) {
            return EvaluationStatus.RUNTIME_ERROR;
        }
        if (containsStatus(caseEvaluations, EvaluationStatus.TIME_LIMIT_EXCEEDED)) {
            return EvaluationStatus.TIME_LIMIT_EXCEEDED;
        }
        return caseEvaluations.stream().allMatch(CaseEvaluation::passed)
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
    }

    private boolean containsStatus(List<CaseEvaluation> caseEvaluations, EvaluationStatus status) {
        return caseEvaluations.stream().anyMatch(item -> item.status() == status);
    }

    private String findFirstMessage(List<CaseEvaluation> caseEvaluations, EvaluationStatus status) {
        return caseEvaluations.stream()
                .filter(item -> item.status() == status)
                .map(CaseEvaluation::message)
                .findFirst()
                .orElse(null);
    }

    private String resolveEvaluationMessage(EvaluationStatus status, int passedCases, int totalCases) {
        return switch (status) {
            case ACCEPTED -> "all cases passed";
            case WRONG_ANSWER -> "passed %d / %d cases".formatted(passedCases, totalCases);
            case COMPILE_ERROR -> "compile error";
            case RUNTIME_ERROR -> "runtime error";
            case TIME_LIMIT_EXCEEDED -> "time limit exceeded";
            case SYSTEM_ERROR -> "evaluation failed";
            case RUNNING -> "evaluation running";
            case PENDING -> "waiting for evaluation";
            default -> status.name();
        };
    }

    private boolean isFailureStatus(EvaluationStatus status) {
        return status != EvaluationStatus.ACCEPTED && status != EvaluationStatus.NONE;
    }

    private boolean isTerminalEvaluationStatus(EvaluationStatus status) {
        return status != EvaluationStatus.PENDING && status != EvaluationStatus.RUNNING;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalidFormat("reevaluation reason is required");
        }
        return reason.trim();
    }

    private void writeRejudgeLog(
            Homework homework,
            HomeworkSubmission submission,
            HomeworkEvaluation evaluation,
            long managerId,
            String reason
    ) {
        reviewLogRepository.save(new HomeworkReviewLog(
                0L,
                submission.id(),
                homework.id(),
                submission.studentId(),
                HomeworkReviewOperationType.REJUDGE,
                submission.autoScore(),
                evaluation.score(),
                null,
                managerId,
                reason,
                LocalDateTime.now()
        ));
    }

    private record ObjectiveScore(int score, int passedCases, int totalCases, int totalScore) {
    }

    private record CaseEvaluation(
            EvaluationStatus status,
            boolean passed,
            int score,
            String message,
            String caseOutput
    ) {
    }

    private JsonNode submittedAnswerForQuestion(JsonNode submitted, HomeworkQuestion question, int index, int questionCount) {
        if (submitted.isObject()) {
            String[] keys = {
                    String.valueOf(question.id()),
                    String.valueOf(question.sortOrder()),
                    "q" + question.sortOrder()
            };
            for (String key : keys) {
                if (submitted.has(key)) {
                    return submitted.get(key);
                }
            }
            return null;
        }
        if (submitted.isArray() && questionCount > 1) {
            return submitted.size() > index ? submitted.get(index) : null;
        }
        return questionCount == 1 ? submitted : null;
    }

    private boolean sameAnswer(JsonNode expected, JsonNode actual) {
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.isArray() && expected.size() == 1 && actual.isValueNode()) {
            return expected.get(0).asText().equals(actual.asText());
        }
        if (actual.isArray() && actual.size() == 1 && expected.isValueNode()) {
            return actual.get(0).asText().equals(expected.asText());
        }
        return false;
    }

    private JsonNode readJson(String value, String message) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            throw invalidFormat(message);
        }
    }

    private void requireStudentCanSubmit(Homework homework, long studentId) {
        if (!coursePermissionClient.canViewCourse(homework.courseId(), studentId)) {
            throw new HomeworkApiException("HWK_4031", "course access denied", HttpStatus.FORBIDDEN);
        }
        if (homework.status() != HomeworkStatus.PUBLISHED) {
            if (homework.status() == HomeworkStatus.CLOSED) {
                throw new HomeworkApiException("HWK_4003", "homework is closed", HttpStatus.CONFLICT);
            }
            throw new HomeworkApiException("HWK_4002", "homework is not published", HttpStatus.FORBIDDEN);
        }
        if (LocalDateTime.now().isAfter(homework.deadline()) && !homework.allowLateSubmit()) {
            throw new HomeworkApiException("HWK_4004", "deadline exceeded", HttpStatus.CONFLICT);
        }
    }

    private Homework findExistingHomework(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "homework not found", HttpStatus.NOT_FOUND));
    }

    private void requireStudentCanViewHistory(Homework homework, long studentId) {
        if (!coursePermissionClient.canViewCourse(homework.courseId(), studentId)) {
            throw new HomeworkApiException("HWK_4031", "course access denied", HttpStatus.FORBIDDEN);
        }
        if (!STUDENT_VISIBLE_STATUSES.contains(homework.status())) {
            throw new HomeworkApiException("HWK_4002", "homework is not published", HttpStatus.FORBIDDEN);
        }
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new HomeworkApiException("HWK_4031", "course management permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireEvaluationVisible(Homework homework) {
        boolean scorePublished = homework.status() == HomeworkStatus.SCORE_PUBLISHED
                || homework.status() == HomeworkStatus.ARCHIVED;
        if (!homework.showEvaluationBeforePublish() && !scorePublished) {
            throw new HomeworkApiException("HWK_4010", "evaluation result is not visible", HttpStatus.FORBIDDEN);
        }
    }

    private CreateHomeworkSubmissionCommand normalize(CreateHomeworkSubmissionCommand command) {
        if (command == null) {
            return new CreateHomeworkSubmissionCommand(null, null, null, null, null);
        }
        return new CreateHomeworkSubmissionCommand(
                blankToNull(command.answerText()),
                blankToNull(command.answerJson()),
                blankToNull(command.fileIds()),
                blankToNull(command.codeText()),
                command.language() == null ? null : blankToNull(command.language().toLowerCase(Locale.ROOT))
        );
    }

    private void validateContent(Homework homework, CreateHomeworkSubmissionCommand command) {
        HomeworkType type = homework.type();
        boolean hasText = command.answerText() != null;
        boolean hasAnswerJson = command.answerJson() != null;
        boolean hasFile = command.fileIds() != null;
        boolean hasCode = command.codeText() != null;
        if (type == HomeworkType.OBJECTIVE && !hasAnswerJson) {
            throw invalidFormat("objective homework requires answers");
        }
        if (type == HomeworkType.TEXT && !hasText && !hasAnswerJson && !hasFile) {
            throw invalidFormat("text homework requires answer content");
        }
        if (type == HomeworkType.FILE && !hasFile) {
            throw invalidFormat("file homework requires attachments");
        }
        if (type == HomeworkType.CODE && (!hasCode || command.language() == null)) {
            throw invalidFormat("code homework requires code and language");
        }
        if (type == HomeworkType.CODE) {
            validateCodeLanguage(homework, command.language());
        }
    }

    private void validateCodeLanguage(Homework homework, String language) {
        List<String> allowedLanguages = allowedLanguages(homework);
        if (!allowedLanguages.isEmpty() && !allowedLanguages.contains(language)) {
            throw invalidFormat("code language is not supported");
        }
    }

    private List<String> allowedLanguages(Homework homework) {
        if (homework.judgeConfig() == null || homework.judgeConfig().languageLimitJson() == null) {
            return List.of();
        }
        String value = homework.judgeConfig().languageLimitJson().trim();
        if (value.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(value);
            if (!node.isArray()) {
                throw invalidFormat("code language allowlist format is invalid");
            }
            List<String> languages = new ArrayList<>();
            node.forEach(item -> {
                String normalized = item.asText("").toLowerCase(Locale.ROOT).trim();
                if (!normalized.isEmpty()) {
                    languages.add(normalized);
                }
            });
            return languages;
        } catch (HomeworkApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidFormat("code language allowlist format is invalid");
        }
    }

    private HomeworkApiException invalidFormat(String message) {
        return new HomeworkApiException("HWK_4005", message, HttpStatus.BAD_REQUEST);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
