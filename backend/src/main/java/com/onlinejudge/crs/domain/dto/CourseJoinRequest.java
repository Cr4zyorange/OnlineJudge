package com.onlinejudge.crs.domain.dto;

import jakarta.validation.constraints.Size;

public record CourseJoinRequest(
        @Size(max = 64) String inviteCode,
        @Size(max = 500) String applyReason
) {
}
