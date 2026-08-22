package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentDownload;
import com.onlinejudge.hwk.service.HomeworkApiException;
import com.onlinejudge.hwk.service.HomeworkAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/homeworks")
public class HomeworkAttachmentController {
    private final HomeworkAttachmentService attachmentService;

    public HomeworkAttachmentController(HomeworkAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(path = "/{homeworkId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HomeworkAttachmentUploadResponse>> upload(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            MultipartHttpServletRequest multipartRequest,
            @RequestParam(value = "file", required = false) List<MultipartFile> files
    ) {
        requireStudent(currentUser);
        long totalFileParts = multipartRequest.getMultiFileMap().values().stream()
                .mapToLong(List::size)
                .sum();
        if (files == null || files.size() != 1 || totalFileParts != 1) {
            throw new HomeworkApiException(
                    "HWK_4005",
                    "exactly one attachment is required",
                    HttpStatus.BAD_REQUEST
            );
        }
        HomeworkAttachmentUploadResponse response = HomeworkAttachmentUploadResponse.from(
                attachmentService.upload(homeworkId, currentUser.id(), files.get(0))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{homeworkId}/attachments/{fileId}")
    public ApiResponse<HomeworkAttachmentUploadResponse> getUnbound(
            @PathVariable long homeworkId,
            @PathVariable String fileId,
            CurrentUser currentUser
    ) {
        requireStudent(currentUser);
        return ApiResponse.ok(HomeworkAttachmentUploadResponse.from(
                attachmentService.getUnbound(homeworkId, fileId, currentUser.id())
        ));
    }

    @DeleteMapping("/{homeworkId}/attachments/{fileId}")
    public ApiResponse<Void> deleteUnbound(
            @PathVariable long homeworkId,
            @PathVariable String fileId,
            CurrentUser currentUser
    ) {
        requireStudent(currentUser);
        attachmentService.deleteUnbound(homeworkId, fileId, currentUser.id());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{homeworkId}/submissions/{submissionId}/attachment/download")
    public ResponseEntity<Resource> download(
            @PathVariable long homeworkId,
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        HomeworkSubmissionAttachmentDownload download = attachmentService.download(
                homeworkId,
                submissionId,
                currentUser.id()
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

    private void requireStudent(CurrentUser currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new HomeworkApiException("HWK_4031", "only students can manage homework uploads", HttpStatus.FORBIDDEN);
        }
    }
}
