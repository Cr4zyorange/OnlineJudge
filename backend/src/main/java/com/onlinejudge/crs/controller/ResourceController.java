package com.onlinejudge.crs.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.crs.domain.ResourceType;
import com.onlinejudge.crs.domain.ResourceVisibility;
import com.onlinejudge.crs.domain.dto.ResourceResponse;
import com.onlinejudge.crs.domain.dto.ResourceUpdateRequest;
import com.onlinejudge.crs.service.ResourceDownload;
import com.onlinejudge.crs.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResourceResponse> upload(@PathVariable Long courseId,
                                                @RequestParam MultipartFile file,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) Long chapterId,
                                                @RequestParam(required = false) ResourceType resourceType,
                                                @RequestParam(required = false) ResourceVisibility visibility,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime publishAt,
                                                CurrentUser currentUser) {
        return ApiResponse.ok(resourceService.upload(courseId, file, name, chapterId, resourceType, visibility, publishAt, currentUser));
    }

    @GetMapping
    public ApiResponse<List<ResourceResponse>> list(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(resourceService.list(courseId, currentUser));
    }

    @PutMapping("/{resourceId}")
    public ApiResponse<ResourceResponse> update(@PathVariable Long courseId,
                                                @PathVariable Long resourceId,
                                                @Valid @RequestBody ResourceUpdateRequest request,
                                                CurrentUser currentUser) {
        return ApiResponse.ok(resourceService.update(courseId, resourceId, request, currentUser));
    }

    @DeleteMapping("/{resourceId}")
    public ApiResponse<Void> delete(@PathVariable Long courseId,
                                    @PathVariable Long resourceId,
                                    CurrentUser currentUser) {
        resourceService.delete(courseId, resourceId, currentUser);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{resourceId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long courseId,
                                             @PathVariable Long resourceId,
                                             CurrentUser currentUser) {
        ResourceDownload download = resourceService.download(courseId, resourceId, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.content());
    }
}
