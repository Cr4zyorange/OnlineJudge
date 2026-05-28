package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.service.HomeworkService;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import com.onlinejudge.integration.course.CoursePermissionClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/homeworks")
public class HomeworkController {
    private final HomeworkService homeworkService;
    private final HomeworkSubmissionService homeworkSubmissionService;
    private final CoursePermissionClient coursePermissionClient;

    public HomeworkController(
            HomeworkService homeworkService,
            HomeworkSubmissionService homeworkSubmissionService,
            CoursePermissionClient coursePermissionClient
    ) {
        this.homeworkService = homeworkService;
        this.homeworkSubmissionService = homeworkSubmissionService;
        this.coursePermissionClient = coursePermissionClient;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HomeworkResponse>> create(
            @Valid @RequestBody CreateHomeworkRequest request,
            CurrentUser currentUser
    ) {
        Homework created = homeworkService.create(request.toCommand(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(HomeworkResponse.fromTeacherView(created)));
    }

    @GetMapping
    public ApiResponse<List<HomeworkResponse>> list(
            @RequestParam(required = false) Long courseId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(homeworkService.list(courseId, currentUser).stream()
                .map(homework -> toResponse(homework, currentUser))
                .toList());
    }

    @GetMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> get(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(toResponse(homeworkService.get(homeworkId, currentUser), currentUser));
    }

    @PutMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> update(
            @PathVariable long homeworkId,
            @Valid @RequestBody CreateHomeworkRequest request,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(
                homeworkService.update(homeworkId, request.toCommand(), currentUser)
        ));
    }

    @PutMapping("/{homeworkId}/publish")
    public ApiResponse<HomeworkResponse> publish(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.publish(homeworkId, currentUser)));
    }

    @PutMapping("/{homeworkId}/close")
    public ApiResponse<HomeworkResponse> close(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.close(homeworkId, currentUser)));
    }

    @PostMapping("/{homeworkId}/submissions")
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> submit(
            @PathVariable long homeworkId,
            @RequestBody SubmitHomeworkRequest request,
            CurrentUser currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                HomeworkSubmissionResponse.from(homeworkSubmissionService.submit(homeworkId, request.toCommand(), currentUser))
        ));
    }

    @GetMapping("/{homeworkId}/my-submissions")
    public ApiResponse<List<HomeworkSubmissionResponse>> mySubmissions(
            @PathVariable long homeworkId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(homeworkSubmissionService.listMine(homeworkId, currentUser).stream()
                .map(HomeworkSubmissionResponse::from)
                .toList());
    }

    @GetMapping("/{homeworkId}/submissions")
    public ApiResponse<List<HomeworkSubmissionResponse>> submissions(
            @PathVariable long homeworkId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(homeworkSubmissionService.listForTeacher(homeworkId, currentUser).stream()
                .map(HomeworkSubmissionResponse::from)
                .toList());
    }

    private HomeworkResponse toResponse(Homework homework, CurrentUser currentUser) {
        if (coursePermissionClient.canManageCourse(homework.courseId(), currentUser.id())) {
            return HomeworkResponse.fromTeacherView(homework);
        }
        return HomeworkResponse.fromStudentView(homework);
    }
}
