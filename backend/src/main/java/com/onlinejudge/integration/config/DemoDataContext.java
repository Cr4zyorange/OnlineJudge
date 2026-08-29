package com.onlinejudge.integration.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DemoDataContext(long teacherId, long studentId, LocalDate courseStart, LocalDate courseEnd,
                              LocalDateTime publishedAt, LocalDateTime deadline) {
    public static final long COURSE=9501, CHAPTER=950101, RESOURCE=950102;
    public static final long LAB=950201, LAB_CASE=950202, LAB_SUBMISSION=950203, LAB_EVALUATION=950204,
            LAB_RESULT=950205, LAB_SCORE=950206, OPEN_LAB=950211, OPEN_LAB_CASE=950212;
    public static final long HOMEWORK=950301, HOMEWORK_QUESTION=950302, HOMEWORK_SUBMISSION=950303,
            HOMEWORK_EVALUATION=950304, HOMEWORK_REVIEW=950305, OPEN_HOMEWORK=950311, OPEN_HOMEWORK_QUESTION=950312;
    public static final long GRADE_LAB=950401, GRADE_HOMEWORK=950402, RECORD_LAB=950411, RECORD_HOMEWORK=950412,
            GRADE_SUMMARY=950421, GRADE_PUBLISH=950431, GRADE_BATCH=950441;
    public static final long TASK_LAB=950601, TASK_HOMEWORK=950602, PROGRESS_RESOURCE=950603, RECORD_RESOURCE=950604,
            TASK_OPEN_LAB=950611, TASK_OPEN_HOMEWORK=950612;

    public static DemoDataContext current(long teacherId, long studentId) {
        LocalDate today = LocalDate.now();
        return new DemoDataContext(teacherId, studentId, today.minusDays(7), today.plusDays(90),
                today.minusDays(1).atTime(9, 0), today.plusDays(30).atTime(23, 59, 59));
    }
}
