package com.onlinejudge.lab.controller;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabSubmissionQuery;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import com.onlinejudge.lab.service.LabExperimentService;
import com.onlinejudge.lab.service.LabPermissionException;
import com.onlinejudge.lab.service.LabSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LabExperimentController {
    private final LabExperimentService labExperimentService;
    private final LabSubmissionService labSubmissionService;
    private final CoursePermissionClient coursePermissionClient;

    public LabExperimentController(
            LabExperimentService labExperimentService,
            LabSubmissionService labSubmissionService,
            CoursePermissionClient coursePermissionClient
    ) {
        this.labExperimentService = labExperimentService;
        this.labSubmissionService = labSubmissionService;
        this.coursePermissionClient = coursePermissionClient;
    }

    @PostMapping("/courses/{courseId}/labs")
    public ResponseEntity<ApiResponse<LabExperimentResponse>> createLab(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @Valid @RequestBody CreateLabExperimentRequest request
    ) {
        requireTeacher(currentUser);
        LabExperiment created = labExperimentService.createLab(courseId, currentUser.id(), request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(LabExperimentResponse.fromTeacherView(created)));
    }

    @GetMapping("/courses/{courseId}/labs")
    public ApiResponse<List<LabExperimentResponse>> listLabs(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @RequestParam(required = false) LabExperimentStatus status
    ) {
        List<LabExperiment> labs = labExperimentService.listLabs(courseId, currentUser.id(), status);
        return ApiResponse.ok(labs.stream()
                .map(lab -> toResponse(lab, currentUser, lab.courseId()))
                .toList());
    }

    @GetMapping("/labs/{labId}")
    public ApiResponse<LabExperimentResponse> getLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        LabExperiment experiment = labExperimentService.getLab(labId, currentUser.id());
        return ApiResponse.ok(toResponse(experiment, currentUser, experiment.courseId()));
    }

    @PutMapping("/labs/{labId}")
    public ApiResponse<LabExperimentResponse> updateLab(
            @PathVariable long labId,
            CurrentUser currentUser,
            @Valid @RequestBody UpdateLabExperimentRequest request
    ) {
        requireTeacher(currentUser);
        LabExperiment updated = labExperimentService.updateLab(labId, currentUser.id(), request.toCommand());
        return ApiResponse.ok(LabExperimentResponse.fromTeacherView(updated));
    }

    @DeleteMapping("/labs/{labId}")
    public ApiResponse<LabExperimentResponse> deleteLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        LabExperiment deleted = labExperimentService.deleteLab(labId, currentUser.id());
        return ApiResponse.ok(LabExperimentResponse.fromTeacherView(deleted));
    }

    @PostMapping("/labs/{labId}/publish")
    public ApiResponse<LabExperimentResponse> publishLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        LabExperiment published = labExperimentService.publishLab(labId, currentUser.id());
        return ApiResponse.ok(LabExperimentResponse.fromTeacherView(published));
    }

    @PostMapping("/labs/{labId}/close")
    public ApiResponse<LabExperimentResponse> closeLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        LabExperiment closed = labExperimentService.closeLab(labId, currentUser.id());
        return ApiResponse.ok(LabExperimentResponse.fromTeacherView(closed));
    }

    @PostMapping(path = "/labs/{labId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LabSubmissionResponse>> submitLab(
            @PathVariable long labId,
            CurrentUser currentUser,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) MultipartFile file
    ) throws IOException {
        requireStudent(currentUser);
        LabSubmissionResponse submission = LabSubmissionResponse.from(labSubmissionService.submit(
                labId,
                currentUser.id(),
                new com.onlinejudge.lab.domain.CreateLabSubmissionCommand(
                        code,
                        file == null ? null : file.getOriginalFilename(),
                        file == null ? null : file.getContentType(),
                        file == null ? null : file.getBytes(),
                        language
                )
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(submission));
    }

    @GetMapping("/labs/{labId}/submissions")
    public ApiResponse<List<LabSubmissionHistoryItemResponse>> listSubmissions(
            @PathVariable long labId,
            CurrentUser currentUser,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) LabSubmitStatus submitStatus,
            @RequestParam(required = false) EvaluationStatus evaluationStatus,
            @RequestParam(required = false) Boolean overdue
    ) {
        return ApiResponse.ok(labSubmissionService.listSubmissions(
                labId,
                currentUser.id(),
                new LabSubmissionQuery(studentId, submitStatus, evaluationStatus, overdue)
        ).stream().map(LabSubmissionHistoryItemResponse::from).toList());
    }

    @GetMapping("/labs/{labId}/submissions/{submissionId}")
    public ApiResponse<LabSubmissionDetailResponse> getSubmissionDetail(
            @PathVariable long labId,
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(LabSubmissionDetailResponse.from(
                labSubmissionService.getSubmissionDetail(labId, submissionId, currentUser.id())
        ));
    }

    @GetMapping("/labs/{labId}/submissions/{submissionId}/result")
    public ApiResponse<LabEvaluationResultResponse> getSubmissionResult(
            @PathVariable long labId,
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(LabEvaluationResultResponse.from(
                labSubmissionService.getSubmissionResult(labId, submissionId, currentUser.id())
        ));
    }

    @PostMapping("/labs/{labId}/submissions/{submissionId}/evaluate")
    public ApiResponse<LabEvaluationResultResponse> evaluateSubmission(
            @PathVariable long labId,
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(LabEvaluationResultResponse.from(
                labSubmissionService.evaluateSubmissionByTeacher(labId, submissionId, currentUser.id())
        ));
    }

    private LabExperimentResponse toResponse(LabExperiment experiment, CurrentUser currentUser, long courseId) {
        if (coursePermissionClient.canManageCourse(courseId, currentUser.id())) {
            return LabExperimentResponse.fromTeacherView(experiment);
        }
        return LabExperimentResponse.fromStudentView(experiment);
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无实验管理权限");
        }
    }

    private void requireStudent(CurrentUser currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new LabPermissionException("仅学生可以提交实验");
        }
    }
}
