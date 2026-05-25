package com.onlinejudge.integration.course;

@FunctionalInterface
public interface CoursePermissionClient {
    boolean canManageCourseGrade(long courseId, long userId);
}
