package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;

public record CoursePermissionResponse(
        Long courseId,
        Long userId,
        boolean member,
        boolean teacher,
        CourseMemberRole role,
        CourseMemberStatus status
) {
}
