package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String name,
        String description,
        Long teacherId,
        String teacherName,
        String semester,
        String category,
        String coverUrl,
        EnrollmentMode enrollmentMode,
        String inviteCode,
        Integer maxStudents,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status,
        long memberCount,
        boolean manageable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
