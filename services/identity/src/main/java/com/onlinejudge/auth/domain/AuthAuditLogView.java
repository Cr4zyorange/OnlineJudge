package com.onlinejudge.auth.domain;

import java.time.LocalDateTime;

public record AuthAuditLogView(
        long logId,
        Long operatorId,
        String operationType,
        String targetType,
        String targetId,
        String resultStatus,
        String failureReason,
        String clientIp,
        String userAgent,
        LocalDateTime createdAt
) {
}
