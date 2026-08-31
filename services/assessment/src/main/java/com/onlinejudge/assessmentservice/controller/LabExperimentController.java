package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.LabExperimentService;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
public class LabExperimentController {
    private final LabExperimentService labs;
    private final CourseMemberProjectionRepository courseMembers;
    private final CoursePermissionClient coursePermissions;

    public LabExperimentController(LabExperimentService labs, CourseMemberProjectionRepository courseMembers,
            CoursePermissionClient coursePermissions) {
        this.labs = labs;
        this.courseMembers = courseMembers;
        this.coursePermissions = coursePermissions;
    }

    @PostMapping("/courses/{courseId}/labs")
    @ResponseStatus(HttpStatus.CREATED)
    public LabExperimentService.LabSummary create(@PathVariable String courseId, @Valid @RequestBody CreateLabRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        if (!canManage(courseId, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        try {
            return labs.create(new LabExperimentService.CreateLabCommand(courseId, request.title(), request.description(),
                    request.deadline(), request.maxScore(), request.allowedLanguages(), request.autoEvaluate(), request.toTestcases()), user.id());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }

    @GetMapping("/courses/{courseId}/labs")
    public List<LabExperimentService.LabSummary> list(@PathVariable String courseId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        if (!courseMembers.isActive(courseId, user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        return labs.list(courseId, canManage(courseId, user));
    }

    @PostMapping("/labs/{labId}/publish")
    public LabExperimentService.LabSummary publish(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        LabExperimentService.LabSummary lab;
        try {
            lab = labs.find(labId);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing);
        }
        if (!canManage(lab.courseId(), user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        try {
            return labs.publish(labId);
        } catch (IllegalStateException invalidState) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState);
        }
    }

    private static void requireRequestId(HttpServletRequest request) {
        if (request.getHeader("X-Request-Id") == null || request.getHeader("X-Request-Id").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        }
    }

    private boolean canManage(String courseId, CurrentUser user) {
        return (user.hasRole("TEACHER") || user.hasRole("ADMIN"))
                && coursePermissions.canManageCourse(courseId, user.id());
    }

    public record CreateLabRequest(@NotBlank String title, @NotBlank String description, Instant deadline,
                                   @DecimalMin(value = "0.01") BigDecimal maxScore,
                                   @NotEmpty List<@NotBlank String> allowedLanguages, boolean autoEvaluate,
                                   @JsonProperty("testcases") List<TestcaseRequest> testcaseRequests) {
        public List<LabExperimentService.LabTestcase> toTestcases() {
            if (testcaseRequests == null) return List.of();
            return testcaseRequests.stream().map(item -> new LabExperimentService.LabTestcase(item.input(), item.expectedOutput(), item.scoreWeight(), item.isPublic(), item.orderNum())).toList();
        }
    }
    public record TestcaseRequest(String input, String expectedOutput, @DecimalMin(value = "0") BigDecimal scoreWeight,
                                  @JsonProperty("public") boolean isPublic, int orderNum) { }
}
