package com.onlinejudge.assessmentservice.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CourseMembershipGuard;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.HomeworkService;
import com.onlinejudge.assessmentservice.service.HomeworkStatisticsService;
import com.onlinejudge.assessmentservice.service.HomeworkSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HomeworkController {
    private static final Logger log = LoggerFactory.getLogger(HomeworkController.class);
    private final HomeworkService homeworks;
    private final CoursePermissionClient coursePermissions;
    private final CourseMembershipGuard membershipGuard;
    private final HomeworkStatisticsService statistics;
    private final HomeworkSubmissionService submissions;

    public HomeworkController(HomeworkService homeworks, CoursePermissionClient coursePermissions,
            CourseMembershipGuard membershipGuard, HomeworkStatisticsService statistics, HomeworkSubmissionService submissions) {
        this.homeworks = homeworks;
        this.coursePermissions = coursePermissions;
        this.membershipGuard = membershipGuard;
        this.statistics = statistics;
        this.submissions = submissions;
    }

    @PostMapping(path = "/homeworks/{homeworkId}/submissions", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(@PathVariable long homeworkId,
            @Valid @RequestBody SubmitHomeworkRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requestId(http);
        if (!user.hasRole("STUDENT")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "student role is required");
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        if (!membershipGuard.isActiveMember(homework.courseId(), user.id(), requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        }
        try {
            HomeworkSubmissionService.SubmittedHomework submitted = request.answerText() != null && !request.answerText().isBlank()
                    ? submissions.submitText(homeworkId, user.id(), request.answerText())
                    : request.answerJson() != null && !request.answerJson().isBlank()
                    ? submissions.submitObjective(homeworkId, user.id(), request.answerJson())
                    : request.fileIds() != null
                    ? submissions.submitFile(homeworkId, user.id(), request.fileIds())
                    : submissions.submit(homeworkId, user.id(), request.codeText(), request.language());
            if (submitted.taskId() != null) {
                log.info("homework_submission_queued publicSubmissionId={} submissionId={} taskId={}",
                        submitted.publicSubmissionId(), submitted.submissionId(), submitted.taskId());
            }
            return success(submissionResponse(submissions.find(submitted.publicSubmissionId())));
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (HomeworkSubmissionService.DeadlineExceededException deadlineExceeded) {
            throw new HomeworkClientException(HttpStatus.CONFLICT, "HWK_4004", deadlineExceeded.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        } catch (UncheckedIOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage(), unavailable);
        }
    }

    @PostMapping(path = "/homeworks/{homeworkId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> uploadAttachment(@PathVariable long homeworkId, @org.springframework.web.bind.annotation.RequestParam MultipartFile file,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requestId(http);
        if (!user.hasRole("STUDENT")) throw new HomeworkClientException(HttpStatus.FORBIDDEN, "HWK_4031", "student role is required");
        HomeworkService.HomeworkSummary homework;
        try { homework = homeworks.find(homeworkId); }
        catch (NoSuchElementException missing) { throw new HomeworkClientException(HttpStatus.NOT_FOUND, "HWK_4042", "homework not found"); }
        if (!membershipGuard.isActiveMember(homework.courseId(), user.id(), requestId)) {
            throw new HomeworkClientException(HttpStatus.FORBIDDEN, "HWK_4031", "active course student membership is required");
        }
        if (!"FILE".equals(homework.type()) || !"PUBLISHED".equals(homework.status())) {
            throw new HomeworkClientException(HttpStatus.BAD_REQUEST, "HWK_4005", "homework does not accept attachments");
        }
        if (file == null || file.isEmpty() || !trustedAttachment(file)) {
            throw new HomeworkClientException(HttpStatus.BAD_REQUEST, "HWK_4005", "attachment format is invalid");
        }
        try {
            HomeworkSubmissionService.AttachmentUpload stored = submissions.uploadFile(homeworkId, user.id(),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileId", stored.fileId());
            data.put("originalFilename", stored.originalFilename());
            data.put("contentType", stored.contentType());
            data.put("fileSize", stored.fileSize());
            data.put("status", "UPLOADED");
            data.put("expiresAt", stored.expiresAt());
            data.put("uploadedAt", stored.uploadedAt());
            return success(data);
        } catch (java.io.IOException storageFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "attachment storage unavailable", storageFailure);
        }
    }

    @GetMapping("/submissions/{submissionId}")
    public Map<String, Object> submission(@PathVariable long submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        HomeworkSubmissionService.SubmissionView found;
        try {
            found = submissions.find(submissionId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework submission not found", missing);
        }
        if (!found.studentId().equals(user.id())) requireManager(found.courseId(), user);
        return success(submissionResponse(found));
    }

    @PutMapping("/submissions/{submissionId}/review")
    public Map<String, Object> review(@PathVariable long submissionId,
            @Valid @RequestBody ReviewHomeworkRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        HomeworkSubmissionService.SubmissionView found;
        try {
            found = submissions.find(submissionId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework submission not found", missing);
        }
        requireManager(found.courseId(), user);
        try {
            return success(submissionResponse(submissions.review(submissionId, user.id(), request.manualScore(),
                    request.finalScore(), request.comment())));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        }
    }

    @PostMapping("/homeworks")
    @ResponseStatus(HttpStatus.CREATED)
    public HomeworkService.HomeworkSummary create(@Valid @RequestBody CreateHomeworkRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requestId(http);
        requireManager(request.courseId(), user);
        try {
            return homeworks.create(request.toCommand(), user.id());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }

    @GetMapping("/homeworks")
    public Map<String, Object> list(@RequestParam String courseId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        boolean manager = (user.hasRole("TEACHER") || user.hasRole("ASSISTANT"))
                && coursePermissions.canManageCourse(courseId, user.id());
        if (!manager && !membershipGuard.isActiveMember(courseId, user.id(), null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        List<Map<String, Object>> records = homeworks.list(courseId, manager).stream()
                .map(homework -> homeworkResponse(homework, manager)).toList();
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", records);
        page.put("total", records.size());
        page.put("page", 1);
        page.put("size", records.size());
        return success(page);
    }

    @GetMapping("/homeworks/{homeworkId}")
    public Map<String, Object> detail(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        boolean manager = (user.hasRole("TEACHER") || user.hasRole("ASSISTANT"))
                && coursePermissions.canManageCourse(homework.courseId(), user.id());
        if (!manager && !membershipGuard.isActiveMember(homework.courseId(), user.id(), null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        if (!manager && "DRAFT".equals(homework.status())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found");
        }
        return success(homeworkResponse(homework, manager));
    }

    @GetMapping("/homeworks/{homeworkId}/my-submissions")
    public Map<String, Object> mySubmissions(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        if (!user.hasRole("STUDENT")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "student role is required");
        HomeworkService.HomeworkSummary homework = homeworks.find(homeworkId);
        if (!membershipGuard.isActiveMember(homework.courseId(), user.id(), null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        return success(submissions.listForHomework(homeworkId, user.id()).stream()
                .map(HomeworkController::submissionResponse).toList());
    }

    @GetMapping("/homeworks/{homeworkId}/submissions")
    public Map<String, Object> managerSubmissions(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        HomeworkService.HomeworkSummary homework = homeworks.find(homeworkId);
        requireManager(homework.courseId(), user);
        List<Map<String, Object>> records = submissions.listForManager(homeworkId).stream()
                .map(HomeworkController::submissionResponse).toList();
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", records);
        page.put("total", records.size());
        page.put("page", 1);
        page.put("size", records.size());
        return success(page);
    }

    @GetMapping("/homeworks/{homeworkId}/statistics")
    public Map<String, Object> statistics(@PathVariable long homeworkId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new HomeworkClientException(HttpStatus.NOT_FOUND, "HWK_4001", "homework not found");
        }
        requireStatisticsManager(homework.courseId(), user);
        try {
            return success(statistics.statistics(homework, page, size));
        } catch (IllegalArgumentException invalid) {
            throw new HomeworkClientException(HttpStatus.BAD_REQUEST, "HWK_4000", invalid.getMessage());
        }
    }

    @PutMapping("/homeworks/{homeworkId}/publish")
    public HomeworkService.HomeworkSummary publish(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requestId(http);
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        requireManager(homework.courseId(), user);
        try {
            return homeworks.publish(homeworkId, requestId);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        }
    }

    @PutMapping("/homeworks/{homeworkId}/scores/publish")
    public Map<String, Object> publishScores(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requestId(http);
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        requireManager(homework.courseId(), user);
        try {
            return success(homeworks.publishScores(homeworkId, user.id(), requestId));
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        }
    }

    private void requireManager(String courseId, CurrentUser user) {
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        if (!managerRole || !coursePermissions.canManageCourse(courseId, user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
    }

    private void requireStatisticsManager(String courseId, CurrentUser user) {
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        if (!managerRole || !coursePermissions.canManageCourse(courseId, user.id())) {
            throw new HomeworkClientException(HttpStatus.FORBIDDEN, "HWK_4031", "course management permission is required");
        }
    }

    private static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) return UUID.randomUUID().toString();
        try {
            String candidate = requestId.trim();
            if (candidate.matches("(?i)[0-9a-f]{32}")) {
                candidate = candidate.substring(0, 8) + "-" + candidate.substring(8, 12) + "-"
                        + candidate.substring(12, 16) + "-" + candidate.substring(16, 20) + "-"
                        + candidate.substring(20);
            }
            return UUID.fromString(candidate).toString();
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id must be a UUID", invalid);
        }
    }

    private static Map<String, Object> success(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "0");
        response.put("message", "success");
        response.put("data", data);
        return response;
    }

    public record CreateHomeworkRequest(@NotBlank String courseId, @NotBlank String title, String description,
                                        @NotBlank String type, @NotNull Instant deadline,
                                        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal totalScore,
                                        boolean allowResubmit, boolean allowLateSubmit,
                                        List<@NotBlank String> languages,
                                        String languageLimitJson,
                                        List<@Valid TestCaseRequest> testCases,
                                        List<@Valid QuestionRequest> questions) {
        HomeworkService.CreateHomeworkCommand toCommand() {
            return new HomeworkService.CreateHomeworkCommand(courseId, title, description == null ? "" : description,
                    type, deadline, totalScore, allowResubmit, allowLateSubmit,
                    normalizedLanguages(),
                    testCases == null ? List.of() : testCases.stream().map(TestCaseRequest::toCommand).toList(),
                    questions == null ? List.of() : questions.stream().map(QuestionRequest::toCommand).toList());
        }

        private List<String> normalizedLanguages() {
            if (languages != null) return languages;
            if (languageLimitJson == null || languageLimitJson.isBlank()) return List.of();
            try {
                JsonNode values = new ObjectMapper().readTree(languageLimitJson);
                if (!values.isArray()) throw new IllegalArgumentException("languageLimitJson must be a JSON array");
                java.util.ArrayList<String> parsed = new java.util.ArrayList<>();
                values.forEach(value -> {
                    if (!value.isTextual() || value.asText().isBlank()) {
                        throw new IllegalArgumentException("languageLimitJson values must be non-blank strings");
                    }
                    parsed.add(value.asText());
                });
                return List.copyOf(parsed);
            } catch (java.io.IOException invalid) {
                throw new IllegalArgumentException("languageLimitJson must be valid JSON", invalid);
            }
        }
    }

    public record TestCaseRequest(@NotNull @JsonAlias("inputData") String input, @NotNull String expectedOutput,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal scoreWeight,
                                  boolean hidden, int sortOrder) {
        HomeworkService.TestCaseCommand toCommand() {
            return new HomeworkService.TestCaseCommand(input, expectedOutput, scoreWeight, hidden, sortOrder);
        }
    }

    public record QuestionRequest(@NotBlank String questionType, @NotBlank String stem, @NotBlank String optionsJson,
                                  @NotBlank String answerJson, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal score,
                                  int sortOrder) {
        HomeworkService.QuestionCommand toCommand() {
            return new HomeworkService.QuestionCommand(questionType, stem, optionsJson, answerJson, score, sortOrder);
        }
    }

    public record SubmitHomeworkRequest(@JsonAlias({"code", "codeText"}) String codeText,
                                        String language, String answerText, String answerJson, List<String> fileIds) { }

    public record ReviewHomeworkRequest(@NotNull @DecimalMin(value = "0") BigDecimal manualScore,
                                        @NotNull @DecimalMin(value = "0") BigDecimal finalScore,
                                        String comment) { }

    private static Map<String, Object> submissionResponse(HomeworkSubmissionService.SubmissionView submission) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", submission.publicSubmissionId());
        data.put("homeworkId", submission.homeworkId());
        data.put("studentId", numericIfPossible(submission.studentId()));
        data.put("submitType", submission.submitType());
        data.put("answerText", submission.answerText());
        data.put("answerJson", submission.answerJson());
        data.put("language", submission.language().isBlank() ? null : submission.language());
        data.put("submitStatus", submission.submitStatus());
        data.put("evaluationStatus", submission.evaluationStatus());
        data.put("reviewStatus", submission.reviewStatus());
        data.put("autoScore", submission.autoScore());
        data.put("manualScore", submission.manualScore());
        data.put("finalScore", submission.finalScore());
        data.put("comment", submission.comment());
        data.put("version", submission.version());
        data.put("final", submission.finalSubmission());
        data.put("submittedAt", submission.submittedAt());
        return data;
    }

    private static Object numericIfPossible(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return value; }
    }

    private static boolean trustedAttachment(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".txt") && contentType.equals("text/plain")) return true;
        if (!name.endsWith(".pdf") || !contentType.equals("application/pdf")) return false;
        try {
            byte[] content = file.getBytes();
            return content.length >= 5 && new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        } catch (java.io.IOException ignored) {
            return false;
        }
    }

    private Map<String, Object> homeworkResponse(HomeworkService.HomeworkSummary homework, boolean includeAnswer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", homework.id());
        data.put("courseId", numericIfPossible(homework.courseId()));
        data.put("chapterId", null);
        data.put("title", homework.title());
        data.put("description", homework.description());
        data.put("type", homework.type());
        data.put("status", homework.status());
        data.put("totalScore", homework.totalScore());
        data.put("deadline", homework.deadline());
        data.put("allowResubmit", homework.allowResubmit());
        data.put("allowLateSubmit", homework.allowLateSubmit());
        data.put("showEvaluationBeforePublish", true);
        data.put("judgeConfigId", null);
        data.put("createdBy", null);
        data.put("publishedAt", homework.publishedAt());
        data.put("deleted", false);
        data.put("createdAt", null);
        data.put("updatedAt", null);
        data.put("languageLimitJson", homework.languages().isEmpty() ? null : "[\"" + String.join("\",\"", homework.languages()) + "\"]");
        data.put("timeLimitMs", homework.type().equals("CODE") ? 1_000 : null);
        data.put("memoryLimitKb", homework.type().equals("CODE") ? 65_536 : null);
        data.put("outputCompareMode", homework.type().equals("CODE") ? "EXACT" : null);
        data.put("questions", homeworks.questions(homework.id()).stream().map(question -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("questionType", question.questionType());
            value.put("stem", question.stem());
            value.put("optionsJson", question.optionsJson());
            if (includeAnswer) value.put("answerJson", question.answerJson());
            value.put("score", question.score());
            value.put("sortOrder", question.sortOrder());
            return value;
        }).toList());
        data.put("testCases", List.of());
        return data;
    }
}
