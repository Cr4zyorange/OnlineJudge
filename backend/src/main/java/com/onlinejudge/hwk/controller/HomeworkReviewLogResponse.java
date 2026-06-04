package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkReviewLogResponse(
        long id,
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkReviewOperationType operationType,
        BigDecimal oldScore,
        BigDecimal newScore,
        String comment,
        long operatorId,
        String reason,
        LocalDateTime createdAt
) {
    static HomeworkReviewLogResponse from(HomeworkReviewLog log) {
        return new HomeworkReviewLogResponse(
                log.id(),
                log.submissionId(),
                log.homeworkId(),
                log.studentId(),
                log.operationType(),
                log.oldScore(),
                log.newScore(),
                log.comment(),
                log.operatorId(),
                log.reason(),
                log.createdAt()
        );
    }
}
