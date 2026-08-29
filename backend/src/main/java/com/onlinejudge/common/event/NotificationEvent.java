package com.onlinejudge.common.event;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationEvent(
        String idempotencyKey,
        String type,
        long courseId,
        List<Long> recipientUserIds,
        String title,
        String content,
        String targetType,
        Long targetId,
        String linkUrl,
        LocalDateTime occurredAt
) {
    /**
     * #310 C-05 通知事件契约版本。事件类型、载荷字段或幂等规则变化必须显式升级版本。
     */
    public static final String VERSION = "v1";
}
