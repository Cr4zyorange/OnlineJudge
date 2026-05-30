package com.onlinejudge.hwk.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.CreateHomeworkCommand;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkJudgeConfig;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HomeworkService {
    private static final Logger log = LoggerFactory.getLogger(HomeworkService.class);

    private final HomeworkRepository repository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public HomeworkService(
            HomeworkRepository repository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.repository = repository;
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
        HomeworkStatus queryStatus = canManage ? status : HomeworkStatus.PUBLISHED;
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<Homework> homeworks = repository.findByCourseId(courseId, queryStatus, keyword, normalizedPage, normalizedSize);
        long total = repository.countByCourseId(courseId, queryStatus, keyword);
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
