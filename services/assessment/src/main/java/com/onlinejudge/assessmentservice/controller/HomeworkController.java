package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.HomeworkService;
import com.onlinejudge.assessmentservice.service.HomeworkSubmissionService;
import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/v1")
public class HomeworkController {
    private final HomeworkService homeworks;
    private final CoursePermissionClient coursePermissions;
    private final CourseMemberProjectionRepository courseMembers;
    private final HomeworkSubmissionService submissions;

    public HomeworkController(HomeworkService homeworks, CoursePermissionClient coursePermissions,
            CourseMemberProjectionRepository courseMembers, HomeworkSubmissionService submissions) {
        this.homeworks = homeworks;
        this.coursePermissions = coursePermissions;
        this.courseMembers = courseMembers;
        this.submissions = submissions;
    }

    @PostMapping(path = "/homeworks/{homeworkId}/submissions", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public HomeworkSubmissionService.SubmittedHomework submit(@PathVariable long homeworkId,
            @Valid @RequestBody SubmitHomeworkRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        if (!user.hasRole("STUDENT")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "student role is required");
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        if (!courseMembers.isActive(homework.courseId(), user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        }
        try {
            return submissions.submit(homeworkId, user.id(), request.code(), request.language());
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

    @PostMapping("/homeworks")
    @ResponseStatus(HttpStatus.CREATED)
    public HomeworkService.HomeworkSummary create(@Valid @RequestBody CreateHomeworkRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        requireManager(request.courseId(), user);
        try {
            return homeworks.create(request.toCommand(), user.id());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }

    @PutMapping("/homeworks/{homeworkId}/publish")
    public HomeworkService.HomeworkSummary publish(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requireRequestId(http);
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
    public HomeworkService.HomeworkSummary publishScores(@PathVariable long homeworkId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requireRequestId(http);
        HomeworkService.HomeworkSummary homework;
        try {
            homework = homeworks.find(homeworkId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework not found", missing);
        }
        requireManager(homework.courseId(), user);
        try {
            return homeworks.publishScores(homeworkId, requestId);
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

    private static String requireRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id must be a UUID", invalid);
        }
        return requestId;
    }

    public record CreateHomeworkRequest(@NotBlank String courseId, @NotBlank String title, String description,
                                        @NotBlank String type, @NotNull Instant deadline,
                                        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal totalScore,
                                        boolean allowResubmit, boolean allowLateSubmit,
                                        @NotEmpty List<@NotBlank String> languages,
                                        @NotEmpty List<@Valid TestCaseRequest> testCases) {
        HomeworkService.CreateHomeworkCommand toCommand() {
            return new HomeworkService.CreateHomeworkCommand(courseId, title, description == null ? "" : description,
                    type, deadline, totalScore, allowResubmit, allowLateSubmit, languages,
                    testCases.stream().map(TestCaseRequest::toCommand).toList());
        }
    }

    public record TestCaseRequest(@NotNull String input, @NotNull String expectedOutput,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal scoreWeight,
                                  boolean hidden, int sortOrder) {
        HomeworkService.TestCaseCommand toCommand() {
            return new HomeworkService.TestCaseCommand(input, expectedOutput, scoreWeight, hidden, sortOrder);
        }
    }

    public record SubmitHomeworkRequest(@NotBlank String code, @NotBlank String language) { }
}
