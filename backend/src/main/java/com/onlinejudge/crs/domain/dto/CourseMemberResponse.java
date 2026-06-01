package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;

import java.time.LocalDateTime;

public record CourseMemberResponse(
        Long courseId,
        Long userId,
        CourseMemberRole role,
        CourseMemberStatus status,
        String joinMethod,
        Long approvedBy,
        LocalDateTime joinedAt
) {
}
