package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/submissions")
public class AssessmentSubmissionController {
    private final AssessmentSubmissionService submissions; private final CourseMemberProjectionRepository courseMembers;
    public AssessmentSubmissionController(AssessmentSubmissionService submissions, CourseMemberProjectionRepository courseMembers) { this.submissions = submissions; this.courseMembers = courseMembers; }
    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AssessmentSubmissionService.SubmittedSubmission submit(@Valid @RequestBody SubmissionRequest request, @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        if (!user.hasRole("STUDENT") || !courseMembers.isActive(request.courseId(), user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        return submissions.submit(new AssessmentSubmissionService.SubmissionCommand(request.sourceType(), request.sourceId(), request.courseId(), user.id(), request.contentRef()));
    }
    public record SubmissionRequest(@NotBlank String sourceType, @NotBlank String sourceId, @NotBlank String courseId, String contentRef) { }
}
