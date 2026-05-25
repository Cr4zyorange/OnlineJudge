package com.onlinejudge.crs.domain;

import java.time.LocalDateTime;

public record CourseMember(
        Long id,
        Long courseId,
        Long userId,
        CourseMemberRole role,
        String joinMethod,
        CourseMemberStatus status,
        Long approvedBy,
        LocalDateTime joinedAt
) {
}
