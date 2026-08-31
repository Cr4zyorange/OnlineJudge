package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AssessmentSubmissionController {
    private final AssessmentSubmissionService submissions; private final CourseMemberProjectionRepository courseMembers; private final PersistentSubmissionFileStore files;
    public AssessmentSubmissionController(AssessmentSubmissionService submissions, CourseMemberProjectionRepository courseMembers, PersistentSubmissionFileStore files) { this.submissions = submissions; this.courseMembers = courseMembers; this.files = files; }
    @PostMapping("/submissions")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AssessmentSubmissionService.SubmittedSubmission submit(@Valid @RequestBody SubmissionRequest request, @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        if (!user.hasRole("STUDENT") || !courseMembers.isActive(request.courseId(), user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        if ("HWK".equalsIgnoreCase(request.sourceType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HWK submissions must use the canonical homework endpoint");
        return submissions.submit(new AssessmentSubmissionService.SubmissionCommand(request.sourceType(), request.sourceId(), request.courseId(), user.id(), request.contentRef()));
    }

    @PostMapping(path = "/labs/{sourceId}/submissions", consumes = "multipart/form-data")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AssessmentSubmissionService.SubmittedSubmission submitLab(@PathVariable String sourceId, @RequestParam String courseId,
            @RequestParam MultipartFile file, @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        return submitUploaded("LAB", sourceId, courseId, file, user, http);
    }

    private AssessmentSubmissionService.SubmittedSubmission submitUploaded(String sourceType, String sourceId, String courseId, MultipartFile file,
            CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        if (!user.hasRole("STUDENT") || !courseMembers.isActive(courseId, user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission file is required");
        try {
            // The persisted key, rather than a client path or bytes in memory, crosses into the queue transaction.
            var stored = files.store(java.util.UUID.randomUUID().toString(), file.getOriginalFilename(), file.getBytes());
            return submissions.submit(new AssessmentSubmissionService.SubmissionCommand(sourceType, sourceId, courseId, user.id(), stored.storageKey()));
        } catch (java.io.IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "submission storage unavailable", failure);
        }
    }
    public record SubmissionRequest(@NotBlank String sourceType, @NotBlank String sourceId, @NotBlank String courseId, String contentRef) { }
}
