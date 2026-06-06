package com.onlinejudge.hwk.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.CreateHomeworkCommand;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkJudgeConfig;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewOperationType;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class HomeworkService {
    private static final Logger log = LoggerFactory.getLogger(HomeworkService.class);
    private static final List<HomeworkStatus> STUDENT_VISIBLE_STATUSES = List.of(
            HomeworkStatus.PUBLISHED,
            HomeworkStatus.CLOSED,
            HomeworkStatus.SCORE_PUBLISHED,
            HomeworkStatus.ARCHIVED
    );

    private final HomeworkRepository repository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkReviewLogRepository reviewLogRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public HomeworkService(
            HomeworkRepository repository,
            HomeworkSubmissionRepository submissionRepository,
            HomeworkReviewLogRepository reviewLogRepository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.repository = repository;
        this.submissionRepository = submissionRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public Homework create(long teacherId, CreateHomeworkCommand command) {
        requireManagePermission(command.courseId(), teacherId);
        validate(command);
        LocalDateTime now = LocalDateTime.now();
        return repository.save(new Homework(
                0L,
                command.courseId(),
                command.chapterId(),
                command.title().trim(),
                command.description() == null ? "" : command.description().trim(),
                command.type(),
                HomeworkStatus.DRAFT,
                command.totalScore(),
                command.deadline(),
                command.allowResubmit(),
                command.allowLateSubmit(),
                command.showEvaluationBeforePublish(),
                null,
                teacherId,
                null,
                false,
                now,
                now,
                normalizeQuestions(command.questions(), now),
                normalizeTestCases(command.testCases(), now),
                normalizeJudgeConfig(command.judgeConfig(), now)
        ));
    }

    @Transactional
    public Homework update(long homeworkId, long teacherId, CreateHomeworkCommand command) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != HomeworkStatus.DRAFT) {
            throw new HomeworkApiException("HWK_4091", "only draft homework can be edited", HttpStatus.CONFLICT);
        }
        if (existing.courseId() != command.courseId()) {
            throw new HomeworkApiException("HWK_4001", "courseId cannot be changed", HttpStatus.BAD_REQUEST);
        }
        validate(command);
        LocalDateTime now = LocalDateTime.now();
        return repository.update(existing.update(
                command.chapterId(),
                command.title().trim(),
                command.description() == null ? "" : command.description().trim(),
                command.type(),
                command.totalScore(),
                command.deadline(),
                command.allowResubmit(),
                command.allowLateSubmit(),
                command.showEvaluationBeforePublish(),
                now,
                normalizeQuestions(command.questions(), now),
                normalizeTestCases(command.testCases(), now),
                normalizeJudgeConfig(command.judgeConfig(), now)
        ));
    }

    public PageResponse<Homework> list(long userId, long courseId, HomeworkStatus status, String keyword, int page, int size) {
        requireViewPermission(courseId, userId);
        boolean canManage = coursePermissionClient.canManageCourse(courseId, userId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        if (canManage) {
            List<Homework> homeworks = repository.findByCourseId(courseId, status, keyword, normalizedPage, normalizedSize);
            long total = repository.countByCourseId(courseId, status, keyword);
            return new PageResponse<>(homeworks, total, normalizedPage, normalizedSize);
        }
        if (status != null && !STUDENT_VISIBLE_STATUSES.contains(status)) {
            return new PageResponse<>(List.of(), 0, normalizedPage, normalizedSize);
        }
        List<HomeworkStatus> visibleStatuses = status == null ? STUDENT_VISIBLE_STATUSES : List.of(status);
        List<Homework> homeworks = repository.findByCourseIdAndStatuses(
                courseId,
                visibleStatuses,
                keyword,
                normalizedPage,
                normalizedSize
        );
        long total = repository.countByCourseIdAndStatuses(courseId, visibleStatuses, keyword);
        return new PageResponse<>(homeworks, total, normalizedPage, normalizedSize);
    }

    public Homework detail(long homeworkId, long userId) {
        Homework homework = findExisting(homeworkId);
        requireViewPermission(homework.courseId(), userId);
        if (!coursePermissionClient.canManageCourse(homework.courseId(), userId)
                && homework.status() != HomeworkStatus.PUBLISHED
                && homework.status() != HomeworkStatus.CLOSED
                && homework.status() != HomeworkStatus.SCORE_PUBLISHED
                && homework.status() != HomeworkStatus.ARCHIVED) {
            throw new HomeworkApiException("HWK_4002", "homework is not published", HttpStatus.FORBIDDEN);
        }
        return homework;
    }

    public boolean canManageCourse(long courseId, long userId) {
        return coursePermissionClient.canManageCourse(courseId, userId);
    }

    @Transactional
    public Homework saveQuestions(long homeworkId, long teacherId, List<HomeworkQuestion> questions) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        requireDraft(existing);
        validateQuestions(questions);
        return repository.replaceQuestions(homeworkId, normalizeQuestions(questions, LocalDateTime.now()));
    }

    @Transactional
    public Homework saveTestCases(long homeworkId, long teacherId, List<HomeworkTestCase> testCases) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        requireDraft(existing);
        validateTestCases(testCases);
        return repository.replaceTestCases(homeworkId, normalizeTestCases(testCases, LocalDateTime.now()));
    }

    public List<HomeworkTestCase> testCases(long homeworkId, long managerId) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), managerId);
        return existing.testCases();
    }

    @Transactional
    public Homework publish(long homeworkId, long teacherId) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != HomeworkStatus.DRAFT) {
            throw new HomeworkApiException("HWK_4092", "current homework state cannot be published", HttpStatus.CONFLICT);
        }
        if (existing.type() == HomeworkType.CODE && existing.testCases().isEmpty()) {
            throw new HomeworkApiException("HWK_4007", "code homework requires at least one test case", HttpStatus.CONFLICT);
        }
        if (existing.type() == HomeworkType.OBJECTIVE && existing.questions().isEmpty()) {
            throw new HomeworkApiException("HWK_4005", "objective homework requires at least one question", HttpStatus.BAD_REQUEST);
        }
        Homework published = repository.update(existing.publish(LocalDateTime.now()));
        try {
            notificationEventPublisher.publish(new NotificationEvent(
                    "homework-published-" + published.id() + "-" + published.updatedAt(),
                    "HOMEWORK_PUBLISHED",
                    published.courseId(),
                    coursePermissionClient.listCourseStudentIds(published.courseId()),
                    "homework published",
                    "New homework: " + published.title(),
                    "HWK",
                    published.id(),
                    "/courses/" + published.courseId() + "/homeworks/" + published.id(),
                    published.updatedAt()
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to publish HOMEWORK_PUBLISHED event for homework {}: {}", published.id(), ex.toString());
        }
        return published;
    }

    @Transactional
    public Homework close(long homeworkId, long teacherId) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != HomeworkStatus.PUBLISHED) {
            throw new HomeworkApiException("HWK_4093", "current homework state cannot be closed", HttpStatus.CONFLICT);
        }
        return repository.update(existing.close(LocalDateTime.now()));
    }

    @Transactional
    public Homework publishScores(long homeworkId, long teacherId) {
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() == HomeworkStatus.SCORE_PUBLISHED) {
            return existing;
        }
        if (existing.status() != HomeworkStatus.PUBLISHED
                && existing.status() != HomeworkStatus.CLOSED) {
            throw new HomeworkApiException("HWK_4094", "current homework state cannot publish scores", HttpStatus.CONFLICT);
        }
        List<HomeworkSubmission> scoredSubmissions = finalSubmissions(homeworkId).stream()
                .filter(submission -> effectiveScore(submission).isPresent())
                .toList();
        LocalDateTime now = LocalDateTime.now();
        Homework published = repository.update(existing.publishScores(now));
        for (HomeworkSubmission submission : scoredSubmissions) {
            reviewLogRepository.save(new HomeworkReviewLog(
                    0L,
                    submission.id(),
                    homeworkId,
                    submission.studentId(),
                    HomeworkReviewOperationType.PUBLISH,
                    null,
                    effectiveScore(submission).orElse(null),
                    submission.comment(),
                    teacherId,
                    "homework score published",
                    now
            ));
        }
        publishScoreNotification(published, scoredSubmissions, now);
        return published;
    }

    public HomeworkStatistics statistics(long homeworkId, long managerId) {
        Homework homework = findExisting(homeworkId);
        requireManagePermission(homework.courseId(), managerId);
        List<Long> courseStudentIds = coursePermissionClient.listCourseStudentIds(homework.courseId()).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<HomeworkSubmission> submissions = finalSubmissions(homeworkId);
        Set<Long> submittedStudentIds = new HashSet<>();
        submissions.forEach(submission -> submittedStudentIds.add(submission.studentId()));
        List<Long> unsubmittedStudentIds = courseStudentIds.stream()
                .filter(studentId -> !submittedStudentIds.contains(studentId))
                .toList();
        List<BigDecimal> scores = submissions.stream()
                .map(this::effectiveScore)
                .flatMap(Optional::stream)
                .toList();
        BigDecimal average = scores.isEmpty() ? null : scores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        BigDecimal max = scores.stream().max(Comparator.naturalOrder()).orElse(null);
        BigDecimal min = scores.stream().min(Comparator.naturalOrder()).orElse(null);
        int evaluatedCount = (int) submissions.stream().filter(this::evaluationCompleted).count();
        int reviewedCount = (int) submissions.stream()
                .filter(submission -> submission.reviewStatus() == HomeworkReviewStatus.REVIEWED)
                .count();
        int totalStudentCount = courseStudentIds.isEmpty() ? submittedStudentIds.size() : courseStudentIds.size();
        return new HomeworkStatistics(
                homework.id(),
                homework.courseId(),
                totalStudentCount,
                submittedStudentIds.size(),
                Math.max(totalStudentCount - submittedStudentIds.size(), unsubmittedStudentIds.size()),
                evaluatedCount,
                reviewedCount,
                average,
                max,
                min,
                unsubmittedStudentIds
        );
    }

    public List<HomeworkSubmission> finalSubmissions(long homeworkId) {
        return submissionRepository.findFinalByHomeworkId(homeworkId);
    }

    public Optional<BigDecimal> effectiveScore(HomeworkSubmission submission) {
        if (submission.finalScore() != null) {
            return Optional.of(submission.finalScore());
        }
        if (submission.autoScore() != null) {
            return Optional.of(BigDecimal.valueOf(submission.autoScore()));
        }
        return Optional.empty();
    }

    public record HomeworkStatistics(
            long homeworkId,
            long courseId,
            int totalStudentCount,
            int submittedCount,
            int unsubmittedCount,
            int evaluatedCount,
            int reviewedCount,
            BigDecimal averageScore,
            BigDecimal maxScore,
            BigDecimal minScore,
            List<Long> unsubmittedStudentIds
    ) {
    }

    private Homework findExisting(long homeworkId) {
        return repository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "homework not found", HttpStatus.NOT_FOUND));
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new HomeworkApiException("HWK_4031", "course management permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireViewPermission(long courseId, long userId) {
        if (!coursePermissionClient.canViewCourse(courseId, userId)) {
            throw new HomeworkApiException("HWK_4031", "course access denied", HttpStatus.FORBIDDEN);
        }
    }

    private boolean evaluationCompleted(HomeworkSubmission submission) {
        return submission.evaluationStatus() != com.onlinejudge.common.evaluation.EvaluationStatus.NONE
                && submission.evaluationStatus() != com.onlinejudge.common.evaluation.EvaluationStatus.PENDING
                && submission.evaluationStatus() != com.onlinejudge.common.evaluation.EvaluationStatus.RUNNING;
    }

    private void publishScoreNotification(
            Homework homework,
            List<HomeworkSubmission> scoredSubmissions,
            LocalDateTime occurredAt
    ) {
        List<Long> recipients = scoredSubmissions.stream()
                .map(HomeworkSubmission::studentId)
                .distinct()
                .sorted()
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        try {
            notificationEventPublisher.publish(new NotificationEvent(
                    "homework-score-published-" + homework.id() + "-" + occurredAt,
                    "HOMEWORK_SCORE_PUBLISHED",
                    homework.courseId(),
                    recipients,
                    "homework score published",
                    "Homework score published: " + homework.title(),
                    "HWK",
                    homework.id(),
                    "/courses/" + homework.courseId() + "/homeworks/" + homework.id(),
                    occurredAt
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to publish HOMEWORK_SCORE_PUBLISHED event for homework {}: {}", homework.id(), ex.toString());
        }
    }

    private void requireDraft(Homework homework) {
        if (homework.status() != HomeworkStatus.DRAFT) {
            throw new HomeworkApiException("HWK_4091", "only draft homework can be configured", HttpStatus.CONFLICT);
        }
    }

    private void validate(CreateHomeworkCommand command) {
        if (command.courseId() <= 0) {
            throw new HomeworkApiException("HWK_4005", "courseId is required", HttpStatus.BAD_REQUEST);
        }
        if (command.title() == null || command.title().trim().isEmpty() || command.title().trim().length() > 255) {
            throw new HomeworkApiException("HWK_4005", "title is required and must be within 255 characters", HttpStatus.BAD_REQUEST);
        }
        if (command.description() == null || command.description().trim().isEmpty()) {
            throw new HomeworkApiException("HWK_4005", "description is required", HttpStatus.BAD_REQUEST);
        }
        if (command.type() == null) {
            throw new HomeworkApiException("HWK_4005", "homework type is required", HttpStatus.BAD_REQUEST);
        }
        if (command.deadline() == null || !command.deadline().isAfter(LocalDateTime.now())) {
            throw new HomeworkApiException("HWK_4005", "deadline must be in the future", HttpStatus.BAD_REQUEST);
        }
        if (command.totalScore() <= 0) {
            throw new HomeworkApiException("HWK_4005", "totalScore must be greater than 0", HttpStatus.BAD_REQUEST);
        }
        validateQuestions(command.questions());
        validateTestCases(command.testCases());
        if (command.type() == HomeworkType.CODE && command.judgeConfig() != null) {
            validateJudgeConfig(command.judgeConfig());
        }
    }

    private void validateQuestions(List<HomeworkQuestion> questions) {
        if (questions == null) {
            return;
        }
        for (HomeworkQuestion question : questions) {
            if (question.questionType() == null || question.questionType().isBlank()
                    || question.stem() == null || question.stem().isBlank()
                    || question.answerJson() == null || question.answerJson().isBlank()
                    || question.score() <= 0) {
                throw new HomeworkApiException("HWK_4005", "question type, stem, answer and positive score are required", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateTestCases(List<HomeworkTestCase> testCases) {
        if (testCases == null) {
            return;
        }
        for (HomeworkTestCase testCase : testCases) {
            if (testCase.inputData() == null || testCase.expectedOutput() == null
                    || testCase.scoreWeight() < 0 || testCase.timeLimitMs() <= 0 || testCase.memoryLimitKb() <= 0) {
                throw new HomeworkApiException("HWK_4005", "test case input, output and resource limits are required", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateJudgeConfig(HomeworkJudgeConfig judgeConfig) {
        if (judgeConfig.timeLimitMs() <= 0 || judgeConfig.memoryLimitKb() <= 0
                || judgeConfig.outputCompareMode() == null || judgeConfig.outputCompareMode().isBlank()) {
            throw new HomeworkApiException("HWK_4005", "judge config resource limits and compare mode are required", HttpStatus.BAD_REQUEST);
        }
    }

    private List<HomeworkQuestion> normalizeQuestions(List<HomeworkQuestion> questions, LocalDateTime now) {
        if (questions == null) {
            return List.of();
        }
        return questions.stream()
                .map(question -> new HomeworkQuestion(0L, 0L, question.questionType().trim(), question.stem().trim(),
                        blankToNull(question.optionsJson()), question.answerJson().trim(), question.score(),
                        question.sortOrder(), now, now))
                .toList();
    }

    private List<HomeworkTestCase> normalizeTestCases(List<HomeworkTestCase> testCases, LocalDateTime now) {
        if (testCases == null) {
            return List.of();
        }
        return testCases.stream()
                .map(testCase -> new HomeworkTestCase(0L, 0L, testCase.inputData(), testCase.expectedOutput(),
                        testCase.scoreWeight(), testCase.hidden(), testCase.timeLimitMs(), testCase.memoryLimitKb(),
                        testCase.sortOrder(), now, now))
                .toList();
    }

    private HomeworkJudgeConfig normalizeJudgeConfig(HomeworkJudgeConfig judgeConfig, LocalDateTime now) {
        if (judgeConfig == null) {
            return null;
        }
        return new HomeworkJudgeConfig(0L, 0L, blankToNull(judgeConfig.languageLimitJson()),
                judgeConfig.timeLimitMs(), judgeConfig.memoryLimitKb(),
                judgeConfig.outputCompareMode() == null ? "EXACT" : judgeConfig.outputCompareMode().trim(), now, now);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
