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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

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
                    request.deadline(), request.maxScore(), request.toAllowedLanguages(), request.autoEvaluateOrDefault(), request.toTestcases(),
                    request.chapterId(), request.toAttachmentIds(), request.evaluationModeOrDefault(), request.reportRequiredOrDefault(),
                    request.timeLimitMsOrDefault(), request.memoryLimitKbOrDefault()), user.id());
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

    @GetMapping("/labs/{labId}")
    public java.util.Map<String, Object> detail(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        LabExperimentService.LabDetail detail;
        try { detail = labs.detail(labId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        if (!canManage(detail.courseId(), user)) {
            if (!courseMembers.isActive(detail.courseId(), user.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
            }
            if (!List.of("PUBLISHED", "CLOSED", "SCORE_PUBLISHED", "ARCHIVED").contains(detail.status())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LAB is not open to students");
            }
        }
        var response = new java.util.LinkedHashMap<String, Object>();
        response.put("id", detail.id());
        response.put("labId", detail.id());
        response.put("courseId", detail.courseId());
        response.put("title", detail.title());
        response.put("description", detail.description());
        response.put("status", detail.status());
        response.put("deadline", detail.deadline());
        response.put("maxScore", detail.maxScore());
        response.put("allowedLanguages", detail.allowedLanguages());
        response.put("evaluationMode", detail.evaluationMode());
        response.put("autoEvaluate", detail.autoEvaluate());
        response.put("reportRequired", detail.reportRequired());
        response.put("chapterId", detail.chapterId());
        response.put("attachmentIds", parseAttachmentIds(detail.attachmentIds()));
        response.put("timeLimitMs", detail.timeLimitMs());
        response.put("memoryLimitKb", detail.memoryLimitKb());
        response.put("publishedAt", detail.publishedAt());
        response.put("deleted", detail.deleted());
        response.put("testcases", detail.testcases().stream()
                .filter(testcase -> canManage(detail.courseId(), user) || testcase.isPublic())
                .map(testcase -> {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id", testcase.id());
            item.put("labId", detail.id());
            item.put("input", testcase.input());
            item.put("expectedOutput", testcase.expectedOutput());
            item.put("scoreWeight", testcase.scoreWeight());
            item.put("public", testcase.isPublic());
            item.put("orderNum", testcase.orderNum());
            return item;
        }).toList());
        return response;
    }

    @PutMapping("/labs/{labId}")
    public LabExperimentService.LabSummary update(@PathVariable long labId, @Valid @RequestBody UpdateLabRequest request,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        LabExperimentService.LabSummary current;
        try { current = labs.find(labId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        if (!canManage(current.courseId(), user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        try { return labs.update(labId, request.toCommand()); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid); }
        catch (IllegalStateException invalidState) { throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState); }
    }

    @DeleteMapping("/labs/{labId}")
    public LabExperimentService.LabSummary delete(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        LabExperimentService.LabSummary current;
        try { current = labs.find(labId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        if (!canManage(current.courseId(), user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        try { return labs.delete(labId); }
        catch (IllegalStateException invalidState) { throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState); }
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

    @PostMapping("/labs/{labId}/close")
    public LabExperimentService.LabSummary close(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        return lifecycleMutation(labId, user, true);
    }

    @PutMapping("/labs/{labId}/release-scores")
    public LabExperimentService.LabSummary releaseScores(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        requireRequestId(http);
        LabExperimentService.LabSummary current = findAndAuthorize(labId, user);
        try { return labs.releaseScores(current.labId()); }
        catch (IllegalStateException invalidState) { throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState); }
    }

    private LabExperimentService.LabSummary lifecycleMutation(long labId, CurrentUser user, boolean close) {
        LabExperimentService.LabSummary current = findAndAuthorize(labId, user);
        try { return close ? labs.close(current.labId()) : labs.publish(current.labId()); }
        catch (IllegalStateException invalidState) { throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState); }
    }

    private LabExperimentService.LabSummary findAndAuthorize(long labId, CurrentUser user) {
        LabExperimentService.LabSummary current;
        try { current = labs.find(labId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        if (!canManage(current.courseId(), user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        return current;
    }

    private static List<Long> parseAttachmentIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).map(Long::valueOf).toList();
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
                                   JsonNode allowedLanguages, Boolean autoEvaluate, Boolean reportRequired,
                                   Long chapterId, List<Long> attachmentIds, String evaluationMode, Integer timeLimitMs,
                                   Integer memoryLimitKb, @JsonProperty("testcases") List<TestcaseRequest> testcaseRequests) {
        public List<String> toAllowedLanguages() { return parseAllowedLanguages(allowedLanguages); }
        public boolean autoEvaluateOrDefault() { return autoEvaluate == null || autoEvaluate; }
        public boolean reportRequiredOrDefault() { return Boolean.TRUE.equals(reportRequired); }
        public int timeLimitMsOrDefault() { return timeLimitMs == null ? 60000 : timeLimitMs; }
        public int memoryLimitKbOrDefault() { return memoryLimitKb == null ? 262144 : memoryLimitKb; }
        public String evaluationModeOrDefault() { return evaluationMode == null || evaluationMode.isBlank() ? "DOCKER_IO" : evaluationMode; }
        public String toAttachmentIds() { return attachmentIds == null ? "" : attachmentIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }
        public List<LabExperimentService.LabTestcase> toTestcases() {
            if (testcaseRequests == null) return List.of();
            return testcaseRequests.stream().map(item -> new LabExperimentService.LabTestcase(item.input(), item.expectedOutput(), item.scoreWeight(), item.isPublic(), item.orderNum())).toList();
        }
    }
    public record UpdateLabRequest(@NotBlank String title, @NotBlank String description, Instant deadline,
                                   @DecimalMin(value = "0.01") BigDecimal maxScore,
                                   JsonNode allowedLanguages, Boolean autoEvaluate, Boolean reportRequired,
                                   Long chapterId, List<Long> attachmentIds, String evaluationMode, Integer timeLimitMs,
                                   Integer memoryLimitKb, @JsonProperty("testcases") List<TestcaseRequest> testcaseRequests) {
        public List<String> toAllowedLanguages() { return parseAllowedLanguages(allowedLanguages); }
        public boolean autoEvaluateOrDefault() { return autoEvaluate == null || autoEvaluate; }
        public boolean reportRequiredOrDefault() { return Boolean.TRUE.equals(reportRequired); }
        public int timeLimitMsOrDefault() { return timeLimitMs == null ? 60000 : timeLimitMs; }
        public int memoryLimitKbOrDefault() { return memoryLimitKb == null ? 262144 : memoryLimitKb; }
        public String toAttachmentIds() { return attachmentIds == null ? "" : attachmentIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }
        public LabExperimentService.UpdateLabCommand toCommand() {
            List<LabExperimentService.LabTestcase> testcases = testcaseRequests == null ? List.of() : testcaseRequests.stream()
                    .map(item -> new LabExperimentService.LabTestcase(item.input(), item.expectedOutput(), item.scoreWeight(), item.isPublic(), item.orderNum())).toList();
            return new LabExperimentService.UpdateLabCommand(title, description, deadline, maxScore, toAllowedLanguages(),
                    autoEvaluateOrDefault(), testcases, chapterId, toAttachmentIds(), evaluationModeOrDefault(), reportRequiredOrDefault(),
                    timeLimitMsOrDefault(), memoryLimitKbOrDefault());
        }
        public String evaluationModeOrDefault() { return evaluationMode == null || evaluationMode.isBlank() ? "DOCKER_IO" : evaluationMode; }
    }
    public record TestcaseRequest(String input, String expectedOutput, @DecimalMin(value = "0") BigDecimal scoreWeight,
                                  @JsonProperty("public") boolean isPublic, int orderNum) { }

    private static List<String> parseAllowedLanguages(JsonNode value) {
        if (value == null || value.isNull()) return List.of();
        if (value.isTextual()) return java.util.Arrays.stream(value.asText().split(","))
                .map(String::trim).filter(item -> !item.isBlank()).toList();
        if (value.isArray()) {
            List<String> languages = new java.util.ArrayList<>();
            value.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) languages.add(item.asText().trim()); });
            return List.copyOf(languages);
        }
        return List.of();
    }
}
