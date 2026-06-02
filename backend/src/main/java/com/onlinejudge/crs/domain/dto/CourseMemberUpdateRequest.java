package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import jakarta.validation.constraints.NotNull;

public record CourseMemberUpdateRequest(
        CourseMemberRole role,
        @NotNull CourseMemberStatus status
) {
}
