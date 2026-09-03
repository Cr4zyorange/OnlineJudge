package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CourseMembershipGuard;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.LabExperimentService;
import com.onlinejudge.assessmentservice.service.LabReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/** REST boundary for the LAB report lifecycle: upload, read, download, and teacher review. */
@RestController
@RequestMapping("/api/v1/labs")
public class LabReportController {
    private final LabExperimentService labs;
    private final LabReportService reports;
    private final CourseMembershipGuard membershipGuard;
    private final CoursePermissionClient coursePermissions;

    public LabReportController(LabExperimentService labs, LabReportService reports,
            CourseMembershipGuard membershipGuard, CoursePermissionClient coursePermissions) {
        this.labs = labs;
        this.reports = reports;
        this.membershipGuard = membershipGuard;
        this.coursePermissions = coursePermissions;
    }

    @PostMapping(path = "/{labId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LabReportService.LabReportSummary upload(@PathVariable long labId,
            @RequestParam(required = false) String submissionId, @RequestParam MultipartFile reportFile,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        String requestId = requireRequestId(http);
        LabExperimentService.LabSummary lab = findLab(labId);
        if (!user.hasRole("STUDENT") || !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course student membership is required");
        }
        try {
            return reports.upload(lab, user.id(), submissionId, reportFile);
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing.getMessage(), missing);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException invalidState) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidState.getMessage(), invalidState);
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "report storage unavailable", unavailable);
        }
    }

    @GetMapping("/{labId}/reports/{reportId}")
    public LabReportService.LabReportSummary detail(@PathVariable long labId, @PathVariable long reportId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        return authorizeRead(labId, reportId, user, requestIdOrGenerated(http)).summary();
    }

    @GetMapping("/{labId}/reports/{reportId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long labId, @PathVariable long reportId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        LabReportService.ReportFile report = authorizeRead(labId, reportId, user, requestIdOrGenerated(http));
        try {
            MediaType contentType;
            try { contentType = MediaType.parseMediaType(report.contentType()); }
            catch (IllegalArgumentException invalid) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
            byte[] content = reports.read(report.storageKey());
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(content.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(report.summary().fileName(), StandardCharsets.UTF_8).build().toString())
                    .body(content);
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "report storage unavailable", unavailable);
        }
    }

    @PutMapping("/{labId}/reports/{reportId}/score")
    public LabReportService.LabReportSummary score(@PathVariable long labId, @PathVariable long reportId,
            @RequestBody ReportScoreRequest request, @RequestAttribute("assessment.currentUser") CurrentUser user,
            HttpServletRequest http) {
        String requestId = requireRequestId(http);
        LabExperimentService.LabSummary lab = findLab(labId);
        if (!canManage(lab, user, requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        if (lab.deleted() || "ARCHIVED".equals(lab.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "an archived LAB is immutable");
        }
        try {
            return reports.score(labId, reportId, request == null ? null : request.score(),
                    request == null ? null : request.comment(), user.id(), lab.maxScore());
        } catch (NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing.getMessage(), missing);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }

    private LabReportService.ReportFile authorizeRead(long labId, long reportId, CurrentUser user, String requestId) {
        LabExperimentService.LabSummary lab = findLab(labId);
        LabReportService.ReportFile report;
        try { report = reports.file(labId, reportId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing.getMessage(), missing); }
        if (canManage(lab, user, requestId)) return report;
        if (!report.studentId().equals(user.id())
                || !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LAB report access is restricted");
        }
        return report;
    }

    private LabExperimentService.LabSummary findLab(long labId) {
        try { return labs.find(labId); }
        catch (NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
    }

    private boolean canManage(LabExperimentService.LabSummary lab, CurrentUser user, String requestId) {
        return (user.hasRole("TEACHER") || user.hasRole("ADMIN"))
                && coursePermissions.canManageCourse(lab.courseId(), user.id(), requestId);
    }

    private static String requireRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        }
        return requestId;
    }

    private static String requestIdOrGenerated(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? java.util.UUID.randomUUID().toString() : requestId;
    }

    public record ReportScoreRequest(BigDecimal score, String comment) { }
}
