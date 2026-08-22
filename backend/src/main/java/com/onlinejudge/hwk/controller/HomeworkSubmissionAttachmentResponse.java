package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentView;

public record HomeworkSubmissionAttachmentResponse(
        String originalFilename,
        String contentType,
        long fileSize,
        boolean downloadAvailable
) {
    static HomeworkSubmissionAttachmentResponse from(HomeworkSubmissionAttachmentView view) {
        return view == null ? null : new HomeworkSubmissionAttachmentResponse(
                view.originalFilename(),
                view.contentType(),
                view.fileSize(),
                view.downloadAvailable()
        );
    }
}
