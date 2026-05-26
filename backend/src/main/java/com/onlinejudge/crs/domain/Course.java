package com.onlinejudge.crs.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Course(
        Long id,
        String name,
        String description,
        Long teacherId,
        String semester,
        String category,
        String coverUrl,
        EnrollmentMode enrollmentMode,
        String inviteCode,
        Integer maxStudents,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
