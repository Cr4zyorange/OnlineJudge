package com.onlinejudge.assessmentservice.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CourseMembershipGuard;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.HomeworkService;
import com.onlinejudge.assessmentservice.service.HomeworkSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
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
    private final HomeworkService homeworks;
    private final CoursePermissionClient coursePermissions;
    private final CourseMembershipGuard membershipGuard;
    private final HomeworkSubmissionService submissions;

    public HomeworkController(HomeworkService homeworks, CoursePermissionClient coursePermissions,
            CourseMembershipGuard membershipGuard, HomeworkSubmissionService submissions) {
        this.homeworks = homeworks;
        this.coursePermissions = coursePermissions;
        this.membershipGuard = membershipGuard;
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
                    : submissions.submit(homeworkId, user.id(), request.codeText(), request.language());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("submissionId", submitted.publicSubmissionId());
            data.put("homeworkId", submitted.homeworkId());
            data.put("studentId", user.id());
            data.put("submitType", submitted.taskId() == null ? "TEXT" : "CODE");
            data.put("language", submitted.taskId() == null ? null : request.language());
            data.put("submitStatus", submitted.submitStatus());
            data.put("evaluationStatus", submitted.evaluationStatus());
            data.put("reviewStatus", "UNREVIEWED");
            data.put("autoScore", null);
            data.put("manualScore", null);
            data.put("finalScore", null);
            data.put("version", submitted.version());
            data.put("final", true);
            data.put("submittedAt", submitted.submittedAt());
            return success(data);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        } catch (UncheckedIOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage(), unavailable);
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
                .map(HomeworkController::homeworkResponse).toList();
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
        return success(homeworkResponse(homework));
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
                                        List<@Valid TestCaseRequest> testCases) {
        HomeworkService.CreateHomeworkCommand toCommand() {
            return new HomeworkService.CreateHomeworkCommand(courseId, title, description == null ? "" : description,
                    type, deadline, totalScore, allowResubmit, allowLateSubmit,
                    languages == null ? List.of() : languages,
                    testCases == null ? List.of() : testCases.stream().map(TestCaseRequest::toCommand).toList());
        }
    }

    public record TestCaseRequest(@NotNull String input, @NotNull String expectedOutput,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal scoreWeight,
                                  boolean hidden, int sortOrder) {
        HomeworkService.TestCaseCommand toCommand() {
            return new HomeworkService.TestCaseCommand(input, expectedOutput, scoreWeight, hidden, sortOrder);
        }
    }

    public record SubmitHomeworkRequest(@JsonAlias({"code", "codeText"}) String codeText,
                                        String language, String answerText) { }

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

    private static Map<String, Object> homeworkResponse(HomeworkService.HomeworkSummary homework) {
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
        data.put("questions", List.of());
        data.put("testCases", List.of());
        return data;
    }
}
