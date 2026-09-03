package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import com.onlinejudge.assessmentservice.service.CourseMembershipGuard;
import com.onlinejudge.assessmentservice.service.LabExperimentService;
import com.onlinejudge.assessmentservice.service.LabSubmissionService;
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
    private final AssessmentSubmissionService submissions; private final CourseMemberProjectionRepository courseMembers; private final CourseMembershipGuard membershipGuard; private final PersistentSubmissionFileStore files;
    private final LabExperimentService labs; private final LabSubmissionService labSubmissions;
    public AssessmentSubmissionController(AssessmentSubmissionService submissions, CourseMemberProjectionRepository courseMembers,
            CourseMembershipGuard membershipGuard, PersistentSubmissionFileStore files, LabExperimentService labs, LabSubmissionService labSubmissions) {
        this.submissions = submissions; this.courseMembers = courseMembers; this.membershipGuard = membershipGuard; this.files = files; this.labs = labs; this.labSubmissions = labSubmissions;
    }
    @PostMapping("/submissions")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AssessmentSubmissionService.SubmittedSubmission submit(@Valid @RequestBody SubmissionRequest request, @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        // LAB facts are owned by the LAB aggregate.  This generic Core endpoint
        // cannot establish publication, deadline, language, or course ownership.
        if ("LAB".equals(request.sourceType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LAB submissions must use the LAB submission endpoint");
        if (!user.hasRole("STUDENT") || !courseMembers.isActive(request.courseId(), user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        if ("HWK".equalsIgnoreCase(request.sourceType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HWK submissions must use the canonical homework endpoint");
        return submissions.submit(new AssessmentSubmissionService.SubmissionCommand(request.sourceType(), request.sourceId(), request.courseId(), user.id(), request.contentRef(), http.getHeader("X-Request-Id")));
    }

    @PostMapping(path = "/labs/{sourceId}/submissions", consumes = "multipart/form-data")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public LabSubmissionService.SubmittedLabSubmission submitLab(@PathVariable long sourceId, @RequestParam(required = false) String courseId,
    @RequestParam String language,
            @RequestParam(required = false) MultipartFile file, @RequestParam(required = false) String code,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        LabExperimentService.LabSummary lab;
        try { lab = labs.find(sourceId); }
        catch (java.util.NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        if (!user.hasRole("STUDENT") || !membershipGuard.isActiveMember(lab.courseId(), user.id(), http.getHeader("X-Request-Id"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasCode = code != null && !code.isBlank();
        if (!hasFile && !hasCode) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission code or file is required");
        if (hasFile && hasCode) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission must provide either code or file, not both");
        try {
            byte[] content = hasFile ? file.getBytes() : code.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String filename = hasFile ? file.getOriginalFilename() : "Main." + language;
            return labSubmissions.submit(new LabSubmissionService.SubmitLabCommand(sourceId, courseId, user.id(), language,
                    filename, content, hasFile, hasFile ? null : code,
                    http.getHeader("X-Request-Id")));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException invalidState) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState);
        } catch (java.io.IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "submission storage unavailable", failure);
        }
    }

    private AssessmentSubmissionService.SubmittedSubmission submitUploaded(String sourceType, String sourceId, String courseId, MultipartFile file,
            CurrentUser user, HttpServletRequest http) {
        if (http.getHeader("X-Request-Id") == null || http.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        if (!user.hasRole("STUDENT") || !courseMembers.isActive(courseId, user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission file is required");
        try {
            // The persisted key, rather than a client path or bytes in memory, crosses into the queue transaction.
            var stored = files.store(java.util.UUID.randomUUID().toString(), file.getOriginalFilename(), file.getBytes());
            return submissions.submit(new AssessmentSubmissionService.SubmissionCommand(sourceType, sourceId, courseId, user.id(), stored.storageKey(), http.getHeader("X-Request-Id")));
        } catch (java.io.IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "submission storage unavailable", failure);
        }
    }
    public record SubmissionRequest(@NotBlank String sourceType, @NotBlank String sourceId, @NotBlank String courseId, String contentRef) { }
}
