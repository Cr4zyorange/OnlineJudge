package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.service.HomeworkService;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
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

    public HomeworkController(HomeworkService homeworkService, HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkService = homeworkService;
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HomeworkResponse>> create(
            CurrentUser currentUser,
            @Valid @RequestBody HomeworkRequest request
    ) {
        requireTeacher(currentUser);
        HomeworkResponse response = HomeworkResponse.fromTeacherView(homeworkService.create(currentUser.id(), request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> update(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody HomeworkRequest request
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.update(homeworkId, currentUser.id(), request.toCommand())));
    }

    @GetMapping
    public ApiResponse<PageResponse<HomeworkResponse>> list(
            CurrentUser currentUser,
            @RequestParam long courseId,
            @RequestParam(required = false) HomeworkStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<HomeworkResponse> response = mapPage(homeworkService.list(currentUser.id(), courseId, status, keyword, page, size));
        return ApiResponse.ok(response);
    }

    @GetMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> detail(@PathVariable long homeworkId, CurrentUser currentUser) {
        com.onlinejudge.hwk.domain.Homework homework = homeworkService.detail(homeworkId, currentUser.id());
        if (homeworkService.canManageCourse(homework.courseId(), currentUser.id())) {
            return ApiResponse.ok(HomeworkResponse.fromTeacherView(homework));
        }
        return ApiResponse.ok(HomeworkResponse.fromStudentView(homework));
    }

    @PutMapping("/{homeworkId}/questions")
    public ApiResponse<HomeworkResponse> saveQuestions(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody List<HomeworkQuestionPayload> questions
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.saveQuestions(
                homeworkId,
                currentUser.id(),
                questions.stream().map(HomeworkQuestionPayload::toDomain).toList()
        )));
    }

    @PutMapping("/{homeworkId}/test-cases")
    public ApiResponse<HomeworkResponse> saveTestCases(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody List<HomeworkTestCasePayload> testCases
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.saveTestCases(
                homeworkId,
                currentUser.id(),
                testCases.stream().map(HomeworkTestCasePayload::toDomain).toList()
        )));
    }

    @PutMapping("/{homeworkId}/publish")
    public ApiResponse<HomeworkResponse> publish(@PathVariable long homeworkId, CurrentUser currentUser) {
        requireTeacher(currentUser);
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.publish(homeworkId, currentUser.id())));
    }

    @PutMapping("/{homeworkId}/close")
    public ApiResponse<HomeworkResponse> close(@PathVariable long homeworkId, CurrentUser currentUser) {
        requireTeacher(currentUser);
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.close(homeworkId, currentUser.id())));
    }

    @PostMapping("/{homeworkId}/submissions")
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> submit(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @RequestBody HomeworkSubmissionRequest request
    ) {
        requireStudent(currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(HomeworkSubmissionResponse.fromStudentView(
                homeworkSubmissionService.submit(homeworkId, currentUser.id(), request)
        )));
    }

    @GetMapping("/{homeworkId}/my-submissions")
    public ApiResponse<List<HomeworkSubmissionResponse>> mySubmissions(
            @PathVariable long homeworkId,
            CurrentUser currentUser
    ) {
        requireStudent(currentUser);
        return ApiResponse.ok(homeworkSubmissionService.listMine(homeworkId, currentUser.id())
                .stream()
                .map(HomeworkSubmissionResponse::fromStudentView)
                .toList());
    }

    private PageResponse<HomeworkResponse> mapPage(PageResponse<com.onlinejudge.hwk.domain.Homework> page) {
        return new PageResponse<>(page.list().stream().map(HomeworkResponse::summary).toList(), page.total(), page.page(), page.size());
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("teacher role is required");
        }
    }

    private void requireStudent(CurrentUser currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("student role is required");
        }
    }
}
