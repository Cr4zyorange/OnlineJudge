package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CourseCreateRequest(
        @NotBlank(message = "课程名称不能为空") @Size(max = 100, message = "课程名称不能超过100个字符") String name,
        @Size(max = 5000, message = "课程简介不能超过5000个字符") String description,
        @Size(max = 64) String semester,
        @Size(max = 64) String category,
        @Size(max = 500) String coverUrl,
        EnrollmentMode enrollmentMode,
        @Size(max = 64) String inviteCode,
        Integer maxStudents,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
}
