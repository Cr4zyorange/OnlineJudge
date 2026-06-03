package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkReviewLog(
        long id,
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkReviewOperationType operationType,
        Integer oldScore,
        Integer newScore,
        String comment,
        long operatorId,
        String reason,
        LocalDateTime createdAt
) {
}
