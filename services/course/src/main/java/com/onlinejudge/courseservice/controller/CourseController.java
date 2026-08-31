package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.service.CourseService;
import com.onlinejudge.courseservice.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseService.CourseView> create(@Valid @RequestBody CreateCourseRequest request,
                                                         @RequestAttribute("course.currentUser") CurrentUser user,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.created(service.create(request.name(), request.description(), request.enrollmentMode(), request.inviteCode(), request.maxStudents(), user, requestId(servletRequest)));
    }

    @GetMapping
    public ApiResponse<CourseService.CoursePage> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.list(keyword, page, size, user));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseService.CourseView> detail(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.detail(courseId, user));
    }

    @GetMapping("/{courseId}/home-summary")
    public ApiResponse<CourseService.HomeSummaryView> homeSummary(@PathVariable long courseId,
                                                                  @RequestAttribute("course.currentUser") CurrentUser user,
                                                                  HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.homeSummary(courseId, user, requestId(servletRequest)));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseService.CourseView> update(@PathVariable long courseId, @RequestBody CourseUpdateRequest request,
                                                         @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.update(courseId, request.name(), request.description(), request.enrollmentMode(), request.inviteCode(), request.maxStudents(), request.status(), user));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<CourseService.CourseView> archive(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.archive(courseId, user));
    }

    @PostMapping("/{courseId}/join")
    public ApiResponse<CourseService.MemberView> join(@PathVariable long courseId, @RequestBody(required = false) JoinCourseRequest request,
                                                       @RequestAttribute("course.currentUser") CurrentUser user, HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.join(courseId, request == null ? null : request.inviteCode(), user, requestId(servletRequest)));
    }

    @PostMapping("/{courseId}/leave")
    public ApiResponse<CourseService.MemberView> leave(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user,
                                                        HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.leave(courseId, user, requestId(servletRequest)));
    }

    @GetMapping("/{courseId}/members")
    public ApiResponse<CourseService.MemberPage> members(@PathVariable long courseId, @RequestParam(required = false) String role,
                                                          @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.members(courseId, role, status, page, size, user));
    }

    @GetMapping("/{courseId}/students")
    public ApiResponse<CourseService.MemberPage> students(@PathVariable long courseId, @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.students(courseId, page, size, user));
    }

    @PutMapping("/{courseId}/members/{userId}")
    public ApiResponse<CourseService.MemberView> changeMember(@PathVariable long courseId, @PathVariable long userId,
                                                               @RequestBody MemberChangeRequest request, @RequestAttribute("course.currentUser") CurrentUser user,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.changeMember(courseId, userId, request.role(), request.status(), user, requestId(servletRequest)));
    }

    @DeleteMapping("/{courseId}/members/{userId}")
    public ApiResponse<CourseService.MemberView> removeMember(@PathVariable long courseId, @PathVariable long userId,
                                                               @RequestAttribute("course.currentUser") CurrentUser user, HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.changeMember(courseId, userId, null, "REMOVED", user, requestId(servletRequest)));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<CourseService.ChapterView> chapter(@PathVariable long courseId, @Valid @RequestBody ChapterRequest request,
                                                          @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.createChapter(courseId, request.title(), request.parentId(), request.sortOrder(), request.objective(), request.visible(), request.chapterType(), user));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<CourseService.ChapterView>> chapters(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.chapters(courseId, user));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<CourseService.ChapterView> updateChapter(@PathVariable long courseId, @PathVariable long chapterId,
                                                                 @RequestBody ChapterUpdateRequest request, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.updateChapter(courseId, chapterId, request.title(), request.parentId(), request.sortOrder(), request.objective(), request.visible(), request.chapterType(), user));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable long courseId, @PathVariable long chapterId, @RequestAttribute("course.currentUser") CurrentUser user) {
        service.deleteChapter(courseId, chapterId, user);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{courseId}/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CourseService.ResourceView> resource(@PathVariable long courseId, @Valid @RequestBody ResourceRequest request,
                                                            @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.createResource(courseId, request.title(), request.url(), request.chapterId(), request.resourceType(), request.visibility(), request.publishAt(), user));
    }

    @PostMapping(value = "/{courseId}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CourseService.ResourceView> uploadResource(@PathVariable long courseId, @RequestParam MultipartFile file,
                                                                   @RequestParam(required = false) String name, @RequestParam(required = false) String chapterId,
                                                                   @RequestParam(required = false) String resourceType, @RequestParam(required = false) String visibility,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishAt,
                                                                   @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.uploadResource(courseId, file, name, chapterId, resourceType, visibility, publishAt, user));
    }

    @GetMapping("/{courseId}/resources")
    public ApiResponse<List<CourseService.ResourceView>> resources(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.resources(courseId, user));
    }

    @PutMapping("/{courseId}/resources/{resourceId}")
    public ApiResponse<CourseService.ResourceView> updateResource(@PathVariable long courseId, @PathVariable long resourceId,
                                                                   @RequestBody ResourceUpdateRequest request, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.updateResource(courseId, resourceId, request.title(), request.url(), request.chapterId(), request.resourceType(), request.visibility(), request.publishAt(), user));
    }

    @DeleteMapping("/{courseId}/resources/{resourceId}")
    public ApiResponse<Void> deleteResource(@PathVariable long courseId, @PathVariable long resourceId, @RequestAttribute("course.currentUser") CurrentUser user) {
        service.deleteResource(courseId, resourceId, user);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{courseId}/resources/{resourceId}/download")
    public ResponseEntity<?> downloadResource(@PathVariable long courseId, @PathVariable long resourceId,
                                              @RequestAttribute("course.currentUser") CurrentUser user) {
        CourseService.ResourceDownload download = service.downloadResource(courseId, resourceId, user);
        if (download.redirectUrl() != null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(download.redirectUrl())).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(download.content()));
    }

    @PostMapping("/{courseId}/announcements")
    public ApiResponse<CourseService.AnnouncementView> createAnnouncement(@PathVariable long courseId, @Valid @RequestBody AnnouncementRequest request,
                                                                            @RequestAttribute("course.currentUser") CurrentUser user,
                                                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.createAnnouncement(courseId, request.title(), request.content(), request.top(), user, requestId(servletRequest)));
    }

    @GetMapping("/{courseId}/announcements")
    public ApiResponse<List<CourseService.AnnouncementView>> announcements(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.announcements(courseId, user));
    }

    @PutMapping("/{courseId}/announcements/{announcementId}")
    public ApiResponse<CourseService.AnnouncementView> updateAnnouncement(@PathVariable long courseId, @PathVariable long announcementId,
                                                                            @RequestBody AnnouncementUpdateRequest request, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.updateAnnouncement(courseId, announcementId, request.title(), request.content(), request.top(), user));
    }

    @DeleteMapping("/{courseId}/announcements/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable long courseId, @PathVariable long announcementId,
                                                 @RequestAttribute("course.currentUser") CurrentUser user) {
        service.deleteAnnouncement(courseId, announcementId, user);
        return ApiResponse.ok(null);
    }

    private String requestId(HttpServletRequest request) { return request.getAttribute("course.requestId").toString(); }

    public record CreateCourseRequest(@NotBlank String name, String description, String enrollmentMode, String inviteCode, Integer maxStudents) { }
    public record CourseUpdateRequest(String name, String description, String enrollmentMode, String inviteCode, Integer maxStudents, String status) { }
    public record JoinCourseRequest(String inviteCode) { }
    public record MemberChangeRequest(String role, String status) { }
    public record ChapterRequest(@NotBlank String title, String parentId, Integer sortOrder, String objective, Boolean visible, Integer chapterType) { }
    public record ChapterUpdateRequest(String title, String parentId, Integer sortOrder, String objective, Boolean visible, Integer chapterType) { }
    public record ResourceRequest(@NotBlank String title, @NotBlank String url, String chapterId, String resourceType, String visibility,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishAt) { }
    public record ResourceUpdateRequest(String title, String url, String chapterId, String resourceType, String visibility,
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishAt) { }
    public record AnnouncementRequest(@NotBlank String title, @NotBlank String content, Boolean top) { }
    public record AnnouncementUpdateRequest(String title, String content, Boolean top) { }
}
