package com.onlinejudge.crs.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.crs.domain.dto.AnnouncementPinRequest;
import com.onlinejudge.crs.domain.dto.AnnouncementRequest;
import com.onlinejudge.crs.domain.dto.AnnouncementResponse;
import com.onlinejudge.crs.domain.dto.ChapterCreateRequest;
import com.onlinejudge.crs.domain.dto.ChapterResponse;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CourseJoinRequest;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.dto.CourseMemberResponse;
import com.onlinejudge.crs.domain.dto.CourseMemberUpdateRequest;
import com.onlinejudge.crs.domain.dto.CoursePermissionResponse;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.domain.dto.CourseUpdateRequest;
import com.onlinejudge.crs.domain.dto.CourseHomeSummaryResponse;
import com.onlinejudge.crs.service.AnnouncementService;
import com.onlinejudge.crs.service.ChapterService;
import com.onlinejudge.crs.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService courseService;
    private final ChapterService chapterService;
    private final AnnouncementService announcementService;

    public CourseController(CourseService courseService, ChapterService chapterService, AnnouncementService announcementService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.announcementService = announcementService;
    }

    @PostMapping
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CourseCreateRequest request, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.create(request, currentUser));
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseResponse>> list(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "all") String scope,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          CurrentUser currentUser) {
        return ApiResponse.ok(courseService.list(keyword, scope, page, size, currentUser));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> detail(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.detail(courseId, currentUser));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(@PathVariable Long courseId,
                                              @Valid @RequestBody CourseUpdateRequest request,
                                              CurrentUser currentUser) {
        return ApiResponse.ok(courseService.update(courseId, request, currentUser));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> archive(@PathVariable Long courseId, CurrentUser currentUser) {
        courseService.archive(courseId, currentUser);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{courseId}/join")
    public ApiResponse<CoursePermissionResponse> join(@PathVariable Long courseId,
                                                      @RequestBody(required = false) CourseJoinRequest request,
                                                      CurrentUser currentUser) {
        return ApiResponse.ok(courseService.join(courseId, request, currentUser));
    }

    @GetMapping("/{courseId}/permissions/{userId}")
    public ApiResponse<CoursePermissionResponse> permission(@PathVariable Long courseId, @PathVariable Long userId) {
        return ApiResponse.ok(courseService.permission(courseId, userId));
    }

    @GetMapping("/{courseId}/members")
    public ApiResponse<java.util.List<CourseMemberResponse>> members(@PathVariable Long courseId,
                                                                     @RequestParam(required = false) CourseMemberStatus status,
                                                                     CurrentUser currentUser) {
        return ApiResponse.ok(courseService.members(courseId, status, currentUser));
    }

    @GetMapping("/{courseId}/students")
    public ApiResponse<java.util.List<Long>> students(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.students(courseId, currentUser));
    }

    @PutMapping("/{courseId}/members/{userId}")
    public ApiResponse<CourseMemberResponse> updateMember(@PathVariable Long courseId,
                                                         @PathVariable Long userId,
                                                         @Valid @RequestBody CourseMemberUpdateRequest request,
                                                         CurrentUser currentUser) {
        return ApiResponse.ok(courseService.updateMember(courseId, userId, request, currentUser));
    }

    @DeleteMapping("/{courseId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long courseId,
                                          @PathVariable Long userId,
                                          CurrentUser currentUser) {
        courseService.removeMember(courseId, userId, currentUser);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<ChapterResponse> createChapter(@PathVariable Long courseId,
                                                      @Valid @RequestBody ChapterCreateRequest request,
                                                      CurrentUser currentUser) {
        return ApiResponse.ok(chapterService.create(courseId, request, currentUser));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<java.util.List<ChapterResponse>> chapterTree(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(chapterService.tree(courseId, currentUser));
    }

    @PostMapping("/{courseId}/announcements")
    public ApiResponse<AnnouncementResponse> createAnnouncement(@PathVariable Long courseId,
                                                                @Valid @RequestBody AnnouncementRequest request,
                                                                CurrentUser currentUser) {
        return ApiResponse.ok(announcementService.create(courseId, request, currentUser));
    }

    @GetMapping("/{courseId}/announcements")
    public ApiResponse<java.util.List<AnnouncementResponse>> announcements(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(announcementService.list(courseId, currentUser));
    }

    @PutMapping("/{courseId}/announcements/{announcementId}")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(@PathVariable Long courseId,
                                                                @PathVariable Long announcementId,
                                                                @Valid @RequestBody AnnouncementRequest request,
                                                                CurrentUser currentUser) {
        return ApiResponse.ok(announcementService.update(courseId, announcementId, request, currentUser));
    }

    @PutMapping("/{courseId}/announcements/{announcementId}/top")
    public ApiResponse<AnnouncementResponse> pinAnnouncement(@PathVariable Long courseId,
                                                             @PathVariable Long announcementId,
                                                             @RequestBody(required = false) AnnouncementPinRequest request,
                                                             CurrentUser currentUser) {
        return ApiResponse.ok(announcementService.updateTop(courseId, announcementId, request == null || Boolean.TRUE.equals(request.isTop()), currentUser));
    }

    @DeleteMapping("/{courseId}/announcements/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable Long courseId,
                                                @PathVariable Long announcementId,
                                                CurrentUser currentUser) {
        announcementService.delete(courseId, announcementId, currentUser);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{courseId}/home-summary")
    public ApiResponse<CourseHomeSummaryResponse> homeSummary(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(announcementService.homeSummary(courseId, currentUser));
    }

}
