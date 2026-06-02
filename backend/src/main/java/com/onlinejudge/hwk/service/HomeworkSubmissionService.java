package com.onlinejudge.hwk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.CreateHomeworkSubmissionCommand;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class HomeworkSubmissionService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final CoursePermissionClient coursePermissionClient;

    public HomeworkSubmissionService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            CoursePermissionClient coursePermissionClient
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    @Transactional
    public HomeworkSubmission submit(long homeworkId, long studentId, CreateHomeworkSubmissionCommand command) {
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
        EvaluationStatus evaluationStatus = evaluationStatus(homework, normalized);
        HomeworkReviewStatus reviewStatus = reviewStatus(homework);
        Integer autoScore = homework.type() == HomeworkType.OBJECTIVE ? calculateObjectiveScore(homework, normalized.answerJson()) : null;
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
        return submissionRepository.save(submission);
    }

    private EvaluationStatus evaluationStatus(Homework homework, CreateHomeworkSubmissionCommand command) {
        if (homework.type() == HomeworkType.CODE) {
            return EvaluationStatus.PENDING;
        }
        if (homework.type() == HomeworkType.OBJECTIVE) {
            int score = calculateObjectiveScore(homework, command.answerJson());
            return score >= homework.totalScore() ? EvaluationStatus.ACCEPTED : EvaluationStatus.WRONG_ANSWER;
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

    private int calculateObjectiveScore(Homework homework, String answerJson) {
        JsonNode submitted = readJson(answerJson, "objective answer format is invalid");
        List<HomeworkQuestion> questions = homework.questions();
        int score = 0;
        for (int index = 0; index < questions.size(); index += 1) {
            HomeworkQuestion question = questions.get(index);
            JsonNode expected = readJson(question.answerJson(), "objective answer key format is invalid");
            JsonNode actual = submittedAnswerForQuestion(submitted, question, index, questions.size());
            if (actual != null && sameAnswer(expected, actual)) {
                score += question.score();
            }
        }
        return Math.min(score, homework.totalScore());
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
