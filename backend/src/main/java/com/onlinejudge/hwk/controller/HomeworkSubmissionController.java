package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
public class HomeworkSubmissionController {
    private final HomeworkSubmissionService homeworkSubmissionService;

    public HomeworkSubmissionController(HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @GetMapping("/{submissionId}")
    public ApiResponse<HomeworkSubmissionResponse> detail(
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        HomeworkSubmission submission;
        if (currentUser.hasRole("STUDENT")) {
            submission = homeworkSubmissionService.detailForStudent(submissionId, currentUser.id());
            return ApiResponse.ok(HomeworkSubmissionResponse.fromStudentView(
                    submission,
                    homeworkSubmissionService.isLatest(submission)
            ));
        }
        if (currentUser.hasRole("TEACHER") || currentUser.hasRole("ADMIN")) {
            submission = homeworkSubmissionService.detailForManager(submissionId, currentUser.id());
            return ApiResponse.ok(HomeworkSubmissionResponse.fromTeacherView(
                    submission,
                    homeworkSubmissionService.isLatest(submission)
            ));
        }
        throw new AccessDeniedException("submission access role is required");
    }
}
