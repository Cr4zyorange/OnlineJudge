package com.onlinejudge.grd.domain;

import java.time.LocalDateTime;

public record GradePublishRecord(
        long id,
        long courseId,
        String publishScope,
        int publishedCount,
        long publishedBy,
        LocalDateTime publishedAt,
        String notificationStatus,
        String remark
) {
    public GradePublishRecord withId(long id) {
        return new GradePublishRecord(
                id,
                courseId,
                publishScope,
                publishedCount,
                publishedBy,
                publishedAt,
                notificationStatus,
                remark
        );
    }
}
